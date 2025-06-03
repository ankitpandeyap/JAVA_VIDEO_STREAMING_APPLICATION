package com.robspecs.streaming.dto;

import org.springframework.web.multipart.MultipartFile;

public class VideoUploadDTO {
	private MultipartFile file;
	private String title; // for videoName
	private String description; // optional

	// Getters & Setters
	public MultipartFile getFile() {
		return file;
	}

	public void setFile(MultipartFile file) {
		this.file = file;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}