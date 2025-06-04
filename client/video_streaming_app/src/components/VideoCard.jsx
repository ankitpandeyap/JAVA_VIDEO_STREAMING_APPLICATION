// src/components/VideoCard.jsx - MODIFIED WITH ENHANCED PLACEHOLDERS

import React from 'react';
import { Link } from 'react-router-dom';
import '../css/VideoCard.css';

const VideoCard = ({ video }) => {
    // Basic validation: If the video object itself or its ID/title are fundamentally missing,
    // it's truly invalid data, so return null or a basic error card.
    // We'll handle missing thumbnails/channels/dates within the card's rendering.
    if (!video || !video.id) {
        return <div className="video-card-error">Missing video ID</div>;
    }

    // Default values for potentially missing data
    const title = video.title || 'Untitled Video';
    const thumbnailUrl = video.thumbnailUrl; // Keep as is, we'll use a placeholder if null
    const channelName = video.channel || 'Unknown Channel'; // Using video.channel as per your code
    const views = video.views !== undefined ? video.views : 0; // Default to 0 if undefined
    const uploadDate = video.uploadDate ? new Date(video.uploadDate).toLocaleDateString() : 'Unknown date';
    const duration = video.duration || '00:00'; // Default duration if missing

    // Function to format views for readability (e.g., 1200000 -> 1.2M)
    const formatViews = (count) => {
        if (count >= 1000000) {
            return (count / 1000000).toFixed(1) + 'M views';
        } else if (count >= 1000) {
            return (count / 1000).toFixed(1) + 'K views';
        }
        return count + ' views';
    };

    return (
        <Link to={`/videos/${video.id}`} className="video-card-link">
            <div className="video-card">
                <div className="video-thumbnail-container">
                    {thumbnailUrl ? (
                        <img
                            src={thumbnailUrl}
                            alt={title}
                            className="video-thumbnail"
                        />
                    ) : (
                        // Placeholder for missing thumbnail
                        <div className="video-thumbnail-placeholder">
                            <span>No Thumbnail Available</span>
                        </div>
                    )}
                    {/* Display video duration if available and not '00:00' */}
                    {duration !== '00:00' && (
                        <span className="video-duration">{duration}</span>
                    )}
                </div>
                <div className="video-info">
                    <h3 className="video-title">{title}</h3>
                    <p className="video-channel">{channelName}</p>
                    <p className="video-stats">
                        {formatViews(views)} • {uploadDate}
                    </p>
                </div>
            </div>
        </Link>
    );
};

export default VideoCard;