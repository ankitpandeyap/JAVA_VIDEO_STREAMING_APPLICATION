package com.robspecs.videoprocessor.service;

import com.robspecs.videoprocessor.exception.VideoProcessingException;
import com.robspecs.videoprocessor.dto.VideoMetadata; // Import the new DTO
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// ADD BYTEDECO IMPORTS
//REMOVED JAVE IMPORTS
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil; // For AV_PIX_FMT_YUV420P if needed for pixel format
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber; // Specific exception for grabber
import org.bytedeco.javacv.FrameRecorder; // Specific exception for recorder

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FFmpegService {

	private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

	private final FileStorageService fileStorageService;

	// Bytedeco typically finds FFmpeg binaries automatically via javacv-platform.
	// This property can be used for debugging or overriding if needed,
	// but direct setting might not be required for standard setups.
	@Value("${ffmpeg.executable.path:#{null}}")
	private String ffmpegExecutablePath;

	public FFmpegService(FileStorageService fileStorageService) {
		this.fileStorageService = fileStorageService;
	}

	/**
	 * Retrieves media information (like duration, resolution) of a video file using
	 * Bytedeco.
	 * 
	 * @param videoPath The absolute Path to the video file.
	 * @return VideoMetadata object containing video metadata.
	 * @throws VideoProcessingException if an error occurs during media info
	 *                                  retrieval.
	 */
	public VideoMetadata getMediaInfo(Path videoPath) {
		File videoFile = videoPath.toFile();
		if (!videoFile.exists() || !videoFile.canRead()) {
			throw new VideoProcessingException("Video file not found or not readable: " + videoPath);
		}

		// Set FFmpeg executable path if provided (optional, Bytedeco usually
		// auto-detects)
		if (ffmpegExecutablePath != null && !ffmpegExecutablePath.isEmpty()) {
			logger.debug(
					"ffmpegExecutablePath is set to {}. Bytedeco usually auto-detects, but this can be useful for debugging.",
					ffmpegExecutablePath);
		}

		try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath.toString())) {
			grabber.start();

			VideoMetadata metadata = new VideoMetadata();
			metadata.setDurationMillis(grabber.getLengthInTime() / 1000); // Convert microseconds to milliseconds
			metadata.setWidth(grabber.getImageWidth());
			metadata.setHeight(grabber.getImageHeight());
			metadata.setVideoCodec(grabber.getVideoCodecName());
			metadata.setAudioCodec(grabber.getAudioCodecName());
			long videoBitRate = grabber.getVideoBitrate();
			long audioBitRate = grabber.getAudioBitrate();

			long totalBitRate = 0;
			if (videoBitRate > 0) {
				totalBitRate += videoBitRate;
			}
			if (audioBitRate > 0) {
				totalBitRate += audioBitRate;
			}
			// If both are -1 (meaning no video or audio stream or bitrate not available),
			// totalBitRate remains 0.

			metadata.setBitRate(totalBitRate);

			grabber.stop(); // Stop the grabber to release resources
			return metadata;
		} catch (FrameGrabber.Exception e) { // Catch Bytedeco's specific grabber exception
			logger.error("Error getting media info for {}: {}", videoPath, e.getMessage(), e);
			throw new VideoProcessingException("Failed to get media info for " + videoPath, e);
		} catch (Exception e) { // Catch any other unexpected exceptions
			logger.error("An unexpected error occurred while getting media info for {}: {}", videoPath, e.getMessage(),
					e);
			throw new VideoProcessingException("An unexpected error occurred while getting media info for " + videoPath,
					e);
		}
	}

	/**
	 * Transcodes a video to HLS (HTTP Live Streaming) format using Bytedeco.
	 *
	 * @param originalVideoPath The absolute Path to the original video file.
	 * @param videoId           The ID of the video being processed.
	 * @param username          The username for creating a unique output directory.
	 * @return The relative path to the master HLS playlist (e.g.,
	 *         "users/username/videos/processed/videoId/hls/master.m3u8").
	 * @throws VideoProcessingException if an error occurs during transcoding.
	 */
	public String transcodeToHLS(Path originalVideoPath, Long videoId, Long userId) {
		File source = originalVideoPath.toFile();
     
		if (!source.exists() || !source.canRead()) {
			throw new VideoProcessingException("Source video file not found or not readable: " + originalVideoPath);
		}

		// Use the dedicated method to get the base processed video directory for this user and video
        Path processedUserVideoDir = fileStorageService.getProcessedVideoDirectory(userId, videoId);
        
        // Resolve the HLS subdirectory within that processed video directory
        Path hlsOutputBaseDir = processedUserVideoDir.resolve("hls");

        // Ensure the HLS output directory exists (important!)
        try {
            Files.createDirectories(hlsOutputBaseDir);
        } catch (Exception e) {
            logger.error("Failed to create HLS output directory {}: {}", hlsOutputBaseDir, e.getMessage());
            throw new VideoProcessingException("Failed to create HLS output directory.", e);
        }

        // Define the master playlist file path within the HLS output base directory
        File targetMasterPlaylist = hlsOutputBaseDir.resolve("master.m3u8").toFile();

        // The relative path to return (for database storage)
        String hlsMasterPlaylistRelativePath = fileStorageService.getRelativePath(targetMasterPlaylist.toPath());
		
        // Set FFmpeg executable path if provided (optional, Bytedeco usually
		// auto-detects)
		if (ffmpegExecutablePath != null && !ffmpegExecutablePath.isEmpty()) {
			logger.debug(
					"ffmpegExecutablePath is set to {}. Bytedeco usually auto-detects, but this can be useful for debugging.",
					ffmpegExecutablePath);
		}

		FFmpegFrameGrabber grabber = null;
		FFmpegFrameRecorder recorder = null;

		try {
			grabber = new FFmpegFrameGrabber(source.getAbsolutePath());
			grabber.start();

			// Initialize recorder with output file path and desired resolution
			// The output path for HLS is the master playlist file.
			// Use grabber's width/height if available, otherwise default or use fixed.
			int width = grabber.getImageWidth() > 0 ? grabber.getImageWidth() : 1280;
			int height = grabber.getImageHeight() > 0 ? grabber.getImageHeight() : 720;

			recorder = new FFmpegFrameRecorder(targetMasterPlaylist.getAbsolutePath(), width, height);

			// Configure recorder for HLS output
			recorder.setFormat("hls"); // Set output format to HLS

			// Video attributes (H.264)
			recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
			recorder.setVideoBitrate(2000000); // 2 Mbps
			recorder.setFrameRate(grabber.getFrameRate()); // Match original frame rate
			// CORRECTED: Cast to int for setGopSize
			recorder.setGopSize((int) (grabber.getFrameRate() * 2)); // Keyframe interval (e.g., every 2 seconds)
			recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // Common pixel format for H.264

			// Audio attributes (AAC)
			recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
			recorder.setAudioBitrate(128000); // 128 kbps
			recorder.setSampleRate(grabber.getSampleRate());
			recorder.setAudioChannels(grabber.getAudioChannels());

			// *** HLS specific options via setOption() - This is where you gain granular
			// control ***
			recorder.setOption("hls_time", "10"); // Segment duration in seconds
			recorder.setOption("hls_playlist_type", "vod"); // VOD (Video On Demand)
			// Example: If you need custom segment filenames, you'd use:
			// recorder.setOption("hls_segment_filename",
			// outputDirPath.resolve("segment_%03d.ts").toString());
			// Example: To ensure segments are deleted when the playlist is updated (for
			// live streams)
			// recorder.setOption("hls_flags", "delete_segments");
			// Example: To ensure segments are independent (no inter-segment dependencies)
			// recorder.setOption("hls_flags", "independent_segments");

			recorder.start(); // Start the recorder

			Frame frame;
			long startTime = System.currentTimeMillis();
			long frameCount = 0;

			// Loop through frames from grabber and record them
			while ((frame = grabber.grab()) != null) {
				recorder.record(frame);
				frameCount++;
				// Optional: Add progress logging here if needed
				// if (frameCount % 100 == 0) {
				// logger.debug("Transcoding progress: {} frames processed for video {}",
				// frameCount, videoId);
				// }
			}

			long endTime = System.currentTimeMillis();
			logger.info("Transcoding loop completed for video {}. Total frames: {}. Time taken: {} ms", videoId,
					frameCount, (endTime - startTime));

			logger.info("HLS transcoding completed for video {}. Master playlist: {}", videoId,
					targetMasterPlaylist.getAbsolutePath());
			return relativeOutputDir + "master.m3u8";

		} catch (FrameGrabber.Exception | FrameRecorder.Exception e) { // Catch Bytedeco's specific exceptions
			logger.error("Error during HLS transcoding for video {}: {}", videoId, e.getMessage(), e);
			throw new VideoProcessingException("Failed to transcode video " + videoId + " to HLS.", e);
		} catch (Exception e) { // Catch any other unexpected exceptions
			logger.error("An unexpected error occurred during HLS transcoding for video {}: {}", videoId,
					e.getMessage(), e);
			throw new VideoProcessingException("An unexpected error occurred during HLS transcoding for " + videoId, e);
		} finally {
			// Ensure resources are released
			try {
				if (recorder != null) {
					recorder.stop();
					recorder.release();
				}
			} catch (FrameRecorder.Exception e) {
				logger.error("Error stopping/releasing recorder for video {}: {}", videoId, e.getMessage());
			}
			try {
				if (grabber != null) {
					grabber.stop();
					grabber.release();
				}
			} catch (FrameGrabber.Exception e) {
				logger.error("Error stopping/releasing grabber for video {}: {}", videoId, e.getMessage());
			}
		}
	}
}
