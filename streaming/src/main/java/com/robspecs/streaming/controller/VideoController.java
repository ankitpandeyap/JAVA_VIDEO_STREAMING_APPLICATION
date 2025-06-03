package com.robspecs.streaming.controller;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.service.VideoService;
import com.robspecs.streaming.exceptions.FileNotFoundException;
import com.robspecs.streaming.service.FileStorageService; // For streaming directly
import com.robspecs.streaming.entities.Video;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpRange;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
@RequestMapping("/api/videos") // Changed to /api/videos for consistency and common practice
public class VideoController {

    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;
    private final FileStorageService fileStorageService; // Inject FileStorageService for streaming

    public VideoController(VideoService videoService, FileStorageService fileStorageService) {
        this.videoService = videoService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Handles video upload.
     * POST /api/videos/upload
     * Consumes multipart form data (file + title, description).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadVideo(
            @ModelAttribute VideoUploadDTO videoDTO,
            @AuthenticationPrincipal User currentUser) {

        logger.info("Received video upload request for user: {}", currentUser.getUsername());

        if (videoDTO.getFile() == null || videoDTO.getFile().isEmpty()) {
            logger.warn("No file provided in upload request by user: {}", currentUser.getUsername());
            return ResponseEntity.badRequest().body(Map.of("message", "No file provided"));
        }

        try {
            Video uploadedVideo = videoService.uploadVideo(videoDTO, currentUser);
            logger.info("Video upload initiated for videoId: {}", uploadedVideo.getVideoId());
            return ResponseEntity.ok(Map.of(
                    "message", "Video upload initiated successfully. Processing will begin shortly.",
                    "videoId", uploadedVideo.getVideoId(),
                    "videoName", uploadedVideo.getVideoName()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Video upload failed for user {}: {}", currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Video upload failed: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("An unexpected error occurred during video upload for user {}: {}", currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred during upload."));
        }
    }

    /**
     * Retrieves details for a specific video.
     * GET /api/videos/{videoId}
     */
    @GetMapping("/{videoId}")
    public ResponseEntity<VideoDetailsDTO> getVideoDetails(
            @PathVariable Long videoId,
            @AuthenticationPrincipal User currentUser) { // Consider if public access is allowed without auth
        logger.info("Fetching video details for videoId: {} by user: {}", videoId, currentUser.getUsername());
        try {
            VideoDetailsDTO videoDetails = videoService.getVideo(videoId, currentUser);
            // Optionally increment views here or on stream request
            // videoService.updateViews(videoId, currentUser); // If you want views incremented on detail fetch
            return ResponseEntity.ok(videoDetails);
        } catch (FileNotFoundException e) {
            logger.warn("Video not found: {}", videoId);
            throw e; // Let @ResponseStatus handle 404
        } catch (Exception e) {
            logger.error("Error fetching video details for videoId {}: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Searches for a video by title for the current user.
     * GET /api/videos/search?title={videoTitle}
     */
    @GetMapping("/search")
    public ResponseEntity<VideoDetailsDTO> searchVideoByTitle(
            @RequestParam String title,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Searching for video with title: {} by user: {}", title, currentUser.getUsername());
        try {
            VideoDetailsDTO videoDetails = videoService.searchByTitle(title, currentUser);
            return ResponseEntity.ok(videoDetails);
        } catch (FileNotFoundException e) {
            logger.warn("Video with title '{}' not found for user: {}", title, currentUser.getUsername());
            throw e; // Let @ResponseStatus handle 404
        } catch (Exception e) {
            logger.error("Error searching video by title '{}' for user {}: {}", title, currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Increments video views.
     * PATCH /api/videos/{videoId}/views
     */
    @PatchMapping("/{videoId}/views")
    public ResponseEntity<Long> incrementVideoViews(
            @PathVariable Long videoId,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Incrementing views for videoId: {} by user: {}", videoId, currentUser.getUsername());
        try {
            Long updatedViews = videoService.updateViews(videoId, currentUser);
            return ResponseEntity.ok(updatedViews);
        } catch (FileNotFoundException e) {
            logger.warn("Video not found for view increment: {}", videoId);
            throw e; // Let @ResponseStatus handle 404
        } catch (Exception e) {
            logger.error("Error incrementing views for videoId {}: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves a list of all videos (e.g., for an admin dashboard or user's library).
     * GET /api/videos
     */
    @GetMapping
    public ResponseEntity<List<VideoDetailsDTO>> getAllVideos(@AuthenticationPrincipal User currentUser) {
        logger.info("Fetching all videos for user: {}", currentUser.getUsername()); // Or remove currentUser if this is admin-only
        try {
            // Implement logic here if this should be filtered by user, otherwise, get all.
            // For now, assuming it returns all videos the current user can see or that are public.
            // Adjust the service method if needed for user-specific listing.
            List<VideoDetailsDTO> videos = videoService.getAllVideos();
            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            logger.error("Error fetching all videos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Streams video content (supports HTTP range requests for seeking).
     * This endpoint should primarily be used for *processed* (HLS) content.
     * GET /api/videos/stream/{videoId}
     *
     * Note: This is a simplified direct file stream. For HLS, the master playlist
     * will reference segments, and a separate endpoint or clever path resolution
     * will be needed to serve those.
     */
    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> streamVideo(
            @PathVariable Long videoId,
            @RequestHeader HttpHeaders headers,
            @AuthenticationPrincipal User currentUser) { // User authentication for streaming

        logger.info("Stream request for videoId: {} by user: {}", videoId, currentUser.getUsername());
        try {
            // 1. Get Video details from DB to find the HLS master playlist path
            Video video = videoService.getVideoByFilePath(null); // We need a method to get video by ID and then resolve its HLS path

            // For now, let's assume we want to stream the master.m3u8 from HLS processed path.
            // In a real scenario, you'd retrieve the HLS master playlist path from the Video entity.
            // Example: String hlsMasterPlaylistRelativePath = video.getResolutionFilePaths().get("hls_master");
            // For now, let's hardcode a path that assumes it's within the file storage.
            // **IMPORTANT:** You need to update `Video` entity and `VideoProcessingService` to store
            // the HLS master playlist path (e.g., `users/username/videos/processed/videoId/hls/master.m3u8`).
            // For demonstration, let's use a dummy path structure which assumes `videoId` maps to its HLS folder
            // and `master.m3u8` is inside.
            String relativeHlsPath = "users/" + currentUser.getUsername() + "/videos/processed/" + videoId + "/hls/master.m3u8";
            // In a production app, fetch this from `video.getResolutionFilePaths()` map (e.g., `video.getResolutionFilePaths().get("hls_master")`)
            // after the video processing service has updated it.

            // 2. Load the HLS master playlist or a segment as a resource
            Path filePath = fileStorageService.getFilePath(relativeHlsPath);
            Resource resource = fileStorageService.loadFileAsResource(relativeHlsPath); // This will throw FileNotFound if not found

            // 3. Handle Range Requests (important for video seeking)
            long contentLength = resource.contentLength();
            List<HttpRange> ranges = headers.getRange();

            if (ranges.isEmpty() || !resource.isReadable()) {
                 return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/x-mpegURL")) // For .m3u8
                        .contentLength(contentLength)
                        .body(resource);
            }

            // For range requests, return a 206 Partial Content
            HttpRange range = ranges.get(0); // Take the first range, as most players request one
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = end - start + 1;

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-mpegURL") // For .m3u8
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rangeLength))
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength)
                    .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).noTransform().mustRevalidate())
                    .body(fileStorageService.loadFileAsResource(relativeHlsPath)); // Load the resource again for the specified range
                                                                                   // (UrlResource can handle byte ranges automatically)

        } catch (FileNotFoundException e) {
            logger.warn("Stream file not found for videoId {}: {}", videoId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error streaming videoId {}: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}