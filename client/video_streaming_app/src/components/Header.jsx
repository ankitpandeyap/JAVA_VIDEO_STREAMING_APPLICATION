import React, { useState, useContext } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom'; // Import useLocation
import '../css/Header.css';
import UploadVideoModal from './UploadVideoModal';
import { AuthContext } from '../context/AuthContext';

const Header = () => {
    const [showUploadModal, setShowUploadModal] = useState(false);
    const { isAuthenticated, logout } = useContext(AuthContext);
    const navigate = useNavigate();
    const location = useLocation(); // Get the current location

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

    // Function to determine the page title based on the current path
    const getPageTitle = () => {
        switch (location.pathname) {
            case '/dashboard':
                return 'Discover Videos';
            case '/my-videos':
                return 'My Uploaded Videos';
            case '/profile':
                return 'Your Profile';
            // You can add more cases for other routes as needed
            default:
                return 'MyTube'; // Default title for unhandled routes or homepage
        }
    };
  const pageTitle = getPageTitle(); // Get the title once

    return (
        <header className="header-bar">
            <div className="header-logo-container"> {/* New container for just the logo */}
                <Link to="/">StreamFlow</Link>
            </div>

            {/* Render the dynamic page title in its own centered div */}
            <div className="header-page-title-container">
                {pageTitle && <h1 className="page-header-title">{pageTitle}</h1>}
            </div>

            <nav className="header-nav">
                {isAuthenticated ? (
                    <>
                        <button className="upload-video-btn" onClick={handleOpenUploadModal}>
                            Upload Video
                        </button>
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
