// src/pages/VideoPlayerPage.jsx
import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import videojs from "video.js";
import "@videojs/http-streaming"; // Important for HLS playback
import "video.js/dist/video-js.css"; // Default Video.js styles
// Removed: Silvermine quality selector plugin and its CSS imports

import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import LoadingSpinner from "../components/LoadingSpinner";
import axiosInstance from "../api/axiosInstance";
import { toast } from "react-toastify";
import "../css/VideoPlayerPage.css";

const VideoPlayerPage = () => {
  const { videoId } = useParams();
  const navigate = useNavigate();
  const [video, setVideo] = useState(null);
  const [hlsPlaybackUrl, setHlsPlaybackUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const hasIncrementedViewForVideoId = useRef({});

  // Video.js specific refs
  const videoRef = useRef(null); // Ref to the <video> element
  const playerRef = useRef(null); // Ref to the Video.js player instance

  const fetchVideoData = useCallback(async () => {
    setLoading(true);
    setError("");
    if (!videoId) {
      setError("Video ID is missing from the URL. Cannot load video.");
      toast.error("Video ID is missing. Redirecting...");
      setTimeout(() => navigate("/dashboard"), 3000);
      setLoading(false);
      return;
    }

    try {
      const videoDetailsResponse = await axiosInstance.get(`/videos/${videoId}`);
      setVideo(videoDetailsResponse.data);

      const urlResponse = await axiosInstance.get(`/videos/${videoId}/hls-stream-url`);
      const relativeUrlFromBackend = urlResponse.data;

      if (relativeUrlFromBackend) {
        const fullAbsoluteUrl = `http://localhost:8082${relativeUrlFromBackend}`;
        setHlsPlaybackUrl(fullAbsoluteUrl);
        console.log("Full HLS Playback URL (for Video.js):", fullAbsoluteUrl);
      } else {
        console.error("HLS stream URL is empty or null from backend.");
        throw new Error("HLS stream URL not received from backend.");
      }

      toast.success("Video loaded successfully!");
    } catch (err) {
      console.error("Failed to load video or HLS stream URL:", err);
      const errorMessage =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Failed to load video. It might not exist, be unavailable, or you lack permission.";
      setError(errorMessage);
      toast.error(errorMessage);

      setTimeout(() => {
        navigate("/dashboard");
      }, 3000);
    } finally {
      setLoading(false);
    }
  }, [videoId, navigate]);

  useEffect(() => {
    fetchVideoData();
  }, [fetchVideoData]);

  useEffect(() => {
    if (videoId && !hasIncrementedViewForVideoId.current[videoId]) {
      axiosInstance
        .patch(`/videos/${videoId}/views`)
        .then(() => {
          console.log(`Views incremented for video ${videoId}`);
          hasIncrementedViewForVideoId.current[videoId] = true;
        })
        .catch((err) => {
          console.error(`Failed to increment view for video ${videoId}:`, err);
        });
    }
  }, [videoId]);


  // --- Video.js Initialization and Cleanup ---
  useEffect(() => {
    if (hlsPlaybackUrl && videoRef.current) {
      // Ensure player is only initialized once
      if (!playerRef.current) {
        const videoJsOptions = {
          autoplay: false, // Control autoplay here
          controls: true,
          responsive: true,
          fluid: true, // Makes the player fill the parent container while maintaining aspect ratio
          sources: [{
            src: hlsPlaybackUrl,
            type: 'application/x-mpegURL', // MIME type for HLS
          }],
        };

        const player = videojs(videoRef.current, videoJsOptions, () => {
          console.log('Video.js player is ready!');
          playerRef.current = player;

          // Removed: qualityLevels API usage as it's not available without the specific plugin
          // @videojs/http-streaming handles adaptive bitrate automatically in the background.
        });
      } else {
        // If player already exists, just update the source
        playerRef.current.src({
          src: hlsPlaybackUrl,
          type: 'application/x-mpegURL',
        });
      }
    }

    // Cleanup: Dispose the player when the component unmounts
    return () => {
      if (playerRef.current) {
        playerRef.current.dispose();
        playerRef.current = null;
      }
    };
  }, [hlsPlaybackUrl]);


  return (
    <div className="player-layout">
      <Header />
      <div className="player-main-content">
        <Sidebar />
        <div className="player-content-area">
          {loading ? (
            <LoadingSpinner />
          ) : error ? (
            <div className="video-error-container">
              <p className="video-error-message">{error}</p>
              <p className="video-redirect-message">
                Redirecting to Dashboard...
              </p>
            </div>
          ) : video && hlsPlaybackUrl ? (
            <div className="video-player-container">
              <div className="player-wrapper">
                {/* The HTML5 video element that Video.js will enhance */}
                <video
                  ref={videoRef}
                  className="video-js vjs-default-skin" // Essential classes for Video.js styling
                ></video>
              </div>

              <div className="video-details">
                <h1 className="video-player-title">{video.videoName}</h1>
                <p className="video-player-views">{video.views} views</p>
                <p className="video-player-description">{video.description}</p>
                <p className="video-player-channel">
                  Uploaded by: {video.uploadUsername}
                </p>
              </div>
            </div>
          ) : (
            <p className="placeholder-text">No video data available or stream URL not ready.</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default VideoPlayerPage;