// src/pages/VideoPlayerPage.jsx - MODIFIED FOR REACT-PLAYER

import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';
import ReactPlayer from 'react-player/lazy'; // Import ReactPlayer (using lazy load for smaller bundle)
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import Footer from '../components/Footer';
import LoadingSpinner from '../components/LoadingSpinner';
import '../css/VideoPlayerPage.css';

// Dummy API URL for fetching a single video's details.
const VIDEO_DETAIL_API_URL = 'http://localhost:5000/api/videos'; // **IMPORTANT: Replace with your actual backend URL**
// Dummy API URL for streaming the video. This will likely be different from detail API.
// For HLS, this would point to the .m3u8 master playlist.
// For direct MP4, this would be the direct video file URL.
const VIDEO_STREAM_BASE_URL = 'http://localhost:5000/api/stream/video'; // **IMPORTANT: Replace with your actual stream base URL**


const VideoPlayerPage = () => {
    const { videoId } = useParams();
    const playerRef = useRef(null); // Reference to the ReactPlayer instance

    const [video, setVideo] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [playbackError, setPlaybackError] = useState('');
    const [buffering, setBuffering] = useState(false); // State for buffering indicator

    // We'll remove the hls.js specific logic as ReactPlayer handles it internally
    // and instead focus on getting the correct URL to pass to ReactPlayer

    useEffect(() => {
        const fetchVideoDetails = async () => {
            try {
                setLoading(true);
                setError('');
                setPlaybackError(''); // Clear playback errors on new fetch

                const response = await axios.get(`${VIDEO_DETAIL_API_URL}/${videoId}`);

                const fetchedVideo = response.data.video || response.data;

                if (fetchedVideo && fetchedVideo.status === 'ready') {
                    setVideo(fetchedVideo);
                    // --- IMPORTANT: Determine the actual stream URL from fetchedVideo data ---
                    // This is where your backend data will dictate what URL to play.
                    // ReactPlayer can often infer the type (MP4 vs HLS) from the URL extension.

                    let streamUrl = '';

                    // Placeholder logic: Adapt this based on your backend's response structure
                    // Your backend should ideally provide a direct playable URL (MP4, HLS .m3u8)
                    if (fetchedVideo.hlsMasterPlaylistUrl) {
                        streamUrl = fetchedVideo.hlsMasterPlaylistUrl;
                    } else if (fetchedVideo.directMp4Url) {
                        streamUrl = fetchedVideo.directMp4Url;
                    } else {
                        // Fallback/example for initial setup if backend provides just ID and fileSize
                        // This logic needs to align with your actual backend's streaming endpoint.
                        if (fetchedVideo.fileSize && fetchedVideo.fileSize > 50 * 1024 * 1024) { // >50MB
                           streamUrl = `${VIDEO_STREAM_BASE_URL}/${videoId}/master.m3u8`; // Assuming this path provides HLS
                        } else if (fetchedVideo.fileSize) { // <50MB (direct stream for now, backend handles byte range)
                           streamUrl = `${VIDEO_STREAM_BASE_URL}/${videoId}/default.mp4`; // Assuming this path provides direct MP4
                        } else {
                            // If no file size or specific URLs, provide a generic placeholder
                            streamUrl = `${VIDEO_STREAM_BASE_URL}/${videoId}/stream`; // Generic stream URL
                        }
                    }

                    if (streamUrl) {
                        setVideo(prevVideo => ({ ...prevVideo, streamUrl })); // Add streamUrl to video state
                    } else {
                        setPlaybackError('No valid stream URL provided by backend.');
                    }

                } else if (fetchedVideo && fetchedVideo.status !== 'ready') {
                    setError('Video is still processing or not ready for streaming.');
                    setVideo(null);
                } else {
                    setError('Video not found or invalid data.');
                    setVideo(null);
                }
            } catch (err) {
                console.error('Error fetching video details:', err);
                if (err.response && err.response.status === 404) {
                    setError('Video not found.');
                } else if (err.response) {
                    setError(err.response.data.message || 'Server error fetching video details.');
                } else if (err.request) {
                    setError('No response from server. Check your network connection.');
                } else {
                    setError('An unexpected error occurred.');
                }
                setVideo(null);
            } finally {
                setLoading(false);
            }
        };

        if (videoId) {
            fetchVideoDetails();
        } else {
            setLoading(false);
            setError('No video ID provided.');
        }

        // No cleanup needed for hls.js instance as ReactPlayer manages it
    }, [videoId]); // Re-fetch if videoId changes

    const handlePlayerError = (e) => {
        console.error('ReactPlayer error:', e);
        // ReactPlayer's onError might give different types of errors (event, code, etc.)
        // You might need to inspect 'e' to get a more specific message.
        setPlaybackError('Could not play video. This might be due to an unsupported format or network issue.');
        setBuffering(false); // Stop buffering on error
    };

    return (
        <div className="player-layout">
            <Header />
            <div className="player-main-content">
                <Sidebar />
                <div className="player-content-area">
                    {loading ? (
                        <LoadingSpinner message="Loading video..." />
                    ) : error ? (
                        <div className="video-player-error">
                            <p>{error}</p>
                            <button onClick={() => window.history.back()} className="back-button">Go Back</button>
                        </div>
                    ) : video && video.streamUrl ? ( // Ensure streamUrl is present
                        <div className="video-player-container">
                            <div className="video-player-wrapper">
                                <ReactPlayer
                                    ref={playerRef}
                                    className="react-player" // For styling the player itself
                                    url={video.streamUrl} // The URL to stream from your backend
                                    playing={true} // Autoplay
                                    controls={true} // Use ReactPlayer's built-in controls for now
                                    width="100%"
                                    height="100%"
                                    onBuffer={() => setBuffering(true)} // When buffering starts
                                    onBufferEnd={() => setBuffering(false)} // When buffering ends
                                    onReady={() => {
                                        setBuffering(false); // Ensure buffering is off when ready
                                        setPlaybackError(''); // Clear any errors on ready
                                    }}
                                    onError={handlePlayerError} // Handle player errors
                                    config={{
                                        file: {
                                            attributes: {
                                                preload: 'auto', // Preload video metadata
                                            },
                                            // You can explicitly tell ReactPlayer to use HLS.js
                                            // for .m3u8 files if not autodetected, though it usually is.
                                            hlsOptions: {
                                                // autoStartLoad: true,
                                                // debug: false,
                                                // etc.
                                            }
                                        }
                                    }}
                                />

                                {buffering && (
                                    <div className="video-overlay loading-overlay">
                                        <LoadingSpinner small={true} />
                                        <p>Buffering...</p>
                                    </div>
                                )}
                                {playbackError && (
                                    <div className="video-overlay playback-error-overlay">
                                        <p className="playback-error-message">Playback Error: {playbackError}</p>
                                        <button onClick={() => playerRef.current && playerRef.current.load()} className="retry-playback-button">Retry</button>
                                        {/* You might want to reload the entire page or re-fetch details */}
                                    </div>
                                )}
                                {/* Future: Custom controls overlay goes here if not using built-in controls */}
                            </div>

                            <div className="video-details">
                                <h1 className="video-player-title">{video.title}</h1>
                                <p className="video-player-channel">By: {video.channelName || 'Unknown Channel'}</p>
                                <p className="video-player-description">{video.description}</p>
                                {video.views !== undefined && <p className="video-player-views">{video.views} views</p>}
                                {video.uploadDate && <p className="video-player-date">Uploaded on: {new Date(video.uploadDate).toLocaleDateString()}</p>}
                            </div>

                            <div className="comments-section-placeholder">
                                <h3>Comments (Coming Soon)</h3>
                            </div>
                        </div>
                    ) : ( // Case where video is null or streamUrl is missing
                        <p className="no-video-found-message">{!loading && !error ? 'No video stream available.' : ''}</p>
                    )}
                </div>
            </div>
            <Footer />
        </div>
    );
};

export default VideoPlayerPage;