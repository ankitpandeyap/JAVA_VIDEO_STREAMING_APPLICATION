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
    const hlsRef = useRef(null); // Ref to hold the Hls.js instance

    // Refs for HLS token management (these do NOT trigger re-renders)
    const currentHlsTokenRef = useRef(null);
    const hlsTokenExpiryRef = useRef(null); // Unix timestamp in seconds
    const refreshTimeoutId = useRef(null); // To clear the auto-refresh timeout

    const [qualityLevels, setQualityLevels] = useState([]);
    const [currentQuality, setCurrentQuality] = useState("auto");
    const [isPlaying, setIsPlaying] = useState(false); // Start paused

    // Helper to extract token and expiry from a full HLS URL
    const extractTokenAndExpiry = useCallback((fullAbsoluteUrl) => {
        try {
            const url = new URL(fullAbsoluteUrl);
            const token = url.searchParams.get("token");
            if (token) {
                const decoded = jwtDecode(token);
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

    // --- Using useRef to hold mutable references to our useCallback functions ---
    // This allows us to break the circular dependency in the useCallback dependency arrays
    const fetchVideoDataRef = useRef();
    const scheduleTokenRefreshRef = useRef();
    const refreshHlsTokenRef = useRef();

    // Define the useCallback functions
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
                    setHlsPlaybackUrl(fullAbsoluteUrl);
                    console.log("Initial HLS Playback URL set:", fullAbsoluteUrl);
                    // Call through ref to avoid circular dependency in useCallback deps
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
    }, [videoId, navigate, setLoading, setError, setVideo, extractTokenAndExpiry]); 


    const refreshHlsToken = useCallback(async () => {
        console.log("Attempting to refresh HLS token...");
        try {
            const urlResponse = await axiosInstance.get(`/videos/${videoId}/hls-stream-url`);
            const relativeUrlFromBackend = urlResponse.data;

            if (relativeUrlFromBackend) {
                const fullAbsoluteUrl = `http://localhost:8082${relativeUrlFromBackend}`;
                const { token, expiryTimeMs } = extractTokenAndExpiry(fullAbsoluteUrl);

                if (token && expiryTimeMs) {
                    currentHlsTokenRef.current = token;
                    hlsTokenExpiryRef.current = expiryTimeMs;

                    if (hlsRef.current) {
                        hlsRef.current.config.xhrSetup = (xhr, url) => {
                            const urlObj = new URL(url);
                            urlObj.searchParams.set("token", token);
                            xhr.open("GET", urlObj.toString());
                        };
                        console.log("hls.js xhrSetup updated with new token.");
                        hlsRef.current.startLoad(); 
                        toast.info("HLS stream token refreshed. Resuming playback.");
                    } else {
                        console.warn("Hls.js instance not available, cannot update xhrSetup. Re-fetching video data.");
                        // Call through ref to avoid circular dependency in useCallback deps
                        fetchVideoDataRef.current(); 
                    }
                    // Call through ref to avoid circular dependency in useCallback deps
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
    }, [videoId, extractTokenAndExpiry]); // Dependencies are now just values this function *directly* uses


    const scheduleTokenRefresh = useCallback(() => {
        if (refreshTimeoutId.current) {
            clearTimeout(refreshTimeoutId.current);
            refreshTimeoutId.current = null;
        }

        const expiry = hlsTokenExpiryRef.current;
        if (expiry) {
            const now = Date.now();
            const refreshTime = Math.max(0, expiry - now - (30 * 1000)); 
            
            console.log(`HLS token will refresh in ${Math.round(refreshTime / 1000)} seconds.`);
            refreshTimeoutId.current = setTimeout(() => {
                console.log("Proactively refreshing HLS token...");
                // Call through ref to avoid circular dependency in useCallback deps
                refreshHlsTokenRef.current();
            }, refreshTime);
        }
    }, []); // Dependencies are now just values this function *directly* uses


    // --- useEffect to update the refs with the latest useCallback instances ---
    // This runs after every render, ensuring the refs always point to the fresh functions.
    useEffect(() => {
        fetchVideoDataRef.current = fetchVideoData;
        scheduleTokenRefreshRef.current = scheduleTokenRefresh;
        refreshHlsTokenRef.current = refreshHlsToken;
    }, [fetchVideoData, scheduleTokenRefresh, refreshHlsToken]);


    useEffect(() => {
        fetchVideoDataRef.current(); // Call the function via its ref

        return () => {
            if (hlsRef.current) {
                console.log("Hls.js instance being destroyed on unmount.");
                // SOLUTION: Explicitly detach media before destroying
                if (hlsRef.current.media) {
                    hlsRef.current.detachMedia();
                }
                hlsRef.current.destroy();
                hlsRef.current = null;
            }
            if (refreshTimeoutId.current) {
                clearTimeout(refreshTimeoutId.current);
                refreshTimeoutId.current = null;
            }
        };
    }, []); // Empty dependency array because fetchVideoData is called via its ref


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
    const handleReady = useCallback((player) => {
        if (!hlsPlaybackUrl) {
            console.warn("handleReady called, but hlsPlaybackUrl is not set yet.");
            return;
        }

        // It's good practice to destroy an old instance if it exists before creating a new one
        if (hlsRef.current) {
            console.log("Destroying old Hls.js instance before new one.");
            if (hlsRef.current.media) { // Ensure media is detached before destruction
                hlsRef.current.detachMedia();
            }
            hlsRef.current.destroy();
            hlsRef.current = null;
        }

        const mediaElement = player.getInternalPlayer(); // Get the underlying video element

        if (Hls.isSupported()) {
            console.log("Hls.js is supported. Initializing...");
            const hls = new Hls({});

            const { token: initialToken } = extractTokenAndExpiry(hlsPlaybackUrl);
            if (initialToken) {
                hls.config.xhrSetup = (xhr, url) => {
                    const urlObj = new URL(url);
                    urlObj.searchParams.set("token", initialToken);
                    xhr.open("GET", urlObj.toString());
                };
                console.log("Initial Hls.js xhrSetup configured with token.");
            } else {
                console.warn("No initial token found in hlsPlaybackUrl. Hls.js might fail.");
            }

            hls.loadSource(hlsPlaybackUrl);
            hls.attachMedia(mediaElement); // Attach to the actual video element
            hlsRef.current = hls; // Store the instance in ref

            hls.on(Hls.Events.MANIFEST_PARSED, () => {
                console.log("Hls.js: MANIFEST_PARSED event fired!");
                updateQualityLevels();
            });

            hls.on(Hls.Events.LEVEL_SWITCHED, (eventName, data) => {
                console.log("Hls.js: LEVEL_SWITCHED event fired!", data);
                updateQualityLevels();
            });

            hls.on(Hls.Events.ERROR, (eventName, data) => {
                console.error("Hls.js Error:", eventName, data);
                if (data.fatal) {
                    switch (data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            if (data.response && (data.response.code === 401 || data.response.code === 403)) {
                                console.warn("HLS token expired or unauthorized. Attempting refresh...");
                                // Call through ref
                                refreshHlsTokenRef.current(); 
                            } else {
                                toast.error("Network error during HLS playback. Please check your connection.");
                                hls.recoverMediaError(); 
                            }
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            toast.error("Media error during HLS playback. Trying to recover...");
                            hls.recoverMediaError();
                            break;
                        default:
                            toast.error("A fatal HLS playback error occurred. Please try again.");
                            if (hlsRef.current) {
                                // Ensure media is detached before destroying on fatal error
                                if (hlsRef.current.media) {
                                    hlsRef.current.detachMedia();
                                }
                                hlsRef.current.destroy();
                                hlsRef.current = null;
                                setQualityLevels([]);
                                setCurrentQuality("auto");
                            }
                            break;
                    }
                }
            });

        } else if (mediaElement && mediaElement.canPlayType('application/vnd.apple.mpegurl')) { // Check mediaElement before canPlayType
            console.log("Using native HLS playback (Hls.js not supported or not needed).");
            mediaElement.src = hlsPlaybackUrl;
            if (hlsRef.current) { 
                if (hlsRef.current.media) { // Ensure media is detached if an HLS instance existed
                    hlsRef.current.detachMedia();
                }
                hlsRef.current.destroy();
                hlsRef.current = null;
            }
            setQualityLevels([]); 
            setCurrentQuality("auto");
            toast.info("Using native HLS playback. Quality control may vary by browser.");
        } else {
            toast.error("Your browser does not support HLS playback.");
            setError("HLS playback not supported on this browser.");
        }
    }, [hlsPlaybackUrl, updateQualityLevels, extractTokenAndExpiry]); // refreshHlsToken is called via ref here


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
            forceHLS: true, 
            hlsOptions: {
                maxBufferLength: 30, 
                maxMaxBufferLength: 60, 
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