import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";

// CORE VIDEO.JS AND ITS PLUGINS
import videojs from "video.js";
import "@videojs/http-streaming";
import "videojs-contrib-quality-levels";

import "video.js/dist/video-js.css";

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
  const [qualityLevels, setQualityLevels] = useState([]); // State to store available quality levels
  // MODIFIED: currentQuality now stores the 'value' from the option, not just the label.
  const [currentQuality, setCurrentQuality] = useState("auto"); // Default: "auto" to match the 'Auto' option's value

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

  // --- Video.js Initialization and Cleanup ---
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (hlsPlaybackUrl && videoRef.current && !loading && !error) {
      if (!playerRef.current) {
        console.log('Video.js: Attempting to initialize new player instance...');

        const videoJsOptions = {
          autoplay: false,
          controls: true,
          responsive: true,
          fluid: true,
          sources: [{
            src: hlsPlaybackUrl,
            type: 'application/x-mpegURL', // MIME type for HLS
          }],
          html5: {
            hls: {
              overrideNative: true // IMPORTANT: Required for videojs-contrib-quality-levels to work
            }
          }
        };

        const initializePlayer = () => {
          if (videoRef.current && !playerRef.current) {
            playerRef.current = videojs(videoRef.current, videoJsOptions, function() {
              const player = this; // Capture 'this' for clarity
              console.log('Video.js player is ready and attached to DOM!');

              player.on('error', function() {
                const error = this.error();
                console.error("Video.js Player Error:", error.code, error.message, error.status, error.src);
                toast.error(`Player Error: ${error.message || 'An unknown player error occurred.'}`);
              });
              player.on('loadedmetadata', () => console.log('Video.js: loadedmetadata event fired!'));
              player.on('loadeddata', () => console.log('Video.js: loadeddata event fired!'));
              player.on('play', () => console.log('Video.js: play event fired!'));
              player.on('playing', () => console.log('Video.js: playing event fired!'));
              player.on('waiting', () => console.log('Video.js: waiting event fired!'));

              const levels = player.qualityLevels(); // Access qualityLevels from player instance

              const updateQualityLevelsState = () => {
                const availableQualities = [];
                availableQualities.push({ label: 'Auto', value: 'auto' });

                for (let i = 0; i < levels.length; i++) {
                  const level = levels[i];
                  if (level.height || level.width) {
                    const label = level.height ? `${level.height}p` : `${level.width}p`;
                    availableQualities.push({ label: label, value: level.id });
                  }
                }

                availableQualities.sort((a, b) => {
                  if (a.value === 'auto') return -1;
                  if (b.value === 'auto') return 1;
                  const resA = parseInt(a.label);
                  const resB = parseInt(b.label);
                  return resA - resB;
                });

                setQualityLevels(prevQualities => {
                    if (prevQualities.length !== availableQualities.length ||
                        !prevQualities.every((val, index) => val.value === availableQualities[index].value)) {
                        console.log("Setting quality levels:", availableQualities);
                        return availableQualities;
                    }
                    return prevQualities;
                });

                // MODIFIED: Log levels.auto and current state clearly
                console.log(`QualityLevels state - levels.auto: ${levels.auto}`);

                if (levels.auto) {
                    setCurrentQuality("auto");
                    console.log("Setting currentQuality state to 'auto' (adaptive mode confirmed).");
                } else {
                    const currentManualLevel = levels.levels_.find(level => level.enabled);
                    if (currentManualLevel) {
                        setCurrentQuality(currentManualLevel.id);
                        console.log("Setting currentQuality state to manual quality ID:", currentManualLevel.id);
                    } else {
                        // Fallback: If no level is explicitly enabled and not in auto mode, default to 'auto'
                        setCurrentQuality("auto");
                        console.log("Fallback: Player quality state ambiguous, setting to 'auto'.");
                    }
                }
              };

              // Assert levels.auto = true when the player is ready to load the source
              // This is a more direct attempt to force adaptive mode from the start
              player.on('loadedmetadata', () => {
                const levels = player.qualityLevels();
                // Ensure all levels are enabled before trying to set auto
                for (let i = 0; i < levels.length; i++) {
                    levels[i].enabled = true;
                }
                levels.auto = true; // Assert adaptive mode
                console.log(`Video.js: levels.auto set to true on loadedmetadata. Current auto state: ${levels.auto}`);
                updateQualityLevelsState(); // Update dropdown immediately after
              });

              // Listen for actual quality changes
              levels.on('addqualitylevel', updateQualityLevelsState);
              levels.on('removequalitylevel', updateQualityLevelsState);
              levels.on('change', () => {
                // MODIFIED: Log levels.auto on change event
                console.log(`QualityLevels change event - levels.auto: ${levels.auto}`);
                if (levels.auto) {
                    setCurrentQuality("auto");
                    console.log("Player quality changed via event to: Auto (adaptive selected).");
                } else {
                    const currentManualLevel = levels.levels_.find(level => level.enabled);
                    if (currentManualLevel) {
                        setCurrentQuality(currentManualLevel.id);
                        console.log("Player quality changed via event to:", currentManualLevel.id);
                    } else {
                        setCurrentQuality("auto"); // Fallback
                        console.log("Player quality changed via event to: Auto (manual selection cleared).");
                    }
                }
              });

              // Initial population of quality levels
              setTimeout(updateQualityLevelsState, 200);
              player.load();
            });
          }
        };
        setTimeout(initializePlayer, 0); // Defer player init to ensure DOM readiness

      } else {
        // If player already exists and hlsPlaybackUrl changes, just update the source
        console.log('Video.js: Updating existing player source with new URL.');
        const player = playerRef.current;

        player.reset();
        player.src({
          src: hlsPlaybackUrl,
          type: 'application/x-mpegURL',
        });

        setQualityLevels([]);
        setCurrentQuality("auto");

        const levels = player.qualityLevels();
        levels.off('addqualitylevel');
        levels.off('removequalitylevel');
        levels.off('change');

        const updateQualityLevelsStateOnUpdate = () => {
            const availableQualities = [];
            availableQualities.push({ label: 'Auto', value: 'auto' });
            for (let i = 0; i < levels.length; i++) {
                const level = levels[i];
                if (level.height || level.width) {
                    const label = level.height ? `${level.height}p` : `${level.width}p`;
                    availableQualities.push({ label: label, value: level.id });
                }
            }
            availableQualities.sort((a, b) => {
                if (a.value === 'auto') return -1;
                if (b.value === 'auto') return 1;
                const resA = parseInt(a.label);
                const resB = parseInt(b.label);
                return resA - resB;
            });

            setQualityLevels(prevQualities => {
                if (prevQualities.length !== availableQualities.length ||
                    !prevQualities.every((val, index) => val.value === availableQualities[index].value)) {
                    console.log("Setting quality levels (on source update):", availableQualities);
                    return availableQualities;
                }
                return prevQualities;
            });

            console.log(`QualityLevels state (on update) - levels.auto: ${levels.auto}`);
            if (levels.auto) {
                setCurrentQuality("auto");
                console.log("Player is in adaptive mode (on update). Setting currentQuality state to 'auto'.");
            } else {
                const currentManualLevel = levels.levels_.find(level => level.enabled);
                if (currentManualLevel) {
                    setCurrentQuality(currentManualLevel.id);
                    console.log("Player is in manual mode (on update). Current manual quality ID:", currentManualLevel.id);
                } else {
                    setCurrentQuality("auto");
                    console.log("Fallback (on update): Player quality state is ambiguous, setting to 'auto'.");
                }
            }
        };

        player.on('loadedmetadata', () => { // Ensure 'auto' is asserted on new source
            const levels = player.qualityLevels();
            for (let i = 0; i < levels.length; i++) {
                levels[i].enabled = true;
            }
            levels.auto = true;
            console.log(`Video.js: levels.auto set to true on loadedmetadata (on update). Current auto state: ${levels.auto}`);
            updateQualityLevelsStateOnUpdate();
        });

        levels.on('addqualitylevel', updateQualityLevelsStateOnUpdate);
        levels.on('removequalitylevel', updateQualityLevelsStateOnUpdate);
        levels.on('change', () => {
            console.log(`QualityLevels change event (on update) - levels.auto: ${levels.auto}`);
            if (levels.auto) {
                setCurrentQuality("auto");
                console.log("Player quality changed via event (on update) to: Auto (adaptive selected).");
            } else {
                const currentManualLevel = levels.levels_.find(level => level.enabled);
                if (currentManualLevel) {
                    setCurrentQuality(currentManualLevel.id);
                    console.log("Player quality changed via event (on update) to:", currentManualLevel.id);
                } else {
                    setCurrentQuality("auto");
                    console.log("Player quality changed via event (on update) to: Auto (manual selection cleared).");
                }
            }
        });
        setTimeout(updateQualityLevelsStateOnUpdate, 200);
        player.load();
      }
    } else {
      console.log("Video.js init skipped or pending:", {
        hlsPlaybackUrl: hlsPlaybackUrl ? "present" : "null",
        videoRefCurrent: videoRef.current ? "present" : "null",
        loading: loading,
        error: error
      });
      if ((loading || error) && playerRef.current) {
        console.log("Disposing player due to loading/error state transition.");
        playerRef.current.dispose();
        playerRef.current = null;
        setQualityLevels([]);
        setCurrentQuality("auto");
      }
    }

    return () => {
      if (playerRef.current) {
        console.log("Video.js: Disposing player instance during cleanup.");
        const currentLevels = playerRef.current.qualityLevels();
        currentLevels.off('addqualitylevel');
        currentLevels.off('removequalitylevel');
        currentLevels.off('change');
        playerRef.current.off('loadedmetadata'); // Clean up the new listener
        playerRef.current.dispose();
        playerRef.current = null;
      }
    };
  }, [hlsPlaybackUrl, loading, error]);

  // --- Function to handle manual quality change ---
  const handleQualityChange = useCallback((selectedQualityValue) => {
    const player = playerRef.current;
    if (!player) {
      toast.error("Player not ready to change quality.");
      return;
    }

    const levels = player.qualityLevels();
    for (let i = 0; i < levels.length; i++) {
      levels[i].enabled = false;
    }

    if (selectedQualityValue === 'auto') {
      for (let i = 0; i < levels.length; i++) {
        levels[i].enabled = true;
      }
      levels.auto = true;
      setCurrentQuality("auto");
      toast.info("Quality set to Auto (Adaptive)");
    } else {
      const selectedLevel = levels.levels_.find(level => level.id === selectedQualityValue);
      if (selectedLevel) {
        selectedLevel.enabled = true;
        levels.auto = false;
        setCurrentQuality(selectedLevel.id);
        toast.info(`Quality set to ${selectedLevel.height}p`);
      } else {
        toast.warn(`Selected quality ${selectedQualityValue} not found.`);
      }
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
              <div className="player-wrapper">
                <video
                  ref={videoRef}
                  className="video-js vjs-default-skin"
                ></video>
              </div>

              {/* --- Manual Quality Selector Dropdown --- */}
              {qualityLevels.length > 0 && (
                <div className="quality-selector-container">
                  <label htmlFor="quality-select" className="quality-label">Quality:</label>
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
            <p className="placeholder-text">No video data available or stream URL not ready.</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default VideoPlayerPage;