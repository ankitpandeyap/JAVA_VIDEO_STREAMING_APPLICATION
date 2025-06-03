package com.robspecs.videoprocessor.dto;

import java.io.Serializable;

/**
 * DTO to encapsulate video metadata retrieved by FFmpegService. This replaces
 * ws.schild.jave.info.MultimediaInfo.
 */
public class VideoMetadata implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long durationMillis;
	private Integer width;
	private Integer height;
	private String videoCodec;
	private String audioCodec;
	private Long bitRate; // Total bitrate

	// Constructors
	public VideoMetadata() {
	}

	public VideoMetadata(Long durationMillis, Integer width, Integer height, String videoCodec, String audioCodec,
			Long bitRate) {
		this.durationMillis = durationMillis;
		this.width = width;
		this.height = height;
		this.videoCodec = videoCodec;
		this.audioCodec = audioCodec;
		this.bitRate = bitRate;
	}

	// Getters
	public Long getDurationMillis() {
		return durationMillis;
	}

	public Integer getWidth() {
		return width;
	}

	public Integer getHeight() {
		return height;
	}

	public String getVideoCodec() {
		return videoCodec;
	}

	public String getAudioCodec() {
		return audioCodec;
	}

	public Long getBitRate() {
		return bitRate;
	}

	// Setters
	public void setDurationMillis(Long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public void setVideoCodec(String videoCodec) {
		this.videoCodec = videoCodec;
	}

	public void setAudioCodec(String audioCodec) {
		this.audioCodec = audioCodec;
	}

	public void setBitRate(Long bitRate) {
		this.bitRate = bitRate;
	}

	@Override
	public String toString() {
		return "VideoMetadata{" + "durationMillis=" + durationMillis + ", width=" + width + ", height=" + height
				+ ", videoCodec='" + videoCodec + '\'' + ", audioCodec='" + audioCodec + '\'' + ", bitRate=" + bitRate
				+ '}';
	}
}
