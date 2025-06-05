import React, { useEffect, useState, useCallback } from 'react'; // <--- ADDED useCallback
import { useParams, useNavigate } from 'react-router-dom';
import ReactPlayer from 'react-player';
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import Footer from '../components/Footer';
import LoadingSpinner from '../components/LoadingSpinner';
import axiosInstance from '../api/axiosInstance';
import { toast } from 'react-toastify';
import '../css/VideoPlayerPage.css';

const VideoPlayerPage = () => {
    const { videoId } = useParams();
    const navigate = useNavigate();
    const [video, setVideo] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Wrap fetchVideoDetails in useCallback to memoize it
    // It depends on `videoId` and `Maps` (for the setTimeout redirect)
    const fetchVideoDetails = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const response = await axiosInstance.get(`/videos/${videoId}`);
            setVideo(response.data);
            toast.success('Video loaded successfully!');

            // After successfully fetching video details, initiate view increment
            await axiosInstance.patch(`/videos/${videoId}/views`); // Using PATCH for incrementing views
            console.log(`Views incremented for video ${videoId}`);

        } catch (err) {
            console.error('Failed to fetch video details:', err);
            const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to load video details. It might not exist or be unavailable.';
            setError(errorMessage);
            toast.error(errorMessage);

            setTimeout(() => {
                navigate('/dashboard');
            }, 3000);
        } finally {
            setLoading(false);
        }
    }, [videoId, navigate]); // <--- Dependencies for useCallback

    // Function to increment video views (fire and forget, less critical error handling)
    // Removed `incrementVideoViews` as a separate function if it's only called from `fetchVideoDetails`
    // If you need it separately, you'd wrap it in useCallback too, and decide its dependencies.
    // For now, I've embedded the logic directly into fetchVideoDetails as it seems more streamlined.
    // If you need it as a standalone, let me know.

    // Handler for ReactPlayer errors (e.g., video file not found, network issues during playback)
    const handlePlayerError = (err) => {
        console.error('ReactPlayer error:', err);
        toast.error('Video playback error. Please try again.');
    };

    // useEffect hook to fetch video details when the component mounts or videoId changes
    useEffect(() => {
        if (videoId) { // Ensure videoId is available from URL
            fetchVideoDetails(); // Call the memoized function
        }
    }, [videoId, fetchVideoDetails]); // <--- Dependency array now includes fetchVideoDetails

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
                            <p className="video-redirect-message">Redirecting to Dashboard...</p>
                        </div>
                    ) : video ? (
                        <>
                            <div className="player-wrapper">
                                <ReactPlayer
                                    url={video.videoUrl}
                                    className='react-player'
                                    playing={true}
                                    controls={true}
                                    width='100%'
                                    height='100%'
                                    onError={handlePlayerError}
                                />
                            </div>
                            <div className="video-details">
                                <h1 className="video-title">{video.title}</h1>
                                <p className="video-views">{video.views} views</p>
                                <p className="video-description">{video.description}</p>
                            </div>
                        </>
                    ) : (
                        <p className="placeholder-text">Video not found.</p>
                    )}
                </div>
            </div>
            <Footer />
        </div>
    );
};

export default VideoPlayerPage;