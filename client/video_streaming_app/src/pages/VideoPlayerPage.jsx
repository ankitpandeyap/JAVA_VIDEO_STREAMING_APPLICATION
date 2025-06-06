// src/pages/VideoPlayerPage.jsx
import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import ReactPlayer from "react-player";
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

  // State to control playing and player dimensions
  const [isPlaying, setIsPlaying] = useState(false); // Initialized to false
  const playerRef = useRef(null);

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
      const videoDetailsResponse = await axiosInstance.get(
        `/videos/${videoId}`
      );
      setVideo(videoDetailsResponse.data);

      const urlResponse = await axiosInstance.get(
        `/videos/${videoId}/hls-stream-url`
      );
      const relativeUrlFromBackend = urlResponse.data;

      if (relativeUrlFromBackend) {
        const fullAbsoluteUrl = `http://localhost:8082${relativeUrlFromBackend}`;
        setHlsPlaybackUrl(fullAbsoluteUrl);
        console.log(
          "Full HLS Playback URL (for ReactPlayer):",
          fullAbsoluteUrl
        );
        // REMOVED: setIsPlaying(true); // Still removed for no autoplay
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

  const handlePlayerError = (err) => {
    console.error("ReactPlayer error:", err);
    toast.error(
      "Video playback error. The video might not be available or is corrupted."
    );
  };

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

  const videoPlaybackUrl = hlsPlaybackUrl;

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
          ) : video && videoPlaybackUrl ? (
            <div className="video-player-container">
              <div className="player-wrapper">
                <ReactPlayer
                  ref={playerRef}
                  url={videoPlaybackUrl}
                  className="react-player"
                  playing={isPlaying}
                  controls={true}
                  width="100%"
                  height="100%"
                  onError={handlePlayerError}
                  onPlay={() => setIsPlaying(true)} // <-- ADDED: Update state when player starts playing
                  onPause={() => setIsPlaying(false)} // <-- ADDED: Update state when player pauses
                  config={{
                    file: {
                      attributes: {
                        crossOrigin: "anonymous",
                      },
                      hlsOptions: {
                        debug: true,
                      },
                    },
                  }}
                />
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
            <p className="placeholder-text">
              No video data available or stream URL not ready.
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default VideoPlayerPage;
