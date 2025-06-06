package com.robspecs.streaming.dto;

import java.util.Map;

import com.robspecs.streaming.enums.VideoStatus;

public class VideoDetailsDTO {
	private Long videoId;
	 private String videoName;
	    private String description;
	    private Long uploadUserId;
	    private String uploadUserName; // Or email, depending on what you expose
	    private VideoStatus status;
	    private Long durationMillis;
	    private Long fileSize; // Added based on your VideoServiceImpl constructor
	    private Long views;    // Added based on your VideoServiceImpl constructor
	    private Map<String, String> resolutionFilePaths;
	    private byte[] thumbnailData;

	public VideoDetailsDTO() {
		// Default constructor
	}

	public VideoDetailsDTO(Long videoId,  String videoName, String description, Long fileSize, String status,
            Long durationMillis, Long views, String uploadUserName, Map<String, String> resolutionFilePaths,
            byte[] thumbnailData) {
		this.videoId = videoId;
		this.videoName = videoName;
        this.description = description;
        this.fileSize = fileSize;
        // This 'status' parameter is a String, but your DTO has VideoStatus.
        // It's better to pass VideoStatus enum directly or handle conversion here.
        // For now, let's assume 'status' is String for this constructor.
        this.status = VideoStatus.valueOf(status); // Convert String back to Enum
        this.durationMillis = durationMillis;
        this.views = views;
        this.uploadUserName = uploadUserName;
        this.resolutionFilePaths = resolutionFilePaths;
        this.thumbnailData = thumbnailData; // Initialize thumbnailData
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

	

	public Map<String, String> getResolutionFilePaths() {
		return resolutionFilePaths;
	}

	public void setResolutionFilePaths(Map<String, String> resolutionFilePaths) {
		this.resolutionFilePaths = resolutionFilePaths;

	}
    
	  public byte[] getThumbnailData() {
	        return thumbnailData;
	    }

	    public void setThumbnailData(byte[] thumbnailData) {
	        this.thumbnailData = thumbnailData;
	    }

		public Long getUploadUserId() {
			return uploadUserId;
		}

		public void setUploadUserId(Long uploadUserId) {
			this.uploadUserId = uploadUserId;
		}

		public String getUploadUserName() {
			return uploadUserName;
		}

		public void setUploadUserName(String uploadUserName) {
			this.uploadUserName = uploadUserName;
		}

		public VideoStatus getStatus() {
			return status;
		}

		public void setStatus(VideoStatus status) {
			this.status = status;
		}
	    
	    
	    
}
