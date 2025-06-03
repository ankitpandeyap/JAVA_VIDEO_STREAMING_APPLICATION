package com.robspecs.streaming.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.enums.VideoStatus; // Import VideoStatus
import com.robspecs.streaming.exceptions.FileNotFoundException;
import com.robspecs.streaming.service.FileStorageService;
import com.robspecs.streaming.service.VideoService;

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
			throw e;
		} catch (SecurityException e) {
			logger.warn("Access denied for videoId {} to user {}: {}", videoId, currentUser.getUsername(),
					e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		} catch (Exception e) {
			logger.error("Error fetching video details for videoId {}: {}", videoId, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
			throw e;
		} catch (SecurityException e) {
			logger.warn("Access denied for video title '{}' to user {}: {}", title, currentUser.getUsername(),
					e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		} catch (Exception e) {
			logger.error("Error searching video by title '{}' for user {}: {}", title, currentUser.getUsername(),
					e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
			throw e;
		} catch (SecurityException e) {
			logger.warn("Access denied for view increment on videoId {} to user {}: {}", videoId,
					currentUser.getUsername(), e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		} catch (Exception e) {
			logger.error("Error incrementing views for videoId {}: {}", videoId, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GetMapping
	public ResponseEntity<List<VideoDetailsDTO>> getAllVideos(@AuthenticationPrincipal User currentUser) {
		logger.info("Fetching all videos (admin view or user's videos) for user: {}", currentUser.getUsername());
		try {
			List<VideoDetailsDTO> videos = videoService.getAllVideos();
			return ResponseEntity.ok(videos);
		} catch (Exception e) {
			logger.error("Error fetching all videos: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GetMapping("/stream/{videoId}")
	public ResponseEntity<Resource> streamVideo(@PathVariable Long videoId,
			@RequestParam(required = false) String fileName, // Optional for HLS segments/other resolutions
			@RequestHeader HttpHeaders headers, @AuthenticationPrincipal User currentUser) {

		logger.info("Stream request for videoId: {} with fileName: {} by user: {}", videoId, fileName,
				currentUser.getUsername());

		try {
			// 1. Get the actual Video entity from DB for internal logic (file size, paths)
			Video video = videoService.getActualVideoEntity(videoId, currentUser); // <--- CALLING THE NEW METHOD

			// Ensure video processing is at least 'ENCODING' or 'READY' for streaming
			if (video.getStatus() == VideoStatus.UPLOADED || video.getStatus() == VideoStatus.PROCESSING) {
				logger.warn("Video {} is not ready for streaming. Current status: {}", videoId, video.getStatus());
				return ResponseEntity.status(HttpStatus.LOCKED).body(null);
			}

			String relativeFilePathToServe;
			MediaType contentType;
			long actualFileSize;

			if (fileName != null && !fileName.isEmpty()) {
				// This branch handles requests for specific HLS segments (.ts) or playlists
				// (e.g., 720p/playlist.m3u8)
				String hlsBasePath = video.getResolutionFilePaths().get("hls_base");
				if (hlsBasePath == null) {
					logger.error("HLS base path not found for videoId: {}. Cannot serve specific HLS file: {}", videoId,
							fileName);
					return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
				}
				relativeFilePathToServe = hlsBasePath + "/" + fileName;

				if (fileName.endsWith(".m3u8")) {
					contentType = MediaType.parseMediaType("application/x-mpegURL");
				} else if (fileName.endsWith(".ts")) {
					contentType = MediaType.parseMediaType("video/MP2T");
				} else if (fileName.endsWith(".mp4")) {
					contentType = MediaType.parseMediaType("video/mp4");
				} else {
					contentType = MediaType.APPLICATION_OCTET_STREAM;
				}

				Path actualPath = fileStorageService.getFilePath(relativeFilePathToServe);
				actualFileSize = Files.size(actualPath);
				logger.debug("Serving specific file: {} (size: {}) for videoId: {}", relativeFilePathToServe,
						actualFileSize, videoId);

			} else {
				// This branch handles the initial stream request (no specific fileName)
				actualFileSize = video.getFileSize();
				logger.debug("Original video size: {} for videoId: {}", actualFileSize, videoId);

				if (actualFileSize < SMALL_VIDEO_THRESHOLD_BYTES) {
					logger.info("Serving full video (small, {} bytes) for videoId: {}", actualFileSize, videoId);
					relativeFilePathToServe = video.getOriginalFilePath();
					contentType = MediaType.parseMediaType("video/mp4");
					return ResponseEntity.ok()
							.header(HttpHeaders.CONTENT_DISPOSITION,
									"attachment; filename=\"" + video.getVideoName() + ".mp4\"")
							.contentType(contentType).contentLength(actualFileSize)
							.body(fileStorageService.loadFileAsResource(relativeFilePathToServe));

				} else if (actualFileSize <= MEDIUM_VIDEO_THRESHOLD_BYTES) { // Includes 10MB to 50MB
					logger.info("Serving byte-range video (medium, {} bytes) for videoId: {}", actualFileSize, videoId);
					relativeFilePathToServe = video.getOriginalFilePath();
					contentType = MediaType.parseMediaType("video/mp4");

				} else { // actualFileSize > MEDIUM_VIDEO_THRESHOLD_BYTES (more than 50MB)
					logger.info("Serving HLS stream (large, {} bytes) for videoId: {}", actualFileSize, videoId);
					String hlsMasterPlaylistPath = video.getResolutionFilePaths().get("hls_master");
					if (hlsMasterPlaylistPath == null) {
						logger.error("HLS master playlist path not found for large videoId: {}", videoId);
						// If HLS isn't processed yet or path is missing, fallback to original or error
						// For now, returning NOT_FOUND as per requirements
						return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
					}
					relativeFilePathToServe = hlsMasterPlaylistPath;
					contentType = MediaType.parseMediaType("application/x-mpegURL");
				}
			}

			Resource resource = fileStorageService.loadFileAsResource(relativeFilePathToServe);

			List<HttpRange> ranges = headers.getRange();

			if (ranges.isEmpty()
					|| actualFileSize < MEDIUM_VIDEO_THRESHOLD_BYTES && actualFileSize > SMALL_VIDEO_THRESHOLD_BYTES) {
				// For files < 10MB, we already returned a full download.
				// For files 10-50MB, we always handle range requests.
				// For HLS, the initial request might not have ranges for .m3u8, but segments
				// will.
				return ResponseEntity.ok().contentType(contentType).contentLength(actualFileSize).body(resource);
			}

			HttpRange range = ranges.get(0);
			long start = range.getRangeStart(actualFileSize);
			long end = range.getRangeEnd(actualFileSize);
			long rangeLength = end - start + 1;

			logger.debug("Serving byte range {}-{} (length: {}) for file: {}", start, end, rangeLength,
					relativeFilePathToServe);

			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
					.header(HttpHeaders.CONTENT_TYPE, contentType.toString()).header(HttpHeaders.ACCEPT_RANGES, "bytes")
					.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rangeLength))
					.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + actualFileSize)
					.cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).noTransform().mustRevalidate())
					.body(fileStorageService.loadFileAsResource(relativeFilePathToServe));

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
}