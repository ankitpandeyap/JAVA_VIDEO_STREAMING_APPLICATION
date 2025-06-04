// src/components/Header.jsx - MODIFIED TO INCLUDE UPLOAD BUTTON AND MODAL

import React, { useState } from 'react';
import { Link } from 'react-router-dom'; // Assuming you use React Router
import '../css/Header.css'; // Your existing Header CSS
import UploadVideoModal from './UploadVideoModal'; // Import the new UploadVideoModal

const Header = () => {
    const [showUploadModal, setShowUploadModal] = useState(false);

    const handleOpenUploadModal = () => {
        setShowUploadModal(true);
    };

    const handleCloseUploadModal = () => {
        setShowUploadModal(false);
        // You might want to refresh video list on dashboard after successful upload
        // (This would be handled in Dashboard.jsx's useEffect, triggered by a state change
        // or a global context update, or by passing a callback to onUploadSuccess if needed)
    };

    // Dummy authentication check. Replace with actual auth context/logic.
    const isAuthenticated = true; // For demonstration, assume user is logged in

    return (
        <header className="header-bar">
            <div className="header-logo">
                <Link to="/">MyTube</Link> {/* Link to your home/dashboard */}
            </div>

            {/* Optional: Central title if needed, otherwise remove */}
            {/* <div className="header-title">Discover</div> */}

            <nav className="header-nav">
                {isAuthenticated ? (
                    <>
                        <button className="upload-video-btn" onClick={handleOpenUploadModal}>
                            Upload Video
                        </button>
                        <Link to="/profile" className="header-link">Profile</Link>
                        {/* Add actual logout logic here */}
                        <button className="header-logout-btn" onClick={() => console.log('Logout clicked')}>Logout</button>
                    </>
                ) : (
                    <>
                        <Link to="/login" className="header-link">Login</Link>
                        <Link to="/register" className="header-link">Register</Link>
                    </>
                )}
            </nav>

            {showUploadModal && <UploadVideoModal onClose={handleCloseUploadModal} />}
        </header>
    );
};

export default Header;