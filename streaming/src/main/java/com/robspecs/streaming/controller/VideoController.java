package com.robspecs.streaming.controller;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.dto.VideoUpdateRequest; // <--- NEW IMPORT
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.enums.VideoStatus;
import com.robspecs.streaming.exceptions.FileNotFoundException;
import com.robspecs.streaming.service.FileStorageService;
import com.robspecs.streaming.service.VideoService;
import jakarta.validation.Valid; // <--- NEW IMPORT (if you plan to use @Valid for DTOs)
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

	private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

	private final VideoService videoService;
	private final FileStorageService fileStorageService;

	// Define file size thresholds in bytes for clarity
	private static final long SMALL_VIDEO_THRESHOLD_BYTES = 10 * 1024 * 1024; // 10 MB
	private static final long MEDIUM_VIDEO_THRESHOLD_BYTES = 50 * 1024 * 1024; // 50 MB

	public VideoController(VideoService videoService, FileStorageService fileStorageService) {
		this.videoService = videoService;
		this.fileStorageService = fileStorageService;
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


	@GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> streamVideo(@PathVariable Long videoId,
                                                @RequestParam(required = false) String fileName, // Optional: for specific HLS segments/sub-playlists
                                                @RequestHeader HttpHeaders headers, // Headers might still be sent by clients, but range logic is gone
                                                @AuthenticationPrincipal User currentUser) {

        logger.info("Stream request for videoId: {} with fileName: {} by user: {}", videoId, fileName,
                currentUser.getUsername());

        try {
            Video video = videoService.getActualVideoEntity(videoId, currentUser);

            // Ensure video is in a streamable status (COMPLETED)
            if (video.getStatus() != VideoStatus.READY) { // Only allow streaming if COMPLETED
                logger.warn("Video {} is not ready for streaming. Current status: {}", videoId, video.getStatus());
                return ResponseEntity.status(HttpStatus.LOCKED).body(null); // 423 Locked
            }

            String relativeFilePathToServe;
            MediaType contentType;
            long fileSize; // Size of the specific file being served (master playlist, sub-playlist, or segment)

            // Determine the file to serve based on 'fileName' parameter
            if (fileName != null && !fileName.isEmpty()) {
                // Client is requesting a specific HLS file (e.g., 360p.m3u8, segment001.ts)
                String hlsBasePath = video.getResolutionFilePaths().get("hls_base");
                if (hlsBasePath == null) {
                    logger.error("HLS base path not found for videoId: {}. Cannot serve specific HLS file: {}", videoId,
                            fileName);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                }
                relativeFilePathToServe = hlsBasePath + "/" + fileName;

                if (fileName.endsWith(".m3u8")) {
                    contentType = MediaType.parseMediaType("application/x-mpegURL"); // HLS playlist
                } else if (fileName.endsWith(".ts")) {
                    contentType = MediaType.parseMediaType("video/MP2T"); // HLS segment
                } else {
                    // Fallback for other potential files, though for HLS, these are primary types
                    logger.warn("Unexpected file extension for HLS stream: {}", fileName);
                    contentType = MediaType.APPLICATION_OCTET_STREAM;
                }

                logger.debug("Serving specific HLS file: {} for videoId: {}", relativeFilePathToServe, videoId);

            } else {
                // Client is requesting the primary stream, which should always be the HLS master playlist
                String hlsMasterPlaylistPath = video.getResolutionFilePaths().get("hls_master");
                if (hlsMasterPlaylistPath == null) {
                    logger.error("HLS master playlist path not found for videoId: {}. Video might not be processed for HLS.", videoId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
                }
                relativeFilePathToServe = hlsMasterPlaylistPath;
                contentType = MediaType.parseMediaType("application/x-mpegURL"); // Always application/x-mpegURL for master playlist
                logger.info("Serving HLS master playlist for videoId: {}", videoId);
            }

            // Get the actual file path and its size for the specific file being served
            Path actualPath = fileStorageService.getFilePath(relativeFilePathToServe);
            fileSize = Files.size(actualPath);
            Resource resource = fileStorageService.loadFileAsResource(relativeFilePathToServe);

            // For HLS, we typically don't implement byte range requests at the server level for segments/playlists.
            // The client (HLS.js, native players) handles segment fetching.
            // We just serve the file content with appropriate headers.
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(fileSize) // Content-Length of the current file (playlist or segment)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).noTransform().mustRevalidate()) // Cache HLS content
                    .body(resource);

        } catch (FileNotFoundException e) {
            logger.warn("Stream file not found for videoId {} / fileName {}: {}", videoId, fileName, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            logger.warn("Access denied for streaming videoId {} to user {}: {}", videoId, currentUser.getUsername(),
                    e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException e) {
            logger.error("IO error streaming videoId {} / fileName {}: {}", videoId, fileName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("An unexpected error occurred during streaming videoId {} / fileName {}: {}", videoId,
                    fileName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
	
}