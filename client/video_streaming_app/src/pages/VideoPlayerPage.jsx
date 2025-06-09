import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import ReactPlayer from "react-player";
import Hls from "hls.js";
import { jwtDecode } from "jwt-decode";

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
    const hlsInstanceRef = useRef(null); // This will hold the Hls.js instance

    // Refs for HLS token management
    const currentHlsTokenRef = useRef(null);
    const hlsTokenExpiryRef = useRef(null); // Unix timestamp in milliseconds
    const refreshTimeoutId = useRef(null); // To clear the auto-refresh timeout

    // Function refs to break circular dependencies for useCallback/useEffect
    const refreshHlsTokenRef = useRef();
    const scheduleTokenRefreshRef = useRef();
    const fetchVideoDataRef = useRef(); // For main data fetching logic

    const [qualityLevels, setQualityLevels] = useState([]);
    // Initialize currentQuality to "auto" and keep it that way unless a manual selection is made
    const [currentQuality, setCurrentQuality] = useState("auto");
    const [isPlaying, setIsPlaying] = useState(false);

    // This ref will help us determine if the user has manually selected a quality
    const userSelectedQualityRef = useRef("auto");

    // Helper to extract token and expiry from a full HLS URL
    const extractTokenAndExpiry = useCallback((fullAbsoluteUrl) => {
        try {
            const url = new URL(fullAbsoluteUrl);
            const token = url.searchParams.get("token");
            if (token) {
                const decoded = jwtDecode(token);
                // decoded.exp is in seconds, convert to milliseconds
                const expiryTimeMs = decoded.exp * 1000;
                console.log(`Extracted HLS Token: ${token.substring(0, 20)}...`);
                console.log(`HLS Token expires at: ${new Date(expiryTimeMs).toLocaleString()}`);
                return { token, expiryTimeMs };
            }
        } catch (e) {
            console.error("Failed to decode HLS token or extract expiry:", e);
        }
        return { token: null, expiryTimeMs: null };
    }, []);

    // --- HLS.js Quality Level Management (Called when Hls.js reports levels) ---
    const updateQualityLevels = useCallback(() => {
        const hls = hlsInstanceRef.current;
        if (!hls || !hls.levels || hls.levels.length === 0) {
            setQualityLevels([]);
            // Even if no levels, we want to show "Auto" if nothing is playing
            setCurrentQuality("auto");
            return;
        }

        const currentLevels = hls.levels;
        const availableQualities = [];

        availableQualities.push({ label: "Auto", value: "auto" });

        currentLevels.forEach((level, index) => {
            if (level.height) {
                availableQualities.push({
                    label: `${level.height}p`,
                    value: index, // The value should be the index for Hls.js
                });
            }
        });

        // Sort qualities by height, keeping "Auto" at the top
        availableQualities.sort((a, b) => {
            if (a.value === "auto") return -1;
            if (b.value === "auto") return 1;
            // Extract numeric part for reliable sorting (e.g., "720p" -> 720)
            const heightA = parseInt(a.label);
            const heightB = parseInt(b.label);
            return heightA - heightB;
        });

        setQualityLevels((prevQualities) => {
            if (JSON.stringify(prevQualities) !== JSON.stringify(availableQualities)) {
                console.log("Setting quality levels:", availableQualities);
                return availableQualities;
            }
            return prevQualities;
        });

        // Only update currentQuality if the user hasn't explicitly selected one
        // or if we are returning to 'auto' from a manual selection.
        if (userSelectedQualityRef.current === "auto") {
            setCurrentQuality("auto");
            console.log("Hls.js: Player is in Auto mode, dropdown displays 'Auto'.");
        } else {
            // If user has selected a quality, stick to it.
            // This ensures the dropdown doesn't jump back to an auto-selected resolution.
            const selectedLevel = hls.levels[hls.currentLevel];
            if (selectedLevel) {
                setCurrentQuality(hls.currentLevel);
                console.log(`Hls.js: Current quality is ${selectedLevel.height}p (user selected/level switched).`);
            } else {
                // Fallback to auto if the user-selected level becomes invalid for some reason
                setCurrentQuality("auto");
                userSelectedQualityRef.current = "auto";
                console.warn("Hls.js: User selected level became invalid, reverting to Auto.");
            }
        }
    }, []);

    // Function to handle quality change from dropdown
    const handleQualityChange = useCallback(
        (selectedQualityValue) => {
            const hls = hlsInstanceRef.current;
            if (!hls) {
                toast.error("Player not ready to change quality.");
                return;
            }

            if (selectedQualityValue === "auto") {
                hls.currentLevel = -1; // -1 means auto quality
                setCurrentQuality("auto"); // Reflect "Auto" in the dropdown
                userSelectedQualityRef.current = "auto"; // Mark that user chose auto
                toast.info("Quality set to Auto (Adaptive)");
                console.log("Hls.js: Manual quality set to Auto.");
            } else {
                const levelIndex = parseInt(selectedQualityValue, 10);
                if (levelIndex >= 0 && levelIndex < hls.levels.length) {
                    hls.currentLevel = levelIndex;
                    setCurrentQuality(levelIndex); // Update state with the index
                    userSelectedQualityRef.current = levelIndex; // Mark that user chose this quality
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

    // Define refreshHlsToken using useCallback, then store in ref
    const refreshHlsToken = useCallback(async () => {
        console.log("Attempting to refresh HLS token...");
        try {
            const urlResponse = await axiosInstance.get(`/videos/${videoId}/hls-stream-url`);
            const relativeUrlFromBackend = urlResponse.data;

            if (relativeUrlFromBackend) {
                const newFullAbsoluteUrl = `http://localhost:8082${relativeUrlFromBackend}`;
                const { token, expiryTimeMs } = extractTokenAndExpiry(newFullAbsoluteUrl);

                if (token && expiryTimeMs) {
                    currentHlsTokenRef.current = token;
                    hlsTokenExpiryRef.current = expiryTimeMs;
                    console.log("New token obtained and stored. Dynamic xhrSetup will use it.");
                    toast.info("HLS stream token refreshed successfully.");
                    setHlsPlaybackUrl(newFullAbsoluteUrl); // Keep this to potentially re-trigger ReactPlayer if URL changes beyond token
                    scheduleTokenRefreshRef.current();
                    return true;
                } else {
                    console.error("New HLS token or expiry not found after refresh attempt.");
                    toast.error("Failed to get new stream token. Playback might stop.");
                }
            } else {
                console.error("HLS stream URL is empty or null from backend during refresh.");
                toast.error("Stream URL missing during refresh. Playback might stop.");
            }
        } catch (err) {
            console.error("Failed to refresh HLS token:", err);
            const errorMessage = err.response?.data?.message || err.response?.data?.error || "Failed to refresh stream token.";
            toast.error(errorMessage);
        }
        return false;
    }, [videoId, extractTokenAndExpiry, setHlsPlaybackUrl]);

    // Define scheduleTokenRefresh using useCallback, then store in ref
    const scheduleTokenRefresh = useCallback(() => {
        if (refreshTimeoutId.current) {
            clearTimeout(refreshTimeoutId.current);
            refreshTimeoutId.current = null;
        }

        const expiry = hlsTokenExpiryRef.current;
        if (expiry) {
            const now = Date.now();
            const refreshTime = Math.max(0, expiry - now - (30 * 1000)); // Refresh 30 seconds before expiry

            console.log(`HLS token will refresh in ${Math.round(refreshTime / 1000)} seconds.`);
            if (refreshTime > 0) {
                refreshTimeoutId.current = setTimeout(() => {
                    console.log("Proactively refreshing HLS token...");
                    refreshHlsTokenRef.current();
                }, refreshTime);
            } else {
                console.warn("HLS token already expired or very close. Refreshing immediately.");
                refreshHlsTokenRef.current();
            }
        }
    }, []);

    // Define fetchVideoData using useCallback, then store in ref
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
                const { token, expiryTimeMs } = extractTokenAndExpiry(fullAbsoluteUrl);

                if (token && expiryTimeMs) {
                    currentHlsTokenRef.current = token;
                    hlsTokenExpiryRef.current = expiryTimeMs;
                    setHlsPlaybackUrl(fullAbsoluteUrl); // This will cause ReactPlayer to try and load
                    console.log("Initial HLS Playback URL set:", fullAbsoluteUrl);
                    scheduleTokenRefreshRef.current();
                } else {
                    console.error("Initial HLS token or expiry not found.");
                    throw new Error("Failed to obtain HLS stream token.");
                }
            } else {
                console.error("HLS stream URL is empty or null from backend.");
                throw new Error("HLS stream URL not received from backend.");
            }
            toast.success("Video loaded successfully!");
        } catch (err) {
            console.error("Failed to load video or HLS stream URL:", err);
            const errorMessage = err.response?.data?.message || err.response?.data?.error || "Failed to load video. It might not exist, be unavailable, or you lack permission.";
            setError(errorMessage);
            toast.error(errorMessage);
            setTimeout(() => {
                navigate("/dashboard");
            }, 3000);
        } finally {
            setLoading(false);
        }
    }, [videoId, navigate, setLoading, setError, setVideo, extractTokenAndExpiry, setHlsPlaybackUrl]);

    // Store functions in refs after they are defined.
    useEffect(() => {
        refreshHlsTokenRef.current = refreshHlsToken;
        scheduleTokenRefreshRef.current = scheduleTokenRefresh;
        fetchVideoDataRef.current = fetchVideoData;
    }, [refreshHlsToken, scheduleTokenRefresh, fetchVideoData]);

    // Initial data fetch effect (uses the ref to call fetchVideoData)
    useEffect(() => {
        if (fetchVideoDataRef.current) {
            fetchVideoDataRef.current();
        }

        return () => {
            // Cleanup on unmount
            if (refreshTimeoutId.current) {
                clearTimeout(refreshTimeoutId.current);
                refreshTimeoutId.current = null;
            }
            if (hlsInstanceRef.current) {
                hlsInstanceRef.current.destroy();
                hlsInstanceRef.current = null;
            }
        };
    }, [videoId]);

    // Increment view count effect
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

    // Callback when ReactPlayer is ready and has loaded its internal player
    const onPlayerReady = useCallback((player) => {
        // player is the ReactPlayer instance. We need its internal Hls.js instance.
        // For HLS, ReactPlayer exposes the Hls.js instance if `forceHLS: true` in config.
        const hls = player.getInternalPlayer('hls');

        if (hls && Hls.isSupported()) {
            hlsInstanceRef.current = hls; // Store the instance for later use
            console.log("ReactPlayer provided Hls.js instance via onReady:", hls);

            // Set initial quality to "auto" in Hls.js to ensure adaptive behavior
            hls.currentLevel = -1;
            setCurrentQuality("auto"); // Ensure dropdown shows "Auto"

            // Configure dynamic xhrSetup here to use the latest token from the ref
            hls.config.xhrSetup = (xhr, url) => {
                const urlObj = new URL(url);
                const tokenToUse = currentHlsTokenRef.current; // Get the latest token from the ref
                if (tokenToUse) {
                    urlObj.searchParams.set("token", tokenToUse);
                }
                xhr.open("GET", urlObj.toString());
            };
            console.log("Hls.js xhrSetup configured for dynamic token inclusion.");

            // Attach all your Hls.js event listeners here
            hls.on(Hls.Events.MANIFEST_PARSED, () => {
                console.log("Hls.js: MANIFEST_PARSED event fired!");
                updateQualityLevels(); // Update dropdown with parsed levels
            });

            hls.on(Hls.Events.LEVEL_SWITCHED, (eventName, data) => {
                console.log("Hls.js: LEVEL_SWITCHED event fired!", data);
                // Only update currentQuality state if user is in 'auto' mode
                // or if the switch was triggered by adaptive logic, not a manual selection.
                // We rely on userSelectedQualityRef to manage this.
                if (userSelectedQualityRef.current === "auto") {
                     updateQualityLevels(); // This will re-evaluate and likely keep it "auto" in dropdown
                }
            });

            hls.on(Hls.Events.ERROR, (eventName, data) => {
                console.error("Hls.js Error:", eventName, data);
                if (data.fatal) {
                    switch (data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            if (data.response && (data.response.code === 401 || data.response.code === 403)) {
                                console.warn("HLS token expired or unauthorized. Attempting refresh...");
                                refreshHlsTokenRef.current(); // Use the ref to call refresh
                            } else {
                                toast.error("Network error during HLS playback. Please check your connection.");
                                // Attempt to recover if the error is not due to authorization
                                if (hls.media) hls.recoverMediaError();
                            }
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            toast.error("Media error during HLS playback. Trying to recover...");
                            if (hls.media) hls.recoverMediaError();
                            break;
                        default:
                            toast.error("A fatal HLS playback error occurred. Please try again.");
                            if (hlsInstanceRef.current) {
                                hlsInstanceRef.current.destroy();
                                hlsInstanceRef.current = null;
                                setQualityLevels([]);
                                setCurrentQuality("auto");
                            }
                            break;
                    }
                }
            });
        } else if (player.canPlayType('application/vnd.apple.mpegurl')) {
            // Fallback to native HLS support for Safari/iOS
            console.log("Using native HLS playback (browser supports application/vnd.apple.mpegurl).");
            // ReactPlayer handles source setting for native HLS when `forceHLS: true` is not applicable
            setQualityLevels([]); // No quality levels to display for native HLS
            setCurrentQuality("auto"); // Always "Auto" for native playback
        } else {
            toast.error("Your browser does not support HLS playback.");
            setError("HLS playback not supported on this browser.");
            setQualityLevels([]);
            setCurrentQuality("auto");
        }
    }, [updateQualityLevels]);


    // Handlers for ReactPlayer play/pause events to keep `isPlaying` state updated
    const handlePlay = useCallback(() => {
        setIsPlaying(true);
        console.log("Video started playing.");
    }, []);

    const handlePause = useCallback(() => {
        setIsPlaying(false);
        console.log("Video paused.");
    }, []);

    const config = {
        file: {
            forceHLS: true, // This tells ReactPlayer to use Hls.js for HLS URLs
            hlsOptions: {
                maxBufferLength: 10,
                maxMaxBufferLength: 15,
                // Add any other Hls.js options here
            },
            hlsVersion: Hls.version,
        },
    };

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
                                    playing={isPlaying}
                                    controls={true}
                                    width="100%"
                                    height="100%"
                                    config={config}
                                    onReady={onPlayerReady}
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