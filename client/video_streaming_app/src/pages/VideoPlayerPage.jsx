import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import ReactPlayer from "react-player";
import Hls from "hls.js";

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

  const playerRef = useRef(null);
  const hlsRef = useRef(null);

  const [qualityLevels, setQualityLevels] = useState([]);
  const [currentQuality, setCurrentQuality] = useState("auto");

  // State to control playback
  const [isPlaying, setIsPlaying] = useState(false); // Start paused

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
        if (hlsPlaybackUrl !== fullAbsoluteUrl) {
            setHlsPlaybackUrl(fullAbsoluteUrl);
            console.log("Updating HLS Playback URL:", fullAbsoluteUrl);
        } else {
            console.log("HLS Playback URL is unchanged, no state update needed.");
        }
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
  }, [videoId, navigate, setLoading, setError, setVideo, hlsPlaybackUrl, setHlsPlaybackUrl]);

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

    availableQualities.push({ label: "Auto", value: "auto" });

    currentLevels.forEach((level, index) => {
      if (level.height) {
        availableQualities.push({
          label: `${level.height}p`,
          value: index,
        });
      }
    });

    availableQualities.sort((a, b) => {
      if (a.value === "auto") return -1;
      if (b.value === "auto") return 1;
      return parseInt(a.label) - parseInt(b.label);
    });

    setQualityLevels((prevQualities) => {
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

    if (hls.currentLevel === -1) {
      setCurrentQuality("auto");
      console.log("Hls.js: Current quality is Auto.");
    } else {
      const selectedLevel = hls.levels[hls.currentLevel];
      if (selectedLevel) {
        setCurrentQuality(hls.currentLevel);
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
        hls.currentLevel = -1;
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

      hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
        console.log("Hls.js: MANIFEST_PARSED event fired!");
        updateQualityLevels();
      });

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
              if (hlsRef.current) {
                hlsRef.current.destroy();
                hlsRef.current = null;
                setQualityLevels([]);
                setCurrentQuality("auto");
              }
              break;
          }
        }
      });
    } else {
      if (hlsRef.current) {
        console.log("Hls.js instance being destroyed.");
        hlsRef.current.destroy();
        hlsRef.current = null;
        setQualityLevels([]);
        setCurrentQuality("auto");
      }
    }
  }, [updateQualityLevels]);

  const config = {
    file: {
      forceHLS: true,
      hlsOptions: {
        // Adding maxBufferLength as discussed, to control how much is buffered
        maxBufferLength: 30, // Example: try to buffer 30 seconds ahead
        maxMaxBufferLength: 60, // Max buffer when idle (e.g., paused)
      },
      hlsVersion: Hls.version,
    },
  };

  const handleReady = useCallback((player) => {
    if (Hls.isSupported()) {
      if (hlsRef.current) {
        console.log("Destroying old Hls.js instance.");
        hlsRef.current.destroy();
        hlsRef.current = null;
      }

      const hls = new Hls();
      hls.loadSource(hlsPlaybackUrl);
      hls.attachMedia(player);
      handleHlsRef(hls);

      console.log("New Hls.js instance created and attached.");
    } else if (player.canPlayType('application/vnd.apple.mpegurl')) {
      if (hlsRef.current) {
        console.log("Destroying Hls.js instance for native playback.");
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      player.src = hlsPlaybackUrl;
      console.log("Using native HLS playback.");
      setQualityLevels([]);
      setCurrentQuality("auto");
    } else {
      toast.error("Your browser does not support HLS playback.");
      setError("HLS playback not supported on this browser.");
    }
  }, [hlsPlaybackUrl, handleHlsRef]);

  useEffect(() => {
    return () => {
      if (hlsRef.current) {
        console.log("Hls.js instance being destroyed on unmount.");
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, []);


  // NEW: Handlers for ReactPlayer play/pause events to keep `isPlaying` state updated
  const handlePlay = useCallback(() => {
    setIsPlaying(true);
    console.log("Video started playing.");
  }, []);

  const handlePause = useCallback(() => {
    setIsPlaying(false);
    console.log("Video paused.");
  }, []);

  // UPDATED: Click handler for the player wrapper
  const handlePlayerWrapperClick = useCallback(() => {
    if (playerRef.current) {
      // Toggle playing state using ReactPlayer's internal mechanism
      // ReactPlayer controls playback via its `playing` prop
      // So, we just need to toggle our `isPlaying` state
      setIsPlaying(prevIsPlaying => !prevIsPlaying);
      console.log("Toggling video play/pause after user click.");
    } else {
      console.warn("playerRef.current is null when trying to toggle playback.");
    }
  }, []);


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
              <div
                className="player-wrapper"
                onClick={handlePlayerWrapperClick}
                style={{ cursor: isPlaying ? 'default' : 'pointer' }}
              >
                <ReactPlayer
                  ref={playerRef}
                  url={hlsPlaybackUrl}
                  playing={isPlaying} // Controlled by our state
                  controls={true}
                  width="100%"
                  height="100%"
                  config={config}
                  onReady={handleReady}
                  onError={(e) => console.error("ReactPlayer error:", e)}
                  onPlay={handlePlay}
                  onPause={handlePause}
                />
              </div>

              {/* --- Manual Quality Selector Dropdown --- */}
              {qualityLevels.length > 1 && (
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