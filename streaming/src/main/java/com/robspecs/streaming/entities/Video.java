package com.robspecs.streaming.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn; // Import JoinColumn
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(indexes = { @Index(name = "title_idx", columnList = "videoName", unique = true) })
public class Video {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long videoId;

	@Column(nullable = false)
	private String videoName;

	@Column(nullable = false, columnDefinition = "TEXT", unique = true)
	private String videoURL;

	@Column(nullable = false)
	// AtomicReference is unusual for an entity field, typically a Long is used and
	// atomicity handled in service layer or with database mechanisms.
	private Long views = 0L;

	@Column(nullable = false)
	private String contentType;

	@Column(columnDefinition = "TEXT")
	private String description;

	// Many-to-One relationship with User
	// @JoinColumn defines the foreign key column in the 'videos' table
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "upload_user_id", nullable = false) // Correct way to define the foreign key and its nullability
	private User uploadUser;

	// --- Getters and Setters ---
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

	public String getVideoURL() {
		return videoURL;
	}

	public void setVideoURL(String videoURL) {
		this.videoURL = videoURL;
	}

	public Long getViews() {
		return views;
	}

	public void setViews(Long views) {
		this.views = views;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public User getUploadUser() {
		return uploadUser;
	}

	public void setUploadUser(User uploadUser) {
		this.uploadUser = uploadUser;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
