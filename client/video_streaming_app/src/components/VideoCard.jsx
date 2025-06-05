// src/components/VideoCard.jsx (MODIFIED)

import React from 'react';
import { Link } from 'react-router-dom';
import '../css/VideoCard.css'; // Make sure this CSS file exists

// Add showActions, onEdit, and onDelete as props
const VideoCard = ({ video, showActions, onEdit, onDelete }) => {
    if (!video) {
        return <div className="video-card-placeholder">Video data missing.</div>;
    }

    return (
        <div className="video-card">
            {/* Link to the video player page */}
            <Link to={`/videos/${video.id}`} className="video-card-link">
                <div className="video-thumbnail-container">
                    {/* Placeholder for thumbnail, replace with actual video.thumbnailUrl if available */}
                    {video.thumbnailUrl ? (
                        <img src={video.thumbnailUrl} alt={video.title} className="video-thumbnail" />
                    ) : (
                        <div className="video-thumbnail-placeholder">No Thumbnail</div>
                    )}
                </div>
                <div className="video-info">
                    <h3 className="video-title">{video.title}</h3>
                    <p className="video-description">{video.description}</p>
                    <p className="video-views">{video.views} views</p>
                </div>
            </Link>

            {/* Conditionally render action buttons if showActions is true */}
            {showActions && (
                <div className="video-actions">
                    <button onClick={onEdit} className="btn-edit">Edit</button>
                    <button onClick={onDelete} className="btn-delete">Delete</button>
                </div>
            )}
        </div>
    );
};

export default VideoCard;