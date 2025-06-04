package com.robspecs.streaming.dto;

import java.io.Serializable;

public class VideoProcessingRequest implements Serializable {

	private static final long serialVersionUID = 1L; // Recommended for Serializable classes

	private Long videoId;
	private String originalFilePath; // Path/URL to the raw video file in cloud storage
	private Long fileSize; // Size of the original video file
	private String uploadUserEmailorUsername; // Email of the user who uploaded the video
	private Long uploadUserId;

	// Default constructor for deserialization
	public VideoProcessingRequest() {
	}

	public VideoProcessingRequest(Long videoId, String originalFilePath, Long fileSize,
			String uploadUserEmailorUsername,Long uploadUserId) {
		this.videoId = videoId;
		this.originalFilePath = originalFilePath;
		this.fileSize = fileSize;
		this.uploadUserEmailorUsername = uploadUserEmailorUsername;
		this.uploadUserId=uploadUserId;
	}

	// Getters
	public Long getVideoId() {
		return videoId;
	}

	public String getOriginalFilePath() {
		return originalFilePath;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public String getUploadUserEmailOrUsername() {
		return uploadUserEmailorUsername;
	}

	// Setters
	public void setVideoId(Long videoId) {
		this.videoId = videoId;
	}

	public void setOriginalFilePath(String originalFilePath) {
		this.originalFilePath = originalFilePath;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public void setUploadUserEmailOrUsername(String uploadUserEmailorUsername) {
		this.uploadUserEmailorUsername = uploadUserEmailorUsername;
	}
	
	

	public Long getUploadUserId() {
		return uploadUserId;
	}

	public void setUploadUserId(Long uploadUserId) {
		this.uploadUserId = uploadUserId;
	}

	@Override
	public String toString() {
		return "VideoProcessingRequest{" + "videoId=" + videoId + ", originalFilePath='" + originalFilePath + '\''
				+ ", fileSize=" + fileSize + ", uploadUserEmail='" + uploadUserEmailorUsername + '\'' + '}';
	}
}
