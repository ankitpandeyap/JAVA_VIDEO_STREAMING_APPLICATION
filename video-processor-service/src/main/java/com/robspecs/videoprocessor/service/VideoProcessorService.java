package com.robspecs.videoprocessor.service;

import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.enums.VideoStatus;
import com.robspecs.streaming.repository.VideosRepository;
import com.robspecs.videoprocessor.dto.VideoProcessingRequest;
import com.robspecs.videoprocessor.exception.VideoProcessingException; // Import the custom exception
import com.robspecs.videoprocessor.dto.VideoMetadata; // Import the new VideoMetadata DTO

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// REMOVE JAVE's MediaInfo import
// import ws.schild.jave.info.MediaInfo;

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
    // You can adjust these values based on your needs
    private static final long SMALL_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final long MEDIUM_VIDEO_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

    public VideoProcessorService(VideosRepository videoRepository,
                                 FileStorageService fileStorageService,
                                 FFmpegService ffmpegService,
                                 EmailService emailService) {
        this.videoRepository = videoRepository;
        this.fileStorageService = fileStorageService;
        this.ffmpegService = ffmpegService;
        this.emailService = emailService;
    }

    /**
     * Kafka listener method to consume video processing requests.
     * This method is transactional to ensure DB updates are atomic.
     */
    @KafkaListener(topics = "video-upload-events", groupId = "video-processor-group",
                   containerFactory = "kafkaListenerContainerFactory")
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

        // Immediately update status to PROCESSING in DB
        video.setStatus(VideoStatus.PROCESSING);
        videoRepository.save(video);
        logger.info("Video {} status set to PROCESSING.", request.getVideoId());

        try {
            Path originalVideoAbsolutePath = fileStorageService.resolvePath(request.getOriginalFilePath());

            // 1. Get video metadata (duration) using FFmpegService (now returns VideoMetadata)
            VideoMetadata mediaInfo = (VideoMetadata) ffmpegService.getMediaInfo(originalVideoAbsolutePath); // Cast to VideoMetadata
            long durationMillis = 0;
            if (mediaInfo != null && mediaInfo.getDurationMillis() != null) {
                durationMillis = mediaInfo.getDurationMillis();
            }
            video.setDurationMillis(durationMillis);
            logger.info("Video {} duration set to {} ms.", request.getVideoId(), durationMillis);

            // 2. Conditional Processing Logic
            Map<String, String> resolutionFilePaths = new HashMap<>();

            if (request.getFileSize() <= SMALL_VIDEO_SIZE_BYTES) {
                // Video is small, no transcoding needed. Just use the original file.
                logger.info("Video {} ({}MB) is small, no transcoding. Marking as READY.",
                            video.getVideoId(), request.getFileSize() / (1024.0 * 1024.0));
                resolutionFilePaths.put("original", request.getOriginalFilePath()); // Use "original" key
                video.setStatus(VideoStatus.READY);
            } else if (request.getFileSize() <= MEDIUM_VIDEO_SIZE_BYTES) {
                // Video is medium, no transcoding needed (can be byte-streamed directly).
                logger.info("Video {} ({}MB) is medium, no transcoding. Marking as READY.",
                            video.getVideoId(), request.getFileSize() / (1024.0 * 1024.0));
                resolutionFilePaths.put("original", request.getOriginalFilePath()); // Use "original" key
                video.setStatus(VideoStatus.READY);
            } else {
                // Video is large, perform HLS transcoding
                logger.info("Video {} ({}MB) is large, initiating HLS transcoding.",
                            video.getVideoId(), request.getFileSize() / (1024.0 * 1024.0));

                String hlsMasterPlaylistPath = ffmpegService.transcodeToHLS(
                    originalVideoAbsolutePath,
                    video.getVideoId(),
                    request.getUploadUserEmailOrUsername() // Assuming username is sufficient for folder structure
                );
                resolutionFilePaths.put("hls", hlsMasterPlaylistPath);
                video.setStatus(VideoStatus.READY); // Assume success after HLS
            }

            video.setResolutionFilePaths(resolutionFilePaths); // Update the map of paths
            videoRepository.save(video); // Save final status, duration, and paths

            logger.info("Video {} processed successfully. Status: {}", video.getVideoId(), video.getStatus());
            emailService.sendProcessingSuccessEmail(uploadUserEmailOrUsername, originalVideoName);

        } catch (VideoProcessingException e) {
            logger.error("Video processing failed for {}: {}", request.getVideoId(), e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video); // Save FAILED status
            emailService.sendProcessingFailureEmail(uploadUserEmailOrUsername, originalVideoName, e.getMessage());
        } catch (Exception e) {
            logger.error("An unexpected error occurred during video processing for {}: {}", request.getVideoId(), e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video); // Save FAILED status
            emailService.sendProcessingFailureEmail(uploadUserEmailOrUsername, originalVideoName, "An unexpected error occurred: " + e.getMessage());
        }
    }
}
