// src/pages/VideoPlayerPage.jsx - COMPLETE CODE FOR VIDEO PLAYBACK, DETAILS, AND ERROR HANDLING

import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom'; // Import useNavigate for redirection
import ReactPlayer from 'react-player'; // Your video player component
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import Footer from '../components/Footer';
import LoadingSpinner from '../components/LoadingSpinner'; // Assuming you have a LoadingSpinner component
import axiosInstance from '../api/axiosInstance'; // Your configured axios instance for API calls
import { toast } from 'react-toastify'; // For user feedback
import '../css/VideoPlayerPage.css'; // Your existing CSS for the video player page

const VideoPlayerPage = () => {
    const { videoId } = useParams(); // Extract videoId from the URL parameters
    const navigate = useNavigate(); // Hook to programmatically navigate users
    const [video, setVideo] = useState(null); // State to store video details
    const [loading, setLoading] = useState(true); // State to manage loading status
    const [error, setError] = useState(''); // State to store error messages

    // Function to fetch video details from the backend
    const fetchVideoDetails = async () => {
        setLoading(true); // Start loading state
        setError('');     // Clear any previous errors
        try {
            // Make an API call using axiosInstance to get video details by ID
            // Assumes backend endpoint: GET /api/videos/{videoId}
            const response = await axiosInstance.get(`/videos/${videoId}`);
            setVideo(response.data); // Update video state with fetched data
            toast.success('Video loaded successfully!'); // Show success toast

            // After successfully fetching video details, initiate view increment
            incrementVideoViews(videoId);

        } catch (err) {
            console.error('Failed to fetch video details:', err);
            // Determine a user-friendly error message
            const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to load video details. It might not exist or be unavailable.';
            setError(errorMessage); // Set error state
            toast.error(errorMessage); // Show error toast

            // Redirect to the dashboard after a short delay so the user can read the message
            setTimeout(() => {
                navigate('/dashboard');
            }, 3000); // Redirect after 3 seconds
        } finally {
            setLoading(false); // End loading state
        }
    };

    // Function to increment video views (fire and forget, less critical error handling)
    const incrementVideoViews = async (id) => {
        try {
            // Make an API call using axiosInstance to increment views
            // Assumes backend endpoint: POST /api/videos/increment-views/{videoId}
            await axiosInstance.post(`/videos/increment-views/${id}`);
            console.log(`Views incremented for video ${id}`);
        } catch (err) {
            console.warn(`Failed to increment views for video ${id}:`, err);
            // Use a warning toast for non-critical errors like view count not updating
            toast.warn('Could not update view count.');
        }
    };

    // Handler for ReactPlayer errors (e.g., video file not found, network issues during playback)
    const handlePlayerError = (err) => {
        console.error('ReactPlayer error:', err);
        toast.error('Video playback error. Please try again.');
        // You could add more sophisticated UI here, like an overlay over the player.
    };

    // useEffect hook to fetch video details when the component mounts or videoId changes
    useEffect(() => {
        if (videoId) { // Ensure videoId is available from URL
            fetchVideoDetails();
        }
    }, [videoId]); // Dependency array: re-run if videoId changes (e.g., navigating between videos)

    return (
        <div className="video-player-page-layout">
            <Header />
            <div className="video-player-main-content">
                <Sidebar />
                <div className="video-content-area">
                    {loading ? (
                        // Show loading spinner while fetching video details
                        <LoadingSpinner />
                    ) : error ? (
                        // Show error message if fetching failed, with a redirect message
                        <div className="video-error-container">
                            <p className="video-error-message">{error}</p>
                            <p className="video-redirect-message">Redirecting to Dashboard...</p>
                        </div>
                    ) : video ? (
                        // If video details are successfully loaded, display the player and details
                        <>
                            <div className="player-wrapper">
                                <ReactPlayer
                                    url={video.videoUrl} // The URL for the video stream
                                    className='react-player'
                                    playing={true} // Autoplay
                                    controls={true} // Show default player controls
                                    width='100%'
                                    height='100%'
                                    onError={handlePlayerError} // Handle playback errors
                                    // Optional: Add a light prop with video.thumbnailUrl for a custom preview image
                                    // light={video.thumbnailUrl}
                                />
                            </div>
                            <div className="video-details">
                                <h1 className="video-title">{video.title}</h1>
                                {/* Display current view count */}
                                <p className="video-views">{video.views} views</p> 
                                <p className="video-description">{video.description}</p>
                                {/* Future Enhancements: Comments section, Like/Dislike buttons, Quality selection UI */}
                            </div>
                        </>
                    ) : (
                        // Fallback if video is null and no specific error was caught (shouldn't typically happen)
                        <p className="placeholder-text">Video not found.</p>
                    )}
                </div>
            </div>
            <Footer />
        </div>
    );
};

export default VideoPlayerPage;