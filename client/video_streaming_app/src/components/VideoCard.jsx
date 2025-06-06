// src/components/VideoCard.jsx

import React from 'react';
import { Link } from 'react-router-dom';
import '../css/VideoCard.css';

const VideoCard = ({ video, showActions, onEdit, onDelete }) => {
    // Defensive check: Ensure video object and video.videoId are valid before rendering a link
    // If videoId is missing, we render a non-clickable placeholder or nothing.
    if (!video || typeof video.videoId === 'undefined' || video.videoId === null) {
        console.warn("VideoCard received invalid video data, cannot create link. Video:", video);
        // Render a basic div or null if video data is fundamentally invalid
        return (
            <div className="video-card video-card-invalid">
                <div className="video-placeholder-box">
                    Invalid Video Data
                </div>
                <div className="video-info">
                    <h3 className="video-title">Error Loading Video</h3>
                    <p className="video-channel">Please check data</p>
                </div>
            </div>
        );
    }

    const videoLink = `/videos/${video.videoId}`;

    return (
        <div className="video-card">
            {/* Link to the video player page - using video.videoId */}
            {/* The Link component now only renders if video.videoId is valid */}
            <Link to={videoLink} className="video-card-link">
                <div className="video-thumbnail-container">
                    {/* Placeholder content for the thumbnail area, since no actual thumbnail is used */}
                    <div className="video-placeholder-box">
                        Video Placeholder
                    </div>
                </div>
                <div className="video-info">
                    {/* TITLE: Use video.videoName from your backend DTO */}
                    <h3 className="video-title">{video.videoName || 'Untitled Video'}</h3>
                    {/* UPLOADER: Use video.uploadUsername from your backend DTO */}
                    <p className="video-channel">{video.uploadUsername || 'Unknown User'}</p>
                    {/* DESCRIPTION: Use video.description from your backend DTO */}
                    <p className="video-description">{video.description}</p>
                    {/* VIEWS: Use video.views from your backend DTO */}
                    <p className="video-views">{video.views} views</p>
                </div>
            </Link>

            {/* Conditionally render action buttons if showActions is true */}
            {showActions && (
                <div className="video-actions">
                    {/* Pass video.videoId to onEdit and onDelete handlers */}
                   <button onClick={() => onEdit(video)} className="btn-edit">Edit</button>
                    <button onClick={() => onDelete(video)} className="btn-delete">Delete</button>
                </div>
            )}
        </div>
    );
};

export default VideoCard;