package com.robspecs.streaming.dto;

public class VideoDetailsDTO {
	private String videoName;
	private Long views = 0L;
	private String ContentType;
	private String uploadUser;

	public String getVideoName() {
		return videoName;
	}

	public void setVideoName(String videoName) {
		this.videoName = videoName;
	}

	public Long getViews() {
		return views;
	}

	public void setViews(Long views) {
		this.views = views;
	}

	public String getContentType() {
		return ContentType;
	}

	public void setContentType(String contentType) {
		ContentType = contentType;
	}

	public String getUploadUser() {
		return uploadUser;
	}

	public void setUploadUser(String uploadUser) {
		this.uploadUser = uploadUser;
	}

	public VideoDetailsDTO(String videoName, Long views, String contentType, String uploadUser) {
		super();
		this.videoName = videoName;
		this.views = views;
		ContentType = contentType;
		this.uploadUser = uploadUser;
	}

	public VideoDetailsDTO() {
		super();
		// TODO Auto-generated constructor stub
	}

}
