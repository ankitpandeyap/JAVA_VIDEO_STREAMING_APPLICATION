package com.robspecs.streaming.enums;

public enum VideoStatus {
	UPLOADED, // The video file has been uploaded to temporary storage
	PROCESSING, // The video processing service has picked up the video and is working on it
	ENCODING, // Specifically, the video is being transcoded to different resolutions
	READY, // All required resolutions are processed and available for streaming
	FAILED // Video processing failed
}
