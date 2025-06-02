package com.robspecs.streaming.dto;

import java.util.Map;

public class VideoDetailsDTO {
	private Long videoId;
	private String videoName;
	private String description;
	private Long fileSize;
	private String status;
	private Long durationMillis;
	private Long views = 0L;
	private String uploadUsername; // Renamed to clearly indicate it's the username string
	private Map<String, String> resolutionFilePaths;

	public VideoDetailsDTO() {
		// Default constructor
	}

	public VideoDetailsDTO(Long videoId, String videoName, String description, Long fileSize, String status,
			Long durationMillis, Long views, String uploadUsername, Map<String, String> resolutionFilePaths) {
		this.videoId = videoId;
		this.videoName = videoName;
		this.description = description;
		this.fileSize = fileSize;
		this.status = status;
		this.durationMillis = durationMillis;
		this.views = views;
		this.uploadUsername = uploadUsername;
		this.resolutionFilePaths = resolutionFilePaths;
	}

	// --- Getters and Setters (auto-generate or write them out) ---
	public Long getVideoId() {
		return videoId;
	}

	public void setVideoId(Long videoId) {
		this.videoId = videoId;
	}

	public String getVideoName() {
		return videoName;
	}

	public void setVideoName(String videoName) {
		this.videoName = videoName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(Long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public Long getViews() {
		return views;
	}

	public void setViews(Long views) {
		this.views = views;
	}

	public String getUploadUsername() {
		return uploadUsername;
	}

	public void setUploadUsername(String uploadUsername) {
		this.uploadUsername = uploadUsername;
	}

	public Map<String, String> getResolutionFilePaths() {
		return resolutionFilePaths;
	}

	public void setResolutionFilePaths(Map<String, String> resolutionFilePaths) {
		this.resolutionFilePaths = resolutionFilePaths;

	}

}
