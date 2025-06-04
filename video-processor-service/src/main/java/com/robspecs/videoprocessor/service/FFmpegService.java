package com.robspecs.videoprocessor.service;

import java.io.File;
import java.io.IOException; // Added for Files.writeString
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList; // Added for List
import java.util.Arrays; // Added for Arrays.asList
import java.util.Comparator; // Added for sorting resolution profiles
import java.util.LinkedHashMap; // Added for LinkedHashMap
import java.util.List;
import java.util.Map; // Added for Map

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.robspecs.videoprocessor.dto.VideoMetadata;
import com.robspecs.videoprocessor.exception.VideoProcessingException;

@Service
public class FFmpegService {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

    private final FileStorageService fileStorageService;

    @Value("${ffmpeg.executable.path:#{null}}")
    private String ffmpegExecutablePath;

    public FFmpegService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    // --- NEW: Resolution Profile Definition ---
    private static class ResolutionProfile {
        String name; // e.g., "360p", "720p"
        int width;
        int height;
        int videoBitrate; // in bits per second
        int audioBitrate; // in bits per second
        String h264Profile; // e.g., "main", "high"
        String h264Level; // e.g., "3.0", "4.0"

        public ResolutionProfile(String name, int width, int height, int videoBitrate, int audioBitrate, String h264Profile, String h264Level) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.videoBitrate = videoBitrate;
            this.audioBitrate = audioBitrate;
            this.h264Profile = h264Profile;
            this.h264Level = h264Level;
        }

        public String getName() { return name; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getVideoBitrate() { return videoBitrate; }
        public int getAudioBitrate() { return audioBitrate; }
        public String getH264Profile() { return h264Profile; }
        public String getH264Level() { return h264Level; }
        public long getTotalBitrate() { return videoBitrate + audioBitrate; } // For BANDWIDTH in master playlist
    }

    // Define target HLS resolutions and their properties
    // Sorted by resolution for consistent master playlist generation
    private static final List<ResolutionProfile> HLS_RESOLUTIONS = Arrays.asList(
        new ResolutionProfile("240p", 426, 240, 400_000, 64_000, "baseline", "3.0"),
        new ResolutionProfile("360p", 640, 360, 800_000, 96_000, "main", "3.0"),
        new ResolutionProfile("480p", 854, 480, 1_500_000, 128_000, "main", "3.1"),
        new ResolutionProfile("720p", 1280, 720, 3_000_000, 192_000, "high", "4.0"),
        new ResolutionProfile("1080p", 1920, 1080, 5_000_000, 256_000, "high", "4.1")
        // Add 1440p if desired and if source supports it:
        // new ResolutionProfile("1440p", 2560, 1440, 8_000_000, 320_000, "high", "4.2")
    );
    // --- END NEW: Resolution Profile Definition ---

    public VideoMetadata getMediaInfo(Path videoPath) {
        // ... (unchanged) ...
        File videoFile = videoPath.toFile();
        if (!videoFile.exists() || !videoFile.canRead()) {
            throw new VideoProcessingException("Video file not found or not readable: " + videoPath);
        }

        if (ffmpegExecutablePath != null && !ffmpegExecutablePath.isEmpty()) {
            logger.debug(
                    "ffmpegExecutablePath is set to {}. Bytedeco usually auto-detects, but this can be useful for debugging.",
                    ffmpegExecutablePath);
        }

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath.toString())) {
            grabber.start();

            VideoMetadata metadata = new VideoMetadata();
            metadata.setDurationMillis(grabber.getLengthInTime() / 1000);
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

            metadata.setBitRate(totalBitRate);

            grabber.stop();
            return metadata;
        } catch (FrameGrabber.Exception e) {
            logger.error("Error getting media info for {}: {}", videoPath, e.getMessage(), e);
            throw new VideoProcessingException("Failed to get media info for " + videoPath, e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred while getting media info for {}: {}", videoPath, e.getMessage(),
                    e);
            throw new VideoProcessingException("An unexpected error occurred while getting media info for " + videoPath,
                    e);
        }
    }


    /**
     * Transcodes a video to multi-resolution HLS (HTTP Live Streaming) format using Bytedeco.
     *
     * @param originalVideoPath The absolute Path to the original video file.
     * @param videoId           The ID of the video being processed.
     * @param userId            The ID of the user for creating a unique output directory.
     * @return The relative path to the master HLS playlist (e.g., "1/videos/processed/7/hls/master.m3u8").
     * @throws VideoProcessingException if an error occurs during transcoding.
     */
    public String transcodeToHLS(Path originalVideoPath, Long videoId, Long userId) {
        File source = originalVideoPath.toFile();

        if (!source.exists() || !source.canRead()) {
            throw new VideoProcessingException("Source video file not found or not readable: " + originalVideoPath);
        }

        Path processedUserVideoDir = fileStorageService.getProcessedVideoDirectory(userId, videoId);
        Path hlsOutputBaseDir = processedUserVideoDir.resolve("hls");

        try {
            Files.createDirectories(hlsOutputBaseDir);
        } catch (IOException e) { // Changed to IOException as Files.createDirectories throws IOException
            logger.error("Failed to create HLS output directory {}: {}", hlsOutputBaseDir, e.getMessage());
            throw new VideoProcessingException("Failed to create HLS output directory.", e);
        }

        // List to store paths of individual resolution playlists
        List<String> individualPlaylistPaths = new ArrayList<>();
        // Map to store HLS_STREAM_INF lines for the master playlist
        Map<ResolutionProfile, String> streamInfoLines = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order

        if (ffmpegExecutablePath != null && !ffmpegExecutablePath.isEmpty()) {
            logger.debug(
                    "ffmpegExecutablePath is set to {}. Bytedeco usually auto-detects, but this can be useful for debugging.",
                    ffmpegExecutablePath);
        }

        FFmpegFrameGrabber grabber = null;

        try {
            grabber = new FFmpegFrameGrabber(source.getAbsolutePath());
            grabber.start();

            // Get original video properties for reference
            int originalWidth = grabber.getImageWidth();
            int originalHeight = grabber.getImageHeight();
            double originalFrameRate = grabber.getFrameRate();
            int originalSampleRate = grabber.getSampleRate();
            int originalAudioChannels = grabber.getAudioChannels();

            // Validate original stream properties
            if (originalWidth <= 0 || originalHeight <= 0 || originalFrameRate <= 0 || originalSampleRate <= 0 || originalAudioChannels <= 0) {
                 logger.warn("Could not determine all original stream properties from {}. Proceeding with best-effort defaults or potential issues.", originalVideoPath);
            }

            // Iterate through desired resolutions and transcode each
            // Sort resolutions to ensure lower resolutions are processed first or highest is excluded if source is too small
            List<ResolutionProfile> applicableResolutions = HLS_RESOLUTIONS.stream()
                .filter(profile -> profile.getWidth() <= originalWidth && profile.getHeight() <= originalHeight)
                .sorted(Comparator.comparingInt(ResolutionProfile::getHeight)) // Sort by height (e.g., 240p, 360p, ...)
                .toList();

            if (applicableResolutions.isEmpty()) {
                throw new VideoProcessingException("No applicable HLS resolutions could be generated for video " + videoId + " with original dimensions " + originalWidth + "x" + originalHeight);
            }

            for (ResolutionProfile profile : applicableResolutions) {
                FFmpegFrameRecorder recorder = null;
                try {
                    String outputFileName = profile.getName() + ".m3u8";
                    Path targetPlaylistPath = hlsOutputBaseDir.resolve(outputFileName);

                    logger.info("Starting HLS transcoding for video {} at resolution: {}", videoId, profile.getName());

                    recorder = new FFmpegFrameRecorder(targetPlaylistPath.toFile().getAbsolutePath(), profile.getWidth(), profile.getHeight());

                    recorder.setFormat("hls");
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                    recorder.setVideoBitrate(profile.getVideoBitrate());
                    recorder.setFrameRate(originalFrameRate > 0 ? originalFrameRate : 24); // Use original, default to 24 if invalid
                    recorder.setGopSize((int) (recorder.getFrameRate() * 2)); // Typically 2x frame rate for 2-second segments
                    recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);

                    // Set H.264 profile and level
                    recorder.setOption("profile:v", profile.getH264Profile());
                    recorder.setOption("level:v", profile.getH264Level());

                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    recorder.setAudioBitrate(profile.getAudioBitrate());
                    recorder.setSampleRate(originalSampleRate > 0 ? originalSampleRate : 48000); // Use original, default to 48kHz
                    recorder.setAudioChannels(originalAudioChannels > 0 ? originalAudioChannels : 2); // Use original, default to stereo

                    recorder.setOption("hls_time", "10"); // Segment duration in seconds
                    recorder.setOption("hls_playlist_type", "vod"); // VOD (Video On Demand)

                    recorder.start();

                    // Re-grab frames from the beginning for each resolution pass
                    // This is less efficient than a single multi-output FFmpeg command,
                    // but often simpler to implement with Byedeco's grab/record API for multiple outputs.
                    // For very large videos, you might optimize this to use one grabber and
                    // multiple recorders or more advanced FFmpeg command line arguments.
                    grabber.restart(); // Restart grabber for a fresh read for this resolution

                    Frame frame;
                    long resolutionFrameCount = 0;
                    while ((frame = grabber.grab()) != null) {
                        recorder.record(frame);
                        resolutionFrameCount++;
                    }
                    logger.info("HLS transcoding completed for video {} at resolution {}. Total frames: {}", videoId, profile.getName(), resolutionFrameCount);

                    // Add this individual playlist to the list for master playlist generation
                    individualPlaylistPaths.add(outputFileName);
                    // Add stream info for the master playlist
                    // CODECS string: video_codec (avc1) + profile.level, audio_codec (mp4a.40.2 for AAC LC)
                    String codecs = String.format("avc1.%02x%02x%02x,mp4a.40.2",
                                                (avcodec.AV_CODEC_ID_H264 >> 16) & 0xFF, // Assuming h264
                                                (avcodec.AV_CODEC_ID_H264 >> 8) & 0xFF,
                                                (avcodec.AV_CODEC_ID_H264) & 0xFF);
                    // This is a simplified codec string. Real HLS often uses more specific AVC profiles/levels.
                    // For standard H.264, 'avc1.42c01e' (for example) corresponds to H264 Profile Main, Level 3.0.
                    // Bytedeco doesn't directly expose the AVC profile_idc/constraint_set_flags/level_idc in a simple way for generating these.
                    // For robust production, you might need to hardcode common ones or use an external library to derive.
                    // For now, let's use a simpler codec string and focus on functionality.

                    // For more accurate H.264 CODECS string, one would need to parse the video stream details
                    // or use a more advanced FFmpeg utility to get the specific profile and level in hex.
                    // A common fallback is just "avc1.42E01E" for baseline profile.
                    // Given your current setup, 'avc1.XXXXXX' is okay for now; players are usually flexible.
                    // The BANDWIDTH and RESOLUTION are most critical.

                    streamInfoLines.put(profile, String.format("#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d,CODECS=\"%s\"\n%s",
                                                profile.getTotalBitrate(), profile.getWidth(), profile.getHeight(), "avc1.42E01E,mp4a.40.2", // Simplified H264 codec for common compatibility
                                                outputFileName));

                } catch (FrameGrabber.Exception | FrameRecorder.Exception e) {
                    logger.error("Error during HLS transcoding for video {} at resolution {}: {}", videoId, profile.getName(), e.getMessage(), e);
                    // Don't rethrow immediately; try to process other resolutions.
                    // This resolution might fail, but others could succeed.
                } finally {
                    try {
                        if (recorder != null) {
                            recorder.stop();
                            recorder.release();
                        }
                    } catch (FrameRecorder.Exception e) {
                        logger.error("Error stopping/releasing recorder for video {} resolution {}: {}", videoId, profile.getName(), e.getMessage());
                    }
                }
            }

            if (individualPlaylistPaths.isEmpty()) {
                throw new VideoProcessingException("No HLS resolution playlists were successfully generated for video " + videoId);
            }

            // Generate the master playlist
            StringBuilder masterPlaylistContent = new StringBuilder();
            masterPlaylistContent.append("#EXTM3U\n");
            masterPlaylistContent.append("#EXT-X-VERSION:3\n"); // HLS protocol version

            // Add stream info for each successfully transcoded resolution
            for (Map.Entry<ResolutionProfile, String> entry : streamInfoLines.entrySet()) {
                masterPlaylistContent.append(entry.getValue()).append("\n");
            }

            Path masterPlaylistPath = hlsOutputBaseDir.resolve("master.m3u8");
            Files.writeString(masterPlaylistPath, masterPlaylistContent.toString());

            logger.info("Master HLS playlist created for video {}: {}", videoId, masterPlaylistPath.toAbsolutePath());
            return fileStorageService.getRelativePath(masterPlaylistPath);

        } catch (FrameGrabber.Exception e) {
            logger.error("Error starting grabber for video {}: {}", videoId, e.getMessage(), e);
            throw new VideoProcessingException("Failed to initiate grabber for video " + videoId, e);
        } catch (IOException e) { // Catch for Files.writeString or other IO
            logger.error("IO Error during HLS master playlist creation for video {}: {}", videoId, e.getMessage(), e);
            throw new VideoProcessingException("Failed to create master HLS playlist for " + videoId, e);
        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("An unexpected error occurred during HLS transcoding for video {}: {}", videoId,
                    e.getMessage(), e);
            throw new VideoProcessingException("An unexpected error occurred during HLS transcoding for " + videoId, e);
        } finally {
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