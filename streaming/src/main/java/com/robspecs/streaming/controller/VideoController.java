package com.robspecs.streaming.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping; // Explicitly import GetMapping

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUpdateRequest;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.enums.VideoStatus;
import com.robspecs.streaming.exceptions.FileNotFoundException;
import com.robspecs.streaming.service.FileStorageService;
import com.robspecs.streaming.service.VideoService;
import com.robspecs.streaming.utils.JWTUtils;

import jakarta.servlet.http.HttpServletRequest; // Import HttpServletRequest

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;
    private final FileStorageService fileStorageService;
    private final JWTUtils jwtUtils;

    // Define file size thresholds in bytes for clarity
    private static final long SMALL_VIDEO_THRESHOLD_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final long MEDIUM_VIDEO_THRESHOLD_BYTES = 50 * 1024 * 1024; // 50 MB

    public VideoController(VideoService videoService, FileStorageService fileStorageService, JWTUtils jwtUtils) {
        this.videoService = videoService;
        this.fileStorageService = fileStorageService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadVideo(@ModelAttribute VideoUploadDTO videoDTO,
            @AuthenticationPrincipal User currentUser) {

        logger.info("Received video upload request for user: {}", currentUser.getUsername());

        if (videoDTO.getFile() == null || videoDTO.getFile().isEmpty()) {
            logger.warn("No file provided in upload request by user: {}", currentUser.getUsername());
            return ResponseEntity.badRequest().body(Map.of("message", "No file provided"));
        }

        try {
            Video uploadedVideo = videoService.uploadVideo(videoDTO, currentUser);
            logger.info("Video upload initiated for videoId: {}", uploadedVideo.getVideoId());
            return ResponseEntity
                    .ok(Map.of("message", "Video upload initiated successfully. Processing will begin shortly.",
                            "videoId", uploadedVideo.getVideoId(), "videoName", uploadedVideo.getVideoName()));
        } catch (IllegalArgumentException e) {
            logger.error("Video upload failed for user {}: {}", currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Video upload failed: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("An unexpected error occurred during video upload for user {}: {}", currentUser.getUsername(),
                    e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred during upload."));
        }
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<VideoDetailsDTO> getVideoDetails(@PathVariable Long videoId,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Fetching video details for videoId: {} by user: {}", videoId, currentUser.getUsername());
        try {
            // This method calls the VideoService.getVideo which returns a DTO
            VideoDetailsDTO videoDetails = videoService.getVideo(videoId, currentUser);
            return ResponseEntity.ok(videoDetails);
        } catch (FileNotFoundException e) {
            logger.warn("Video not found: {}", videoId);
            throw e; // Let global exception handler (if any) catch this and return 404
        } catch (SecurityException e) {
            logger.warn("Access denied for videoId {} to user {}: {}", videoId, currentUser.getUsername(),
                    e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null); // Return empty body for 403
        } catch (Exception e) {
            logger.error("Error fetching video details for videoId {}: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<VideoDetailsDTO> searchVideoByTitle(@RequestParam String title,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Searching for video with title: {} by user: {}", title, currentUser.getUsername());
        try {
            VideoDetailsDTO videoDetails = videoService.searchByTitle(title, currentUser);
            return ResponseEntity.ok(videoDetails);
        } catch (FileNotFoundException e) {
            logger.warn("Video with title '{}' not found for user: {}", title, currentUser.getUsername());
            throw e; // Let global exception handler (if any) catch this and return 404
        } catch (SecurityException e) {
            logger.warn("Access denied for video title '{}' to user {}: {}", title, currentUser.getUsername(),
                    e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (Exception e) {
            logger.error("Error searching video by title '{}' for user {}: {}", title, currentUser.getUsername(),
                    e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PatchMapping("/{videoId}/views")
    public ResponseEntity<Long> incrementVideoViews(@PathVariable Long videoId,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Incrementing views for videoId: {} by user: {}", videoId, currentUser.getUsername());
        try {
            Long updatedViews = videoService.updateViews(videoId, currentUser);
            return ResponseEntity.ok(updatedViews);
        } catch (FileNotFoundException e) {
            logger.warn("Video not found for view increment: {}", videoId);
            throw e; // Let global exception handler (if any) catch this and return 404
        } catch (SecurityException e) {
            logger.warn("Access denied for view increment on videoId {} to user {}: {}", videoId,
                    currentUser.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (Exception e) {
            logger.error("Error incrementing views for videoId {}: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<VideoDetailsDTO>> getAllVideos(@AuthenticationPrincipal User currentUser) {
        logger.info("Fetching all videos (admin view or public listing) for user: {}", currentUser.getUsername());
        try {
            List<VideoDetailsDTO> videos = videoService.getAllVideos();
            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            logger.error("Error fetching all videos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

  

    @GetMapping("/stream/{videoId}/{resolutionOrSegment:.+}") // .+: captures anything including dots and slashes
    public ResponseEntity<?> streamHlsContent(
            @PathVariable Long videoId,
            @PathVariable String resolutionOrSegment,
            @RequestParam(name = "token", required = false) String hlsToken, // HLS Token from query param
            HttpServletRequest request) { // To get the full request URI for logging

        logger.info("Received HLS stream request for videoId: {} with path: {}. HLS Token present: {}",
                videoId, resolutionOrSegment, hlsToken != null);

        try {
            // Retrieve video entity. This method will throw FileNotFoundException if not found.
            // Importantly, the HlsTokenValidationFilter should have already authenticated
            // and authorized this request, so findVideoById just retrieves the entity.
            Video video = videoService.findVideoById(videoId);

            // Ensure video is in a streamable status (READY)
            if (video.getStatus() != VideoStatus.READY) {
                logger.warn("Video {} is not ready for streaming. Current status: {}", videoId, video.getStatus());
                return ResponseEntity.status(HttpStatus.LOCKED).body("Video not ready for streaming."); // 423 Locked
            }

            // Get the user ID associated with the video to construct the file path
            Long userIdForPath = video.getUploadUser().getUserId();

            // Construct the relative file path based on the user ID, video ID, and the requested segment/playlist
            String relativeFilePathToServe = String.format("%d/videos/processed/%d/hls/%s",
                    userIdForPath, videoId, resolutionOrSegment);

            MediaType contentType;
            if (resolutionOrSegment.endsWith(".m3u8")) {
                contentType = MediaType.parseMediaType("application/x-mpegURL"); // HLS playlist
            } else if (resolutionOrSegment.endsWith(".ts")) {
                contentType = MediaType.parseMediaType("video/MP2T"); // HLS segment
            } else {
                logger.warn("Unsupported HLS content type requested for videoId {}: {}", videoId, resolutionOrSegment);
                return ResponseEntity.badRequest().body("Unsupported file type for streaming.");
            }

            // Get the actual Path object for file operations like size
            Path actualPath = fileStorageService.getFilePath(relativeFilePathToServe);
            Resource resource = fileStorageService.loadFileAsResource(relativeFilePathToServe);

            // --- HLS Playlist Rewriting Logic (ONLY for .m3u8 files) ---
            if (contentType.equals(MediaType.parseMediaType("application/x-mpegURL"))) {
                logger.debug("Rewriting HLS playlist for videoId: {}.", videoId);
                String playlistContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                String rewrittenPlaylist = playlistContent.lines()
                        .map(line -> {
                            // Look for lines that are not comments or empty, and don't start with EXT-X-
                            // These are typically relative paths to other .m3u8 files or .ts segments
                            if (!line.startsWith("#") && !line.trim().isEmpty()) {
                                // Append the token query parameter to the URL
                                // Example: 360p.m3u8 -> 360p.m3u8?token={hlsToken}
                                // Example: segment0001.ts -> segment0001.ts?token={hlsToken}
                                // Ensure we don't double-add if it somehow already has a query.
                                // The token should always be present here due to HlsTokenValidationFilter.
                                if (hlsToken != null && !hlsToken.isEmpty()) {
                                    return line + (line.contains("?") ? "&" : "?") + "token=" + hlsToken;
                                }
                            }
                            return line; // Return unchanged for comments, directives, or if no token
                        })
                        .collect(Collectors.joining("\n"));

                // Return the rewritten playlist as a String in the ResponseEntity body
                return ResponseEntity.ok()
                        .contentType(contentType)
                        .contentLength(rewrittenPlaylist.getBytes(StandardCharsets.UTF_8).length) // Update content length
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).noTransform().mustRevalidate()) // Caching for HLS
                        .body(rewrittenPlaylist);

            } else {
                // For .ts files (segments), just serve the resource directly
                logger.debug("Serving HLS segment for videoId: {}.", videoId);
                long fileSize = Files.size(actualPath); // Get file size for .ts segments

                return ResponseEntity.ok()
                        .contentType(contentType)
                        .contentLength(fileSize)
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).noTransform().mustRevalidate()) // Caching for HLS
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes") // Important for video players to seek
                        .body(resource);
            }

        } catch (FileNotFoundException e) {
            logger.warn("Stream file not found for videoId {} / path {}: {}", videoId, resolutionOrSegment, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            logger.error("IO error streaming videoId {} / path {}: {}", videoId, resolutionOrSegment, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading stream file.");
        } catch (Exception e) {
            logger.error("An unexpected error occurred during streaming videoId {} / path {}: {}",
                    videoId, resolutionOrSegment, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during streaming.");
        }
    }


   

    @GetMapping("/my-videos")
    public ResponseEntity<List<VideoDetailsDTO>> getMyVideos(@AuthenticationPrincipal User currentUser) {
        logger.info("Fetching videos for current user: {}", currentUser.getUsername());
        try {
            List<VideoDetailsDTO> userVideos = videoService.getVideosByCurrentUser(currentUser);
            return ResponseEntity.ok(userVideos);
        } catch (Exception e) {
            logger.error("Error fetching videos for user {}: {}", currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @PatchMapping("/{videoId}")
    public ResponseEntity<VideoDetailsDTO> updateVideo(@PathVariable Long videoId,
            @RequestBody VideoUpdateRequest updateRequest,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Received update request for video ID: {} by user: {}", videoId, currentUser.getUsername());
        try {
            VideoDetailsDTO updatedVideo = videoService.updateVideo(videoId, updateRequest, currentUser);
            return ResponseEntity.ok(updatedVideo);
        } catch (FileNotFoundException e) {
            logger.warn("Video not found for update: {}", videoId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (SecurityException e) {
            logger.warn("Access denied for updating videoId {} to user {}: {}", videoId, currentUser.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (Exception e) {
            logger.error("Error updating video ID {}: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @DeleteMapping("/{videoId}")
    public ResponseEntity<Void> deleteVideo(@PathVariable Long videoId,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Received delete request for video ID: {} by user: {}", videoId, currentUser.getUsername());
        try {
            videoService.deleteVideo(videoId, currentUser);
            return ResponseEntity.noContent().build(); // 204 No Content for successful deletion
        } catch (FileNotFoundException e) {
            logger.warn("Video not found for deletion: {}", videoId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (SecurityException e) {
            logger.warn("Access denied for deleting videoId {} to user {}: {}", videoId, currentUser.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            logger.error("An unexpected error occurred during video deletion for videoId {}: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Endpoint to generate a signed URL for HLS video streaming.
     * The generated URL includes a short-lived JWT token as a query parameter.
     * This endpoint requires the requesting user to be authenticated and authorized to access the video.
     *
     * @param videoId The ID of the video for which to generate the stream URL.
     * @param currentUser The authenticated user.
     * @return ResponseEntity containing the signed HLS stream URL.
     */
    @GetMapping("/{videoId}/hls-stream-url")
    public ResponseEntity<String> generateHlsStreamUrl(@PathVariable Long videoId,
            @AuthenticationPrincipal User currentUser) {
        logger.info("Request to generate HLS stream URL for videoId: {} by user: {}", videoId,
                currentUser.getUsername());

        try {
            // 1. Validate user access to the video and its status
            Video video = videoService.findVideoById(videoId);

            // Ensure video is in a streamable status (READY) before generating a token
            if (video.getStatus() != VideoStatus.READY) {
                logger.warn("Video {} is not ready for streaming. Current status: {}", videoId, video.getStatus());
                return ResponseEntity.status(HttpStatus.LOCKED).body("Video not ready for streaming."); // 423 Locked
            }

            // Perform explicit authorization check here since findVideoById just gets the entity.
            // If getActualVideoEntity from VideoService already handles this based on @AuthenticationPrincipal,
            // then this can be simplified. Assuming findVideoById is purely retrieval.
            if (!video.getUploadUser().getUserId().equals(currentUser.getUserId()) &&
                currentUser.getRole() != com.robspecs.streaming.enums.Roles.ADMIN) {
                logger.warn("User {} attempted to generate HLS URL for video {} which they do not own and are not admin.",
                        currentUser.getUsername(), videoId);
                throw new SecurityException("Access denied to video: " + videoId);
            }

            logger.debug("User {} authorized to access video {}.", currentUser.getUsername(), videoId);

            // 2. Generate a short-lived HLS specific JWT token
            long hlsTokenExpiryMinutes = 15; // Set a short expiry for HLS tokens
            String hlsToken = jwtUtils.generateHlsToken(videoId, currentUser.getUserId(), hlsTokenExpiryMinutes);

            if (hlsToken == null) {
                logger.error("Failed to generate HLS token for videoId: {}", videoId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate stream token.");
            }
            logger.debug("HLS token generated for videoId: {}.", videoId);

            // 3. Construct the full signed HLS master playlist URL
            // This URL will be like:
            // /api/videos/stream/{videoId}/master.m3u8?token={hlsToken}
            // Note: The /stream/{videoId} path now handles both master playlist and segments
            // So we directly point to master.m3u8 as the starting point.
            String baseUrl = "/api/videos/stream"; // Base path for streaming
            String signedUrl = String.format("%s/%d/master.m3u8?token=%s", baseUrl, videoId, hlsToken);
            logger.info("Generated signed HLS stream URL for videoId: {}: {}", videoId, signedUrl);

            return ResponseEntity.ok(signedUrl);

        } catch (FileNotFoundException e) {
            logger.warn("Video not found when generating HLS stream URL for videoId: {}. Message: {}", videoId,
                    e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            logger.warn("Access denied for user {} to videoId {} when generating HLS stream URL. Message: {}",
                    currentUser.getUsername(), videoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            logger.error(
                    "An unexpected error occurred during HLS stream URL generation for videoId: {} by user {}: {}",
                    videoId, currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred while generating the stream URL.");
        }
    }
}