// src/components/Sidebar.jsx - MODIFIED FOR VIDEO STREAMING APP

import React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import "../css/Sidebar.css";

export default function Sidebar() {
    const navigate = useNavigate();
    const location = useLocation(); // Get current location

    // Handler for "Discover Videos" dashboard
    const handleDiscoverClick = () => {
        navigate("/dashboard"); // Navigates to the main video feed
    };

    // New handler for "My Videos" section
    const handleMyVideosClick = () => {
        navigate("/my-videos"); // Navigate to the user's uploaded videos page
    };

    const handleProfileClick = () => {
        navigate("/profile");
    };

    // Determine current active page for styling and disabling buttons
    const isOnDiscoverPage = location.pathname === "/dashboard";
    const isOnMyVideosPage = location.pathname === "/my-videos"; // Check if currently on /my-videos
    const isOnProfilePage = location.pathname === "/profile";


    return (
        <div className="sidebar-container">
            <nav className="sidebar-nav">
                {/* Discover/Home Button */}
                <button
                    className={`sidebar-button ${
                        isOnDiscoverPage ? "active" : ""
                    }`}
                    onClick={handleDiscoverClick}
                    disabled={isOnDiscoverPage} // Disable when on the current page
                >
                    <span className="icon">🏠</span> Discover
                </button>

                {/* My Videos Button */}
                <button
                    className={`sidebar-button ${
                        isOnMyVideosPage ? "active" : "" // Apply 'active' class if on My Videos page
                    }`}
                    onClick={handleMyVideosClick}
                    disabled={isOnMyVideosPage} // Disable when on the current page
                >
                    <span className="icon">🎬</span> My Videos
                </button>

                {/* Profile Button */}
                <button
                    className={`sidebar-button ${
                        isOnProfilePage ? "active" : ""
                    }`}
                    onClick={handleProfileClick}
                    disabled={isOnProfilePage} // Disable when on the current page
                >
                    <span className="icon">👤</span> Profile
                </button>
            </nav>
        </div>
    );
}