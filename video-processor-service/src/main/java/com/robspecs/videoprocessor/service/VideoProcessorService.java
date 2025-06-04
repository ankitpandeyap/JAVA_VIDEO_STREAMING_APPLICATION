package com.robspecs.videoprocessor.service;

import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.enums.VideoStatus;
import com.robspecs.streaming.repository.VideosRepository;
import com.robspecs.videoprocessor.dto.VideoProcessingRequest;
import com.robspecs.videoprocessor.exception.VideoProcessingException;
import com.robspecs.videoprocessor.dto.VideoMetadata;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException; // Needed for Files.copy
import java.nio.file.Files; // Needed for Files.createDirectories, Files.copy
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VideoProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(VideoProcessorService.class);

    private final VideosRepository videoRepository;
    private final FileStorageService fileStorageService; // This is the video-processor-service's FileStorageService
    private final FFmpegService ffmpegService;
    private final EmailService emailService;

    // Define video size thresholds (in bytes)
    private static final long SMALL_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final long MEDIUM_VIDEO_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

    public VideoProcessorService(VideosRepository videoRepository, FileStorageService fileStorageService,
            FFmpegService ffmpegService, EmailService emailService) {
        this.videoRepository = videoRepository;
        this.fileStorageService = fileStorageService;
        this.ffmpegService = ffmpegService;
        this.emailService = emailService;
    }

    /**
     * Kafka listener method to consume video processing requests. This method is
     * transactional to ensure DB updates are atomic.
     */
    @KafkaListener(topics = "video-upload-events", groupId = "video-processor-group", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void processVideoRequest(VideoProcessingRequest request) {
        logger.info("Received video processing request for videoId: {}", request.getVideoId());

        Optional<Video> videoOptional = videoRepository.findById(request.getVideoId());
        if (videoOptional.isEmpty()) {
            logger.warn("Video with ID {} not found in database. Skipping processing.", request.getVideoId());
            return;
        }

        Video video = videoOptional.get();
        String originalVideoName = video.getVideoName(); // Store original name for email
        String uploadUserEmailOrUsername = request.getUploadUserEmailOrUsername();
        String originalRawFilePath = request.getOriginalFilePath(); // Path to the raw file

        // Immediately update status to PROCESSING in DB
        video.setStatus(VideoStatus.PROCESSING);
        videoRepository.save(video);
        logger.info("Video {} status set to PROCESSING.", request.getVideoId());

        try {
            Path originalVideoAbsolutePath = fileStorageService.resolvePath(originalRawFilePath);
            Path processedVideoDirectory = fileStorageService.getProcessedVideoDirectory(
                    request.getUploadUserId(), video.getVideoId());

            // Ensure the user-specific and video-specific processed directory exists
            // This is where processed files (copied original or HLS output) will reside
            Files.createDirectories(processedVideoDirectory);

            // 1. Get video metadata (duration) using FFmpegService
            VideoMetadata mediaInfo = ffmpegService.getMediaInfo(originalVideoAbsolutePath);
            long durationMillis = 0;
            if (mediaInfo != null && mediaInfo.getDurationMillis() != null) {
                durationMillis = mediaInfo.getDurationMillis();
            }
            video.setDurationMillis(durationMillis);
            logger.info("Video {} duration set to {} ms.", request.getVideoId(), durationMillis);

            // 2. Conditional Processing Logic based on file size
            Map<String, String> resolutionFilePaths = new HashMap<>();

            if (request.getFileSize() <= SMALL_VIDEO_SIZE_BYTES || request.getFileSize() <= MEDIUM_VIDEO_SIZE_BYTES) {
                // Video is small/medium, no HLS transcoding needed. Copy original to processed.
                logger.info("Video {} ({}MB) is small/medium. Copying to processed folder.", video.getVideoId(),
                        request.getFileSize() / (1024.0 * 1024.0));

                String fileName = originalVideoAbsolutePath.getFileName().toString();
                Path targetProcessedFilePath = processedVideoDirectory.resolve(fileName);

                // Copy the raw file to the processed directory
                fileStorageService.copyFile(originalVideoAbsolutePath, targetProcessedFilePath);
                logger.info("Copied raw file from {} to processed path: {}", originalVideoAbsolutePath, targetProcessedFilePath);

                // Store the relative path of the *copied* file in the database
                resolutionFilePaths.put("original", fileStorageService.getRelativePath(targetProcessedFilePath));
                video.setStatus(VideoStatus.READY);

            } else {
                // Video is large, perform HLS transcoding
                logger.info("Video {} ({}MB) is large, initiating HLS transcoding.", video.getVideoId(),
                        request.getFileSize() / (1024.0 * 1024.0));

                // ffmpegService.transcodeToHLS should take care of placing files in the correct processed sub-directory
                String hlsMasterPlaylistRelativePath = ffmpegService.transcodeToHLS(
                        originalVideoAbsolutePath,
                        video.getVideoId(),
                        request.getUploadUserId()
                );
                // Store the master playlist path and derive the base HLS directory path
                resolutionFilePaths.put("hls_master", hlsMasterPlaylistRelativePath);
                Path hlsMasterAbsolutePath = fileStorageService.resolvePath(hlsMasterPlaylistRelativePath);
                String hlsBasePath = fileStorageService.getRelativePath(hlsMasterAbsolutePath.getParent());
                resolutionFilePaths.put("hls_base", hlsBasePath);

                video.setStatus(VideoStatus.READY); // Assume success after HLS transcoding
            }

            video.setResolutionFilePaths(resolutionFilePaths); // Update the map of paths in the video entity
            videoRepository.save(video); // Save final status, duration, and paths to DB

            logger.info("Video {} processed successfully. Status: {}", video.getVideoId(), video.getStatus());
            emailService.sendProcessingSuccessEmail(uploadUserEmailOrUsername, originalVideoName);

            // --- DELETE THE RAW FILE HERE AFTER ALL PROCESSING (COPY/TRANSCODE) IS DONE AND DB IS UPDATED ---
            try {
                if (originalRawFilePath != null && !originalRawFilePath.isEmpty()) {
                    boolean deleted = fileStorageService.deleteFile(originalRawFilePath);
                    if (deleted) {
                        logger.info("Successfully deleted raw file: {}", originalRawFilePath);
                    } else {
                        logger.warn("Raw file was not found or could not be deleted: {}. This might be normal if it was already moved/deleted.", originalRawFilePath);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to delete raw file for videoId {}: {}. Error: {}", video.getVideoId(), e.getMessage(), e);
                // Log the error but don't rethrow, as the video is already processed and ready.
            }
            // --- END OF DELETION BLOCK ---

        } catch (VideoProcessingException e) {
            logger.error("Video processing failed for {}: {}", request.getVideoId(), e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video); // Save FAILED status
            emailService.sendProcessingFailureEmail(uploadUserEmailOrUsername, originalVideoName, e.getMessage());
            // Raw file is NOT deleted on failure
        } catch (Exception e) {
            logger.error("An unexpected error occurred during video processing for {}: {}", request.getVideoId(),
                    e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video); // Save FAILED status
            emailService.sendProcessingFailureEmail(uploadUserEmailOrUsername, originalVideoName,
                    "An unexpected error occurred: " + e.getMessage());
            // Raw file is NOT deleted on failure
        }
    }
}