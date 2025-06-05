import React, { useState, useContext } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import '../css/Header.css';
import UploadVideoModal from './UploadVideoModal';
import { AuthContext } from '../context/AuthContext';

const Header = () => {
    const [showUploadModal, setShowUploadModal] = useState(false);
    const { isAuthenticated, logout } = useContext(AuthContext);
    const navigate = useNavigate();

    const handleOpenUploadModal = () => {
        setShowUploadModal(true);
    };

    const handleCloseUploadModal = () => {
        setShowUploadModal(false);
    };

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <header className="header-bar">
            <div className="header-logo">
                <Link to="/">MyTube</Link>
            </div>

            <nav className="header-nav">
                {isAuthenticated ? (
                    <>
                        <button className="upload-video-btn" onClick={handleOpenUploadModal}>
                            Upload Video
                        </button>
                        {/* REMOVED: <Link to="/profile" className="header-link">Profile</Link> */}
                        {/* REMOVED: <Link to="/my-videos" className="header-link">My Videos</Link> */}
                        <button className="header-logout-btn" onClick={handleLogout}>Logout</button>
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