package com.robspecs.streaming.entities;

import java.util.HashMap;
import java.util.Map;

import com.robspecs.streaming.enums.VideoStatus;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "videos", uniqueConstraints = {
		@UniqueConstraint(name = "uq_video_name_user", columnNames = { "videoName", "upload_user_id" }) }, indexes = {
				@Index(name = "idx_video_name", columnList = "videoName"),
				@Index(name = "idx_upload_user_id", columnList = "upload_user_id") })
public class Video {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long videoId;

	@Column(nullable = false)
	private String videoName;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String originalFilePath;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	private Long fileSize; // Size in bytes

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VideoStatus status = VideoStatus.UPLOADED;

	private Long durationMillis;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "video_resolutions", joinColumns = @JoinColumn(name = "video_id"))
	@MapKeyColumn(name = "resolution_key")
	@Column(name = "file_path", columnDefinition = "TEXT")
	private Map<String, String> resolutionFilePaths = new HashMap<>();

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "upload_user_id", nullable = false)
	private User uploadUser;

	@Column(nullable = false)
	private Long views = 0L;

	// Constructors
	public Video() {
	}

	// Getters and Setters

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

	public String getOriginalFilePath() {
		return originalFilePath;
	}

	public void setOriginalFilePath(String originalFilePath) {
		this.originalFilePath = originalFilePath;
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

	public VideoStatus getStatus() {
		return status;
	}

	public void setStatus(VideoStatus status) {
		this.status = status;
	}

	public Long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(Long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public Map<String, String> getResolutionFilePaths() {
		return resolutionFilePaths;
	}

	public void setResolutionFilePaths(Map<String, String> resolutionFilePaths) {
		this.resolutionFilePaths = resolutionFilePaths;
	}

	public User getUploadUser() {
		return uploadUser;
	}

	public void setUploadUser(User uploadUser) {
		this.uploadUser = uploadUser;
	}

	public Long getViews() {
		return views;
	}

	public void setViews(Long views) {
		this.views = views;
	}
}