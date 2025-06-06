package com.robspecs.videoprocessor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.enums.VideoStatus;
import com.robspecs.streaming.repository.VideosRepository;
import com.robspecs.videoprocessor.dto.VideoMetadata;
import com.robspecs.videoprocessor.dto.VideoProcessingRequest;
import com.robspecs.videoprocessor.exception.VideoProcessingException;

@Service
public class VideoProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(VideoProcessorService.class);

    private final VideosRepository videoRepository;
    private final FileStorageService fileStorageService;
    private final FFmpegService ffmpegService;
    private final EmailService emailService;

  
    // private static final long SMALL_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    // private static final long MEDIUM_VIDEO_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB
  
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

            
            try {
                // Determine thumbnail capture timestamp: 2 seconds, or half the video duration if shorter.
                // Ensures a frame is taken from within the video even if it's very short.
                long captureTimestampMillis = (durationMillis > 0) ? Math.min(2000, durationMillis / 2) : 0;

                // Desired thumbnail dimensions (e.g., standard 16:9 aspect ratio)
                int thumbnailWidth = 640;
                int thumbnailHeight = 360; 

                byte[] thumbnailBytes = ffmpegService.generateThumbnail(
                    originalVideoAbsolutePath,
                    captureTimestampMillis, 
                    thumbnailWidth,
                    thumbnailHeight
                );
                video.setThumbnailData(thumbnailBytes);
                logger.info("Thumbnail generated and set for videoId: {}", video.getVideoId());
            } catch (VideoProcessingException e) {
                logger.error("Failed to generate thumbnail for video {}: {}", request.getVideoId(), e.getMessage());
                // Don't fail the entire video processing for a thumbnail error, just log and continue
                video.setThumbnailData(null); // Ensure no corrupted data is saved if error
            } catch (Exception e) { // Catch any other unexpected errors during thumbnail generation
                logger.error("An unexpected error occurred during thumbnail generation for video {}: {}", request.getVideoId(), e.getMessage(), e);
                video.setThumbnailData(null);
            }            
            // 2. Transcode all files to HLS (UNCONDITIONAL)
            Map<String, String> resolutionFilePaths = new HashMap<>();

            // --- HIGHLIGHT START: Removed the 'if-else' block entirely ---
            // Removed: if (request.getFileSize() > MEDIUM_VIDEO_SIZE_BYTES) { ... } else { ... }
            // --- HIGHLIGHT END: Removed the 'if-else' block entirely ---

            // --- HIGHLIGHT START: This block is now unconditional ---
            logger.info("Initiating multi-resolution HLS transcoding for video {} ({}MB) regardless of size.",
                    video.getVideoId(),
                    request.getFileSize() / (1024.0 * 1024.0));

            String hlsMasterPlaylistRelativePath = ffmpegService.transcodeToHLS(
                    originalVideoAbsolutePath,
                    video.getVideoId(),
                    request.getUploadUserId()
            );

            // Store only the master playlist path. The frontend player will use this single entry point.
            resolutionFilePaths.put("hls_master", hlsMasterPlaylistRelativePath);

            video.setStatus(VideoStatus.READY); // Assume success after HLS transcoding
            // --- HIGHLIGHT END: This block is now unconditional ---

            video.setResolutionFilePaths(resolutionFilePaths); // Update the map of paths in the video entity
            videoRepository.save(video); // Save final status, duration, and paths to DB

            logger.info("Video {} processed successfully. Status: {}", video.getVideoId(), video.getStatus());
            emailService.sendProcessingSuccessEmail(uploadUserEmailOrUsername, originalVideoName);

           
            try {
                if (originalRawFilePath != null && !originalRawFilePath.isEmpty()) {
                    boolean deleted = fileStorageService.deleteFile(originalRawFilePath);
                    if (deleted) {
                        logger.info("Successfully deleted raw file: {}", originalRawFilePath);
                    } else {
                        logger.warn("Raw file was not deleted, possibly due to it not existing or being in use before deletion. Path: {}", originalRawFilePath);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to delete raw file for videoId {}. It might be in use or permissions are insufficient. Error: {}", video.getVideoId(), e.getMessage());
            }
          

        } catch (VideoProcessingException e) {
            logger.error("Video processing failed for {}: {}", request.getVideoId(), e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video); // Save FAILED status
            emailService.sendProcessingFailureEmail(uploadUserEmailOrUsername, originalVideoName, e.getMessage());
        } catch (Exception e) {
            logger.error("An unexpected error occurred during video processing for {}: {}", request.getVideoId(),
                    e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video); // Save FAILED status
            emailService.sendProcessingFailureEmail(uploadUserEmailOrUsername, originalVideoName,
                    "An unexpected error occurred: " + e.getMessage());
        }
    }

}