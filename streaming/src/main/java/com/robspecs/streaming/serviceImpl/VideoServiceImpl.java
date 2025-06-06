package com.robspecs.streaming.serviceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoProcessingRequest;
import com.robspecs.streaming.dto.VideoUpdateRequest;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.enums.VideoStatus;
import com.robspecs.streaming.exceptions.FileNotFoundException;
import com.robspecs.streaming.exceptions.FileStorageException;
import com.robspecs.streaming.repository.VideosRepository;
import com.robspecs.streaming.service.FileStorageService;
import com.robspecs.streaming.service.VideoService;

import jakarta.transaction.Transactional;

@Service
public class VideoServiceImpl implements VideoService {

	private static final Logger logger = LoggerFactory.getLogger(VideoServiceImpl.class);

	private final VideosRepository videoRepository;
	private final FileStorageService fileStorageService;
	private final KafkaTemplate<String, VideoProcessingRequest> kafkaTemplate;

	private static final String VIDEO_UPLOAD_TOPIC = "video-upload-events";

	public VideoServiceImpl(VideosRepository videoRepository, FileStorageService fileStorageService,
			KafkaTemplate<String, VideoProcessingRequest> kafkaTemplate) {
		this.videoRepository = videoRepository;
		this.fileStorageService = fileStorageService;
		this.kafkaTemplate = kafkaTemplate;
	}

	@Override
	@Transactional
	public Video uploadVideo(VideoUploadDTO v, User user) {
		MultipartFile file = v.getFile();

		// 1. Validate the uploaded file
		if (file == null || file.isEmpty()) {
			logger.warn("Attempt to upload empty or null file by user: {}", user.getUsername());
			throw new IllegalArgumentException("Uploaded file is empty or null.");
		}

		if (!Objects.requireNonNull(file.getContentType()).startsWith("video/")) {
			logger.warn("Attempt to upload non-video file type: {} by user: {}", file.getContentType(),
					user.getUsername());
			throw new IllegalArgumentException("Invalid file type. Only video files are allowed.");
		}

		// Generate a unique filename for the original video
		String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
		String fileExtension = StringUtils.getFilenameExtension(originalFileName);
		String uniqueFileName = UUID.randomUUID().toString() + (fileExtension != null ? "." + fileExtension : "");

		String originalFilePath; // Path where the original file will be stored

		try (InputStream fileStream = file.getInputStream()) {
			// 2. Save the original video file to your configured filesystem
			// Store it in the "raw" subdirectory within the user's video folder
			originalFilePath = fileStorageService.storeFile(fileStream, uniqueFileName, user.getUserId(), "raw");
			logger.info("Original video file stored at: {} for user: {}", originalFilePath, user.getUsername());

		} catch (IOException ex) {
			logger.error("Could not store the original video file {}: {}", originalFileName, ex.getMessage(), ex);
			throw new FileStorageException("Could not store the original video file: " + originalFileName, ex);
		}

		// 3. Create a new Video entity
		Video newVideo = new Video();
		newVideo.setVideoName(v.getTitle()); // User-defined video title
		newVideo.setDescription(v.getDescription());
		newVideo.setUploadUser(user);
		newVideo.setOriginalFilePath(originalFilePath); // Store the relative path returned by FileStorageService
		newVideo.setFileSize(file.getSize()); // Set the original file size
		newVideo.setStatus(VideoStatus.UPLOADED); // Set initial status

		// Save the video entity to the database to get its ID
		newVideo = videoRepository.save(newVideo); // Save to get the videoId
		logger.info("Video entity saved to DB with ID: {}", newVideo.getVideoId());

		// 4. Publish a message to Kafka
		VideoProcessingRequest request = new VideoProcessingRequest(newVideo.getVideoId(),
				newVideo.getOriginalFilePath(), newVideo.getFileSize(), user.getEmail(),user.getUserId());

		// Send message to Kafka. The key can be videoId (as String) for
		// partitioning/ordering
		kafkaTemplate.send(VIDEO_UPLOAD_TOPIC, newVideo.getVideoId().toString(), request);
		logger.info("Kafka message sent for videoId: {}", newVideo.getVideoId());

		return newVideo; // Return the persisted Video entity
	}

	@Override
	@Transactional
	public VideoDetailsDTO getVideo(Long videoId, User user) {
		logger.debug("Fetching video with ID: {} for user: {}", videoId, user.getUsername());
		Video video = videoRepository.findById(videoId).orElseThrow(() -> {
			logger.warn("Video not found with ID: {}", videoId);
			return new FileNotFoundException("Video not found with ID: " + videoId);
		});

		// Basic authorization check: Ensure the user owns the video or is an admin
		// You might want to refine this based on your Roles enum
//		if (!video.getUploadUser().getUserId().equals(user.getUserId())
//				&& user.getRole() != com.robspecs.streaming.enums.Roles.ADMIN) {
//			logger.warn("User {} attempted to access video {} which they do not own and are not admin.",
//					user.getUsername(), videoId);
//			throw new SecurityException("Access denied to video: " + videoId); // Or a custom AccessDeniedException
//		}

		// Convert entity to DTO
		return convertToVideoDetailsDTO(video);
	}

	@Override
	@Transactional
	public VideoDetailsDTO searchByTitle(String videoName, User user) {
		logger.debug("Searching for video with title: '{}' for user: {}", videoName, user.getUsername());
		Video video = videoRepository.findByVideoNameAndUploadUser(videoName, user).orElseThrow(() -> {
			logger.warn("Video with title '{}' not found for user: {}", videoName, user.getUsername());
			return new FileNotFoundException("Video '" + videoName + "' not found for user: " + user.getUsername());
		});

		return convertToVideoDetailsDTO(video);
	}

	@Override
	@Transactional // This method needs a transaction for pessimistic locking
	public Long updateViews(Long videoId, User user) {
		logger.debug("Attempting to update views for video ID: {} by user: {}", videoId, user.getUsername());
		Video video = videoRepository.findById(videoId) // Uses the @Lock(PESSIMISTIC_WRITE) method
				.orElseThrow(() -> {
					logger.warn("Video not found for view increment: {}", videoId);
					return new FileNotFoundException("Video not found with ID: " + videoId);
				});

		video.setViews(video.getViews() + 1);
		videoRepository.save(video);
		logger.info("Views incremented for video ID: {} to {}", videoId, video.getViews());

		return video.getViews();
	}

	@Override
	@Transactional
	public List<VideoDetailsDTO> getAllVideos() {
		logger.debug("Fetching all videos.");
		List<Video> videos = videoRepository.findAll();
		logger.info("Found {} videos in total.", videos.size());
		return videos.stream().map(this::convertToVideoDetailsDTO).collect(Collectors.toList());
	}

	@Override
	public Video getVideoByFilePath(String relativeFilePath) {
		// This method is primarily used internally or for specific streaming scenarios
		// where you know the exact relative path to a video file (e.g., HLS master
		// playlist).
		// You might need to query the database to find the Video entity associated with
		// this path.
		// For example, if 'relativeFilePath' is
		// 'users/username/videos/processed/videoId/hls/master.m3u8',
		// you might extract 'videoId' from it and then fetch the Video.
		// For simplicity, let's assume 'relativeFilePath' directly corresponds to
		// `originalFilePath`
		// or a known HLS path. A more robust solution might involve:
		// 1. Parsing the videoId from the path if your paths are structured that way.
		// 2. Adding a new method to VideoRepository: `findByOriginalFilePath(String
		// path)`
		// 3. Or, if this is for HLS, you'd be looking up the video by its ID, and then
		// finding
		// the HLS master path from its `resolutionFilePaths` map.

		// Placeholder implementation: For now, this will simply throw an exception
		// until you decide how to map a generic relativeFilePath back to a Video
		// entity.
		// In the VideoController's stream method, you'd ideally get the Video entity by
		// ID first,
		// then use `video.getResolutionFilePaths().get("hls_master")` to get the path.

		logger.warn("getVideoByFilePath called with path: {}. This method needs proper implementation.",
				relativeFilePath);
		throw new UnsupportedOperationException(
				"getVideoByFilePath is not fully implemented yet. Please fetch video by ID and resolve paths from entity properties.");
		// Example: If you passed videoId to it, you would do:
		// return videoRepository.findById(videoId).orElseThrow(() -> new
		// FileNotFoundException("Video not found."));
	}

	/**
	 * Helper method to convert a Video entity to a VideoDetailsDTO. This helps
	 * centralize the mapping logic.
	 */
	private VideoDetailsDTO convertToVideoDetailsDTO(Video video) {
		return new VideoDetailsDTO(
				video.getVideoId(), // Assuming getId() or videoId is used consistently
				video.getVideoName(),
				video.getDescription(),
				video.getFileSize(), // Pass fileSize
				video.getStatus().name(), // Convert enum to String
				video.getDurationMillis(),
				video.getViews(),
				video.getUploadUser().getUsername(), // Get username directly
				video.getResolutionFilePaths(),
				video.getThumbnailData() // --- HIGHLIGHT: Pass the thumbnailData ---
		);
	}

	@Override
	@Transactional
	public Video getActualVideoEntity(Long videoId, User user) {
		logger.debug("Fetching actual video entity with ID: {} for user: {}", videoId, user.getUsername());
		Video video = videoRepository.findById(videoId).orElseThrow(() -> {
			logger.warn("Video entity not found with ID: {}", videoId);
			return new FileNotFoundException("Video entity not found with ID: " + videoId);
		});

		// Basic authorization check for fetching the entity
		if (!video.getUploadUser().getUserId().equals(user.getUserId())
				&& user.getRole() != com.robspecs.streaming.enums.Roles.ADMIN) {
			logger.warn("User {} attempted to access video entity {} which they do not own and are not admin.",
					user.getUsername(), videoId);
			throw new SecurityException("Access denied to video entity: " + videoId);
		}
		return video;
	}

	@Override
    @Transactional
    public List<VideoDetailsDTO> getVideosByCurrentUser(User user) {
        logger.debug("Fetching all videos for user: {}", user.getUsername());
        List<Video> videos = videoRepository.findAllByUploadUser(user);
        logger.info("Found {} videos for user: {}", videos.size(), user.getUsername());
        return videos.stream()
                .map(this::convertToVideoDetailsDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VideoDetailsDTO updateVideo(Long videoId, VideoUpdateRequest updateRequest, User currentUser) { // <--- Uses VideoUpdateRequest
        logger.info("Attempting to update video ID: {} by user: {}", videoId, currentUser.getUsername());

        Video video = videoRepository.findByVideoIdAndUploadUser(videoId, currentUser)
                .orElseThrow(() -> {
                    logger.warn("Video with ID: {} not found for user: {} or access denied.", videoId, currentUser.getUsername());
                    return new FileNotFoundException("Video not found or you do not have permission to update.");
                });

        if (updateRequest.getTitle() != null && !updateRequest.getTitle().trim().isEmpty()) {
            video.setVideoName(updateRequest.getTitle());
            logger.debug("Updated videoName to: {}", updateRequest.getTitle());
        }
        if (updateRequest.getDescription() != null) {
            video.setDescription(updateRequest.getDescription());
            logger.debug("Updated description to: {}", updateRequest.getDescription());
        }

        Video updatedVideo = videoRepository.save(video);
        logger.info("Video ID: {} updated successfully by user: {}", videoId, currentUser.getUsername());
        return convertToVideoDetailsDTO(updatedVideo);
    }

    @Override
    @Transactional
    public void deleteVideo(Long videoId, User currentUser) {
        logger.info("Attempting to delete video ID: {} by user: {}", videoId, currentUser.getUsername());

        Video video = videoRepository.findByVideoIdAndUploadUser(videoId, currentUser)
                .orElseThrow(() -> {
                    logger.warn("Video with ID: {} not found for user: {} or access denied for deletion.", videoId, currentUser.getUsername());
                    return new FileNotFoundException("Video not found or you do not have permission to delete.");
                });

        try {
            if (video.getOriginalFilePath() != null && !video.getOriginalFilePath().isEmpty()) {
                fileStorageService.deleteFile(video.getOriginalFilePath());
                logger.info("Deleted original file: {} for video ID: {}", video.getOriginalFilePath(), videoId);
            }
//
//            String processedVideoDirectoryPath = String.format("%s/videos/processed/%d/hls",
//                                                                currentUser.getUserId(),
//                                                                videoId);
//            fileStorageService.deleteDirectory(processedVideoDirectoryPath);
			String videoIdProcessedBaseDirectory = String.format("%s/videos/processed/%d", currentUser.getUserId(),
					videoId);
			fileStorageService.deleteDirectory(videoIdProcessedBaseDirectory);
            logger.info("Deleted processed video directory: {} for video ID: {}", videoIdProcessedBaseDirectory, videoId);

        } catch (IOException e) {
            logger.error("Failed to delete video files from storage for video ID {}: {}", videoId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete video files from storage.", e);
        }

        videoRepository.delete(video);
        logger.info("Video entity with ID: {} deleted successfully from DB.", videoId);
    }
    
    
    @Override
    @Transactional
    public Video findVideoById(Long videoId) throws FileNotFoundException {
        logger.debug("Attempting to find video by ID: {}", videoId);
        return videoRepository.findById(videoId)
                .orElseThrow(() -> {
                    logger.warn("Video not found with ID: {}", videoId);
                    return new FileNotFoundException("Video not found with ID: " + videoId);
                });
    }
}