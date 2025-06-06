// src/main/java/com/robspecs/videoprocessor/service/VideoProcessorService.java (Modified)

package com.robspecs.videoprocessor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor; // Import Executor

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async; // Import @Async

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
    private final Executor videoProcessingExecutor; // Inject the Executor
    
    // private static final long SMALL_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    // private static final long MEDIUM_VIDEO_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB


    public VideoProcessorService(VideosRepository videoRepository, FileStorageService fileStorageService,
                                 FFmpegService ffmpegService, EmailService emailService,
                                 Executor videoProcessingExecutor) { // Add Executor to constructor
        this.videoRepository = videoRepository;
        this.fileStorageService = fileStorageService;
        this.ffmpegService = ffmpegService;
        this.emailService = emailService;
        this.videoProcessingExecutor = videoProcessingExecutor; // Assign it
    }

    /**
     * Kafka listener method to consume video processing requests. This method should
     * quickly submit the task to a thread pool.
     */
    @KafkaListener(topics = "video-upload-events", groupId = "video-processor-group", containerFactory = "kafkaListenerContainerFactory")
    public void receiveVideoProcessingRequest(VideoProcessingRequest request) {
        logger.info("Kafka Listener: Received video processing request for videoId: {}. Submitting to thread pool.", request.getVideoId());
        // Submit the actual processing to the dedicated executor
        videoProcessingExecutor.execute(() -> processVideoAsync(request));
    }

    /**
     * This method contains the actual long-running video processing logic.
     * It runs asynchronously on the 'videoProcessingExecutor' thread pool.
     * Note: @Transactional annotation should ideally be applied here if
     * the entire async method needs to be transactional.
     */
    @Async("videoProcessingExecutor") // Specifies which executor to use
    @Transactional(readOnly = false) // Apply @Transactional here for the entire async operation
    public void processVideoAsync(VideoProcessingRequest request) {
        logger.info("Async Processor: Starting processing for videoId: {}", request.getVideoId());

        Optional<Video> videoOptional = videoRepository.findById(request.getVideoId());
        if (videoOptional.isEmpty()) {
            logger.warn("Async Processor: Video with ID {} not found in database. Skipping processing.", request.getVideoId());
            return;
        }

        Video video = videoOptional.get();
        // --- Idempotency Check (Highly Recommended) ---
        // Check if the video is already READY. If so, skip processing.
        if (VideoStatus.READY.equals(video.getStatus())) {
            logger.info("Async Processor: Video {} is already READY. Skipping duplicate processing.", request.getVideoId());
            // You might want to delete the raw file here if it still exists and was not deleted in the first pass
            try {
                String originalRawFilePath = request.getOriginalFilePath();
                if (originalRawFilePath != null && !originalRawFilePath.isEmpty()) {
                    boolean deleted = fileStorageService.deleteFile(originalRawFilePath);
                    if (deleted) {
                        logger.info("Async Processor: Successfully deleted raw file (duplicate event): {}", originalRawFilePath);
                    } else {
                        logger.warn("Async Processor: Raw file not deleted (duplicate event), possibly not existing or in use. Path: {}", originalRawFilePath);
                    }
                }
            } catch (IOException e) {
                logger.error("Async Processor: Failed to delete raw file for videoId {} during duplicate event handling. Error: {}", video.getVideoId(), e.getMessage());
            }
            return;
        }

        // Set status to PROCESSING (if not already READY)
        // This save might be part of the same transaction as the rest of the processing,
        // so if an exception occurs before commit, it will be rolled back.
        video.setStatus(VideoStatus.PROCESSING);
        videoRepository.save(video);
        logger.info("Async Processor: Video {} status set to PROCESSING.", request.getVideoId());

        try {
            Path originalVideoAbsolutePath = fileStorageService.resolvePath(request.getOriginalFilePath()); // Use request.getOriginalFilePath() directly
            Path processedVideoDirectory = fileStorageService.getProcessedVideoDirectory(
                    request.getUploadUserId(), video.getVideoId());

            Files.createDirectories(processedVideoDirectory);

            // 1. Get video metadata (duration) using FFmpegService
            VideoMetadata mediaInfo = ffmpegService.getMediaInfo(originalVideoAbsolutePath);
            long durationMillis = 0;
            if (mediaInfo != null && mediaInfo.getDurationMillis() != null) {
                durationMillis = mediaInfo.getDurationMillis();
            }
            video.setDurationMillis(durationMillis);
            logger.info("Async Processor: Video {} duration set to {} ms.", request.getVideoId(), durationMillis);

            try {
                long captureTimestampMillis = (durationMillis > 0) ? Math.min(2000, durationMillis / 2) : 0;
                int thumbnailWidth = 640;
                int thumbnailHeight = 360;

                byte[] thumbnailBytes = ffmpegService.generateThumbnail(
                        originalVideoAbsolutePath,
                        captureTimestampMillis,
                        thumbnailWidth,
                        thumbnailHeight
                );
                video.setThumbnailData(thumbnailBytes);
                logger.info("Async Processor: Thumbnail generated and set for videoId: {}", video.getVideoId());
            } catch (VideoProcessingException e) {
                logger.error("Async Processor: Failed to generate thumbnail for video {}: {}", request.getVideoId(), e.getMessage());
                video.setThumbnailData(null);
            } catch (Exception e) {
                logger.error("Async Processor: An unexpected error occurred during thumbnail generation for video {}: {}", request.getVideoId(), e.getMessage(), e);
                video.setThumbnailData(null);
            }

            // 2. Transcode all files to HLS
            Map<String, String> resolutionFilePaths = new HashMap<>();
            logger.info("Async Processor: Initiating multi-resolution HLS transcoding for video {} ({}MB) regardless of size.",
                    video.getVideoId(),
                    request.getFileSize() / (1024.0 * 1024.0));

            String hlsMasterPlaylistRelativePath = ffmpegService.transcodeToHLS(
                    originalVideoAbsolutePath,
                    video.getVideoId(),
                    request.getUploadUserId()
            );

            resolutionFilePaths.put("hls_master", hlsMasterPlaylistRelativePath);

            // Set status to READY and save to DB immediately after successful HLS transcoding
            video.setStatus(VideoStatus.READY);
            video.setResolutionFilePaths(resolutionFilePaths);
            videoRepository.save(video);
            logger.info("Async Processor: Video {} processed successfully. Status: {}", video.getVideoId(), video.getStatus());

            String originalVideoName = video.getVideoName(); // Retrieve the name after successful processing
            String uploadUserEmailOrUsername = request.getUploadUserEmailOrUsername(); // From request

            try {
                emailService.sendProcessingSuccessEmail(uploadUserEmailOrUsername, originalVideoName);
            } catch (Exception emailEx) {
                logger.error("Async Processor: Failed to send success email to {} for video {}: {}",
                        uploadUserEmailOrUsername, originalVideoName, emailEx.getMessage(), emailEx);
            }

            try {
                String originalRawFilePath = request.getOriginalFilePath();
                if (originalRawFilePath != null && !originalRawFilePath.isEmpty()) {
                    boolean deleted = fileStorageService.deleteFile(originalRawFilePath);
                    if (deleted) {
                        logger.info("Async Processor: Successfully deleted raw file: {}", originalRawFilePath);
                    } else {
                        logger.warn("Async Processor: Raw file was not deleted, possibly due to it not existing or being in use before deletion. Path: {}", originalRawFilePath);
                    }
                }
            } catch (IOException e) {
                logger.error("Async Processor: Failed to delete raw file for videoId {}. It might be in use or permissions are insufficient. Error: {}", video.getVideoId(), e.getMessage());
            }

        } catch (VideoProcessingException e) {
            logger.error("Async Processor: Video processing failed for {}: {}", request.getVideoId(), e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video);
            emailService.sendProcessingFailureEmail(request.getUploadUserEmailOrUsername(), video.getVideoName(), e.getMessage()); // Use video.getVideoName() here
        } catch (Exception e) {
            logger.error("Async Processor: An unexpected error occurred during video processing for {}: {}", request.getVideoId(),
                    e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video);
            emailService.sendProcessingFailureEmail(request.getUploadUserEmailOrUsername(), video.getVideoName(),
                    "An unexpected error occurred: " + e.getMessage());
        }
    }
}