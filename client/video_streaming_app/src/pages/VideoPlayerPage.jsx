// src/pages/VideoPlayerPage.jsx

import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom'; // To get the video ID from the URL
import axios from 'axios'; // For fetching video details
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import Footer from '../components/Footer';
import LoadingSpinner from '../components/LoadingSpinner'; // Assuming you have one
import '../css/VideoPlayerPage.css'; // Styling for the video player page

// Dummy API URL for fetching a single video's details.
// **IMPORTANT: Replace with your actual backend URL**
const VIDEO_DETAIL_API_URL = 'http://localhost:5000/api/videos'; // Will be /api/videos/:id

const VideoPlayerPage = () => {
    const { videoId } = useParams(); // Get the video ID from the URL (e.g., /videos/123 -> videoId = "123")
    const [video, setVideo] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const fetchVideoDetails = async () => {
            try {
                setLoading(true);
                setError('');
                // This URL will need to be adapted to your backend's specific endpoint for single videos
                // e.g., 'http://localhost:5000/api/videos/123'
                const response = await axios.get(`${VIDEO_DETAIL_API_URL}/${videoId}`);

                if (response.status === 200) {
                    // Assuming your backend returns the video object directly or within a 'video' key
                    const fetchedVideo = response.data.video || response.data;

                    // IMPORTANT: Your backend should provide the stream URLs (e.g., direct MP4, or HLS .m3u8)
                    // and resolution details here.
                    // For now, we're just checking for a 'ready' status as per your notes.
                    if (fetchedVideo && fetchedVideo.status === 'ready') {
                        setVideo(fetchedVideo);
                    } else if (fetchedVideo && fetchedVideo.status !== 'ready') {
                        setError('Video is still processing or not ready for streaming.');
                        setVideo(null); // Clear video if not ready
                    } else {
                        setError('Video not found or invalid data.');
                        setVideo(null);
                    }
                } else {
                    setError('Failed to fetch video details.');
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
    }, [videoId]); // Re-fetch if videoId changes

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
                    ) : video ? (
                        <div className="video-player-container">
                            {/* This is where the actual video player will go (Step 3.2) */}
                            <div className="video-player-placeholder">
                                {/* For now, a placeholder */}
                                <p>Video player will be here for: **{video.title}**</p>
                                <p>Backend Status: {video.status}</p>
                                {/* In Step 3.2, we'll replace this with a <video> tag or a player library */}
                            </div>

                            <div className="video-details">
                                <h1 className="video-player-title">{video.title}</h1>
                                <p className="video-player-channel">By: {video.channelName || 'Unknown Channel'}</p>
                                <p className="video-player-description">{video.description}</p>
                                {/* Add more details like views, upload date, etc. */}
                                {video.views !== undefined && <p className="video-player-views">{video.views} views</p>}
                                {video.uploadDate && <p className="video-player-date">Uploaded on: {new Date(video.uploadDate).toLocaleDateString()}</p>}
                            </div>

                            {/* Placeholder for comments/related videos (future steps) */}
                            <div className="comments-section-placeholder">
                                <h3>Comments (Coming Soon)</h3>
                            </div>
                        </div>
                    ) : (
                        <p className="no-video-found-message">Please select a video to play.</p>
                    )}
                </div>
            </div>
            <Footer />
        </div>
    );
};

export default VideoPlayerPage;