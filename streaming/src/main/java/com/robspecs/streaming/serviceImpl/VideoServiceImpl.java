package com.robspecs.streaming.serviceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoProcessingRequest;
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
			throw new IllegalArgumentException("Uploaded file is empty or null.");
		}

		if (!Objects.requireNonNull(file.getContentType()).startsWith("video/")) {
			throw new IllegalArgumentException("Invalid file type. Only video files are allowed.");
		}

		// Generate a unique filename for the original video
		String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
		String fileExtension = StringUtils.getFilenameExtension(originalFileName);
		String uniqueFileName = UUID.randomUUID().toString() + (fileExtension != null ? "." + fileExtension : "");

		String originalFilePath = null; // Initialize to null for try-catch scope

		try (InputStream fileStream = file.getInputStream()) {
			// 2. Save the original video file to your configured filesystem
			// Store it in the "raw" subdirectory within the user's video folder
			originalFilePath = fileStorageService.storeFile(fileStream, uniqueFileName, user.getUsername(), "raw");

		} catch (IOException ex) {
			// Wrap IOException in FileStorageException as per our custom exception handling
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

		// 4. Publish a message to Kafka
		VideoProcessingRequest request = new VideoProcessingRequest(newVideo.getVideoId(),
				newVideo.getOriginalFilePath(), newVideo.getFileSize(), user.getUsername());

		// Send message to Kafka. The key can be videoId (as String) for
		// partitioning/ordering
		kafkaTemplate.send(VIDEO_UPLOAD_TOPIC, newVideo.getVideoId().toString(), request);

		return newVideo; // Return the persisted Video entity
	}

	@Override
	@Transactional
	public VideoDetailsDTO getVideo(Long videoId, User user) {
		// You might want to ensure the user has access to this video if it's not public
		// For now, we'll just fetch it.
		Video video = videoRepository.findById(videoId)
				.orElseThrow(() -> new FileNotFoundException("Video not found with ID: " + videoId));

		// Convert entity to DTO
		return convertToVideoDetailsDTO(video);
	}

	@Override
	@Transactional
	public VideoDetailsDTO searchByTitle(String videoName, User user) {
		// Find by video name and user (assuming unique per user, or you might return a
		// list)
		// If 'videoName' is not unique per user, you'd need a different repository
		// method
		// For simplicity, let's assume it finds one or throws an exception.
		// Or, you might want to return a List<VideoDetailsDTO> if multiple videos with
		// same name exist.
		// Let's assume for now, it's finding one specific video by name and user for
		// direct playback.
		// You would likely have a custom method like:
		// findByVideoNameAndUploadUser(String videoName, User user)
		// For broader search, you might need:
		// findByVideoNameContainingIgnoreCase(String videoName)
		Video video = videoRepository.findByVideoNameAndUploadUser(videoName, user).orElseThrow(
				() -> new FileNotFoundException("Video '" + videoName + "' not found for user: " + user.getUsername()));

		return convertToVideoDetailsDTO(video);
	}

	@Override
	@Transactional // This method needs a transaction for pessimistic locking
	public Long updateViews(Long videoId, User user) {
		// Fetch the video with a pessimistic write lock.
		// This ensures that no other transaction can modify this video record
		// until the current transaction commits or rolls back, preventing race
		// conditions.
		Video video = videoRepository.findById(videoId) // Uses the @Lock(PESSIMISTIC_WRITE) method
				.orElseThrow(() -> new FileNotFoundException("Video not found with ID: " + videoId));

		// Increment views
		video.setViews(video.getViews() + 1);

		// Save the updated video. Since it was fetched within a transactional,
		// and with a pessimistic lock, this update is safely persisted.
		videoRepository.save(video);

		return video.getViews();
	}

	@Override
	@Transactional
	public List<VideoDetailsDTO> getAllVideos() {
		// For admin purposes, fetch all videos
		List<Video> videos = videoRepository.findAll();

		// Convert list of entities to list of DTOs
		return videos.stream().map(this::convertToVideoDetailsDTO).collect(Collectors.toList());
	}

	/**
	 * Helper method to convert a Video entity to a VideoDetailsDTO. This helps
	 * centralize the mapping logic.
	 */
	 private VideoDetailsDTO convertToVideoDetailsDTO(Video video) {
	        return new VideoDetailsDTO(
	            video.getVideoId(),
	            video.getVideoName(),
	            video.getDescription(),
	            video.getFileSize(),
	            video.getStatus().name(), // Convert enum to String
	            video.getDurationMillis(),
	            video.getViews(),
	            video.getUploadUser().getUsername(), // Get username directly
	            video.getResolutionFilePaths()
	        );
	    }

}
