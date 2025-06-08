import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import ReactPlayer from "react-player"; // Import ReactPlayer
import Hls from "hls.js"; // Import Hls.js

import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import LoadingSpinner from "../components/LoadingSpinner";
import axiosInstance from "../api/axiosInstance";
import { toast } from "react-toastify";
import "../css/VideoPlayerPage.css"; // Keep your existing CSS

const VideoPlayerPage = () => {
  const { videoId } = useParams();
  const navigate = useNavigate();
  const [video, setVideo] = useState(null);
  const [hlsPlaybackUrl, setHlsPlaybackUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const hasIncrementedViewForVideoId = useRef({});

  // ReactPlayer specific refs and states
  const playerRef = useRef(null); // Ref to the ReactPlayer component
  const hlsRef = useRef(null); // Ref to the Hls.js instance

  const [qualityLevels, setQualityLevels] = useState([]);
  const [currentQuality, setCurrentQuality] = useState("auto"); // Default: "auto" for adaptive

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
        console.log("Full HLS Playback URL:", fullAbsoluteUrl);
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
  }, [videoId, navigate, setLoading, setError, setVideo, setHlsPlaybackUrl]);

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

  // --- HLS.js Quality Level Management ---
  const updateQualityLevels = useCallback(() => {
    if (!hlsRef.current) return;

    const hls = hlsRef.current;
    const currentLevels = hls.levels;
    const availableQualities = [];

    // Add 'Auto' option first
    availableQualities.push({ label: "Auto", value: "auto" });

    currentLevels.forEach((level, index) => {
      // level.height provides the resolution
      if (level.height) {
        availableQualities.push({
          label: `${level.height}p`,
          value: index, // Use the index of the level for selection in Hls.js
        });
      }
    });

    // Sort qualities numerically, keeping 'Auto' at the top
    availableQualities.sort((a, b) => {
      if (a.value === "auto") return -1;
      if (b.value === "auto") return 1;
      return parseInt(a.label) - parseInt(b.label);
    });

    setQualityLevels((prevQualities) => {
      // Only update if the qualities have actually changed to prevent unnecessary re-renders
      if (
        prevQualities.length !== availableQualities.length ||
        !prevQualities.every(
          (val, index) => val.value === availableQualities[index].value
        )
      ) {
        console.log("Setting quality levels:", availableQualities);
        return availableQualities;
      }
      return prevQualities;
    });

    // Update currentQuality state based on Hls.js's current level
    if (hls.currentLevel === -1) {
      setCurrentQuality("auto"); // -1 indicates auto selection
      console.log("Hls.js: Current quality is Auto.");
    } else {
      const selectedLevel = hls.levels[hls.currentLevel];
      if (selectedLevel) {
        setCurrentQuality(hls.currentLevel); // Use the index as the value
        console.log(`Hls.js: Current quality is ${selectedLevel.height}p.`);
      }
    }
  }, []);

  const handleQualityChange = useCallback(
    (selectedQualityValue) => {
      if (!hlsRef.current) {
        toast.error("Player not ready to change quality.");
        return;
      }
      const hls = hlsRef.current;

      if (selectedQualityValue === "auto") {
        hls.currentLevel = -1; // -1 for auto selection in hls.js
        setCurrentQuality("auto");
        toast.info("Quality set to Auto (Adaptive)");
        console.log("Hls.js: Manual quality set to Auto.");
      } else {
        const levelIndex = parseInt(selectedQualityValue, 10);
        if (levelIndex >= 0 && levelIndex < hls.levels.length) {
          hls.currentLevel = levelIndex;
          setCurrentQuality(levelIndex);
          const selectedLevelHeight = hls.levels[levelIndex].height;
          toast.info(`Quality set to ${selectedLevelHeight}p`);
          console.log(`Hls.js: Manual quality set to ${selectedLevelHeight}p.`);
        } else {
          toast.warn(`Selected quality ${selectedQualityValue} not found.`);
        }
      }
    },
    []
  );

  // --- ReactPlayer Custom HLS.js configuration ---
  const handleHlsRef = useCallback((hlsInstance) => {
    if (hlsInstance) {
      hlsRef.current = hlsInstance;
      console.log("Hls.js instance obtained:", hlsInstance);

      // Listen for when Hls.js finishes loading the manifest and levels are available
      hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
        console.log("Hls.js: MANIFEST_PARSED event fired!");
        updateQualityLevels();
      });

      // Listen for when quality changes (adaptive or manual)
      hlsInstance.on(Hls.Events.LEVEL_SWITCHED, (eventName, data) => {
        console.log("Hls.js: LEVEL_SWITCHED event fired!", data);
        updateQualityLevels();
      });

      hlsInstance.on(Hls.Events.ERROR, (eventName, data) => {
        console.error("Hls.js Error:", eventName, data);
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              toast.error(
                "Network error during HLS playback. Please check your connection."
              );
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              toast.error(
                "Media error during HLS playback. Trying to recover..."
              );
              hlsInstance.recoverMediaError();
              break;
            default:
              toast.error(
                "A fatal HLS playback error occurred. Please try again."
              );
              break;
          }
        }
      });
    } else {
      // Clean up Hls.js instance when component unmounts or player unloads
      if (hlsRef.current) {
        console.log("Hls.js instance being destroyed.");
        hlsRef.current.destroy();
        hlsRef.current = null;
        setQualityLevels([]);
        setCurrentQuality("auto");
      }
    }
  }, [updateQualityLevels]); // updateQualityLevels is a dependency for useCallback

  // Custom HLS.js config for ReactPlayer
  const config = {
    file: {
      forceHLS: true, // Crucial for ReactPlayer to use HLS.js for HLS streams
      hlsOptions: {
        // You can add HLS.js specific options here if needed
        // For example:
        // fragLoadingMaxRetry: 5,
        // fragLoadingRetryDelay: 500,
      },
      hlsVersion: Hls.version, // Use the version from the Hls.js import
    },
  };

  const handleReady = useCallback((player) => {
    // This `player` object from ReactPlayer's onReady contains the internal player instance
    // For HLS streams, this `player` refers to the <video> element
    if (Hls.isSupported()) {
      const hls = new Hls();
      hls.loadSource(hlsPlaybackUrl);
      hls.attachMedia(player); // Attach HLS.js to the video element
      handleHlsRef(hls); // Store the hls instance and set up its listeners
    } else if (player.canPlayType('application/vnd.apple.mpegurl')) {
      // Native HLS support (Safari on macOS/iOS)
      player.src = hlsPlaybackUrl;
      console.log("Using native HLS playback.");
    } else {
      toast.error("Your browser does not support HLS playback.");
      setError("HLS playback not supported on this browser.");
    }
  }, [hlsPlaybackUrl, handleHlsRef]);

  // Clean up Hls.js instance when component unmounts or URL changes
  useEffect(() => {
    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, []); // Run only on unmount


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
                <ReactPlayer
                  ref={playerRef}
                  url={hlsPlaybackUrl}
                  playing={false} // Start paused
                  controls={true} // ReactPlayer provides its own controls by default
                  width="100%"
                  height="100%"
                  config={config} // Pass the custom HLS.js config
                  onReady={handleReady} // This is where we initiate Hls.js
                  onError={(e) => console.error("ReactPlayer error:", e)}
                  // You might want to add more event handlers here if needed
                  // e.g., onPlay, onPause, onEnded
                />
              </div>

              {/* --- Manual Quality Selector Dropdown --- */}
              {qualityLevels.length > 1 && ( // Only show if more than just "Auto" is available
                <div className="quality-selector-container">
                  <label htmlFor="quality-select" className="quality-label">
                    Quality:
                  </label>
                  <select
                    id="quality-select"
                    className="quality-select"
                    value={currentQuality}
                    onChange={(e) => handleQualityChange(e.target.value)}
                  >
                    {qualityLevels.map((q) => (
                      <option key={q.value} value={q.value}>
                        {q.label}
                      </option>
                    ))}
                  </select>
                </div>
              )}
              {/* --- End Manual Quality Selector Dropdown --- */}

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