// src/components/VideoPlayerPage.jsx

import React, {
  useEffect,
  useState,
  useCallback,
  useRef,
  useContext,
} from "react";
import { useParams, useNavigate } from "react-router-dom";
import ReactPlayer from "react-player";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import { AuthContext } from "../context/AuthContext";
import LoadingSpinner from "../components/LoadingSpinner";
import axiosInstance from "../api/axiosInstance";
import { toast } from "react-toastify";
import "../css/VideoPlayerPage.css";

const VideoPlayerPage = () => {
  const { videoId } = useParams();
  const navigate = useNavigate();
  const [video, setVideo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const hasIncrementedViewForVideoId = useRef({});
  const { accessToken } = useContext(AuthContext); // Get accessToken from AuthContext

  // Add the console.log here as a sanity check for accessToken availability
  console.log("VideoPlayerPage - Current accessToken:", accessToken);

  const fetchVideoDetails = useCallback(async () => {
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
      const response = await axiosInstance.get(`/videos/${videoId}`);
      setVideo(response.data);
      toast.success("Video loaded successfully!");
    } catch (err) {
      console.error("Failed to fetch video details:", err);
      const errorMessage =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Failed to load video details. It might not exist or be unavailable.";
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
    fetchVideoDetails();
  }, [fetchVideoDetails]);

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

  // Construct the video URL for ReactPlayer
  const videoPlaybackUrl =
    videoId && video
      ? `http://localhost:8082/api/videos/stream/${videoId}`
      : "";

  // Define headers for the stream
  // Only include Authorization header if accessToken is truly available
  const streamHeaders = accessToken && accessToken !== "null" && accessToken.length > 0
    ? { 'Authorization': `Bearer ${accessToken}` }
    : {};

  return (
    <div className="video-player-page-layout">
      <Header />
      <div className="video-player-main-content">
        <Sidebar />
        <div className="video-content-area">
          {loading ? (
            <LoadingSpinner />
          ) : error ? (
            <div className="video-error-container">
              <p className="video-error-message">{error}</p>
              <p className="video-redirect-message">
                Redirecting to Dashboard...
              </p>
            </div>
          ) : video ? (
            <>
              <div className="player-wrapper">
                {videoPlaybackUrl ? (
                  <ReactPlayer
                    url={videoPlaybackUrl}
                    className="react-player"
                    playing={true}
                    controls={true}
                    width="100%"
                    height="100%"
                    onError={handlePlayerError}
                    config={{
                      file: {
                        attributes: {
                          crossOrigin: "use-credentials",
                        },
                        // <--- NEW / MODIFIED PART START --->
                        // This sends headers for direct file playback (MP4, etc.)
                        httpHeaders: streamHeaders,

                        // hlsOptions.xhrSetup is STILL needed for HLS (large videos)
                        // It's good to keep it for when your backend *does* serve HLS.
                        hlsOptions: {
                          xhrSetup: (xhr, url) => {
                            console.log(`[HLS.js xhrSetup] Intercepting request for: ${url}`);
                            console.log(`[HLS.js xhrSetup] Current accessToken: ${accessToken}`);

                            if (accessToken && accessToken !== "null" && accessToken.length > 0) {
                              xhr.setRequestHeader(
                                "Authorization",
                                `Bearer ${accessToken}`
                              );
                              console.log(
                                `[HLS.js xhrSetup] Set Authorization header for: ${url.substring(
                                  0,
                                  Math.min(url.length, 100)
                                )}...`
                              );
                            } else {
                              console.warn(
                                `[HLS.js xhrSetup] Skipping Authorization header for: ${url} (No token or token is null/empty).`
                              );
                            }
                          },
                          debug: true, // Keep this for HLS.js specific debugging
                        },
                        // <--- NEW / MODIFIED PART END --->
                      },
                    }}
                  />
                ) : (
                  <div className="video-error-container">
                    <p className="video-error-message">
                      Video source URL could not be generated.
                    </p>
                    <p>Check video ID and processing status.</p>
                  </div>
                )}
              </div>
              <div className="video-details">
                <h1 className="video-title">{video.videoName}</h1>
                <p className="video-views">{video.views} views</p>
                <p className="video-description">{video.description}</p>
                <p className="video-uploader">
                  Uploaded by: {video.uploadUsername}
                </p>
              </div>
            </>
          ) : (
            <p className="placeholder-text">No video data available.</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default VideoPlayerPage;