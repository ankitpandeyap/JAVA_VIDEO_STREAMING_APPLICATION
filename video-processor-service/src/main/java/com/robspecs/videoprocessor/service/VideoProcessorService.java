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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VideoProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(VideoProcessorService.class);

    private final VideosRepository videoRepository;
    private final FileStorageService fileStorageService;
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

            // Corrected logic:
            if (request.getFileSize() <= SMALL_VIDEO_SIZE_BYTES) {
                // Video is small, no HLS transcoding needed. Copy original to processed.
                logger.info("Video {} ({}MB) is small. Copying to processed folder.", video.getVideoId(),
                        request.getFileSize() / (1024.0 * 1024.0));

                String fileName = originalVideoAbsolutePath.getFileName().toString();
                Path targetProcessedFilePath = processedVideoDirectory.resolve(fileName);

                // Copy the raw file to the processed directory
                fileStorageService.copyFile(originalVideoAbsolutePath, targetProcessedFilePath);
                logger.info("Copied raw file from {} to processed path: {}", originalVideoAbsolutePath, targetProcessedFilePath);

                // Store the relative path of the *copied* file in the database
                resolutionFilePaths.put("original", fileStorageService.getRelativePath(targetProcessedFilePath));
                video.setStatus(VideoStatus.READY);

            } else { // This else block handles MEDIUM and LARGE videos, triggering HLS
                logger.info("Video {} ({}MB) is medium or large, initiating multi-resolution HLS transcoding.", video.getVideoId(),
                        request.getFileSize() / (1024.0 * 1024.0));

                String hlsMasterPlaylistRelativePath = ffmpegService.transcodeToHLS(
                        originalVideoAbsolutePath,
                        video.getVideoId(),
                        request.getUploadUserId()
                );
                
                // Store only the master playlist path. The frontend player will use this single entry point.
                resolutionFilePaths.put("hls_master", hlsMasterPlaylistRelativePath);
                
                // The 'hls_base' entry is no longer needed as the frontend player only needs the master playlist URL.
                // It can resolve the base directory from the master playlist's URL if necessary.

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
                        // This else block is now reached if the file didn't exist when deleteFile was called,
                        // or if deleteFile had to return false for some other non-exception reason (less likely with my prev suggested change).
                        logger.warn("Raw file was not deleted, possibly due to it not existing or being in use before deletion. Path: {}", originalRawFilePath);
                    }
                }
            } catch (IOException e) { // Catch the IOException re-thrown by deleteFile
                logger.error("Failed to delete raw file for videoId {}. It might be in use or permissions are insufficient. Error: {}", video.getVideoId(), e.getMessage());
                // Log the error but do not rethrow, as the video is already processed and ready.
                // The raw file might persist, but the main goal (processed video) is achieved.
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