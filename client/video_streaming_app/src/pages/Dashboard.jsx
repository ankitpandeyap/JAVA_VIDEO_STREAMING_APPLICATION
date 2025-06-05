// src/pages/Dashboard.jsx - COMPLETE CODE WITH VIDEO FETCHING AND TOASTS
import React, { useEffect, useState } from 'react';
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import Footer from '../components/Footer';
import VideoCard from '../components/VideoCard'; // Make sure this component exists
import LoadingSpinner from '../components/LoadingSpinner'; // Make sure this component exists
import axiosInstance from '../api/axiosInstance'; // Using your configured axios instance
import { toast } from 'react-toastify'; // For user feedback
import '../css/Dashboard.css';

const Dashboard = () => {
    const [videos, setVideos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Function to fetch videos from the backend
    const fetchVideos = async () => {
        setLoading(true); // Start loading
        setError('');     // Clear previous errors
        try {
            // Assuming your backend has an endpoint like /api/videos/all
            // axiosInstance will automatically add the Authorization header and handle token refresh
            const response = await axiosInstance.get('/videos/all'); 
            setVideos(response.data); // Set the fetched videos
            toast.success('Videos loaded successfully!'); // Success notification
        } catch (err) {
            console.error('Failed to fetch videos:', err);
            // Extract a more user-friendly error message
            const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to load videos. Please try again later.';
            setError(errorMessage); // Set error state for display
            toast.error(errorMessage); // Error notification
        } finally {
            setLoading(false); // End loading
        }
    };

    // useEffect hook to call fetchVideos when the component mounts
    useEffect(() => {
        fetchVideos();
    }, []); // Empty dependency array ensures it runs only once on mount

    return (
        <div className="dashboard-layout">
            <Header />
            <div className="dashboard-main-content">
                <Sidebar />
                <div className="dashboard-content-area">
                    <h1 className="dashboard-title">Discover Videos</h1>
                    {loading ? (
                        <LoadingSpinner /> // Show loading spinner
                    ) : error ? (
                        <p className="error-message-dashboard">{error}</p> // Show error message if fetch failed
                    ) : videos.length > 0 ? (
                        // If videos are available, render them in a grid
                        <div className="video-grid">
                            {videos.map(video => (
                                // Ensure VideoCard can handle your video object structure (e.g., video.id, video.title, video.thumbnailUrl)
                                <VideoCard key={video.id} video={video} />
                            ))}
                        </div>
                    ) : (
                        // If no videos are available after loading
                        <p className="placeholder-text">No videos available yet. Be the first to upload!</p>
                    )}
                </div>
            </div>
            <Footer />
        </div>
    );
};

export default Dashboard;