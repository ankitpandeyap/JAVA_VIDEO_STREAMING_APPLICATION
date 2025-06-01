// src/components/VideoCard.jsx

import React from 'react';
import { Link } from 'react-router-dom'; // Assuming you are using React Router for navigation
import '../css/VideoCard.css'; // Styling for the video card

const VideoCard = ({ video }) => {
    // Basic validation for video object
    if (!video || !video.id || !video.title || !video.thumbnailUrl) {
        // You might want a more sophisticated placeholder or error handling
        return <div className="video-card-error">Invalid video data</div>;
    }

    // Function to format views for readability (e.g., 1200000 -> 1.2M)
    const formatViews = (views) => {
        if (views >= 1000000) {
            return (views / 1000000).toFixed(1) + 'M views';
        } else if (views >= 1000) {
            return (views / 1000).toFixed(1) + 'K views';
        }
        return views + ' views';
    };

    return (
        <Link to={`/videos/${video.id}`} className="video-card-link">
            <div className="video-card">
                <div className="video-thumbnail-container">
                    <img
                        src={video.thumbnailUrl}
                        alt={video.title}
                        className="video-thumbnail"
                    />
                    {/* Optional: Display video duration if available */}
                    {video.duration && (
                        <span className="video-duration">{video.duration}</span>
                    )}
                </div>
                <div className="video-info">
                    <h3 className="video-title">{video.title}</h3>
                    {video.channel && (
                        <p className="video-channel">{video.channel}</p>
                    )}
                    {video.views !== undefined && (
                        <p className="video-views">{formatViews(video.views)}</p>
                    )}
                </div>
            </div>
        </Link>
    );
};

export default VideoCard;