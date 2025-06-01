// src/pages/Dashboard.jsx - MODIFIED FOR VIDEO DASHBOARD LAYOUT
import React from 'react';
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import Footer from '../components/Footer';
import '../css/Dashboard.css';

const Dashboard = () => {
    // In later steps, this component will fetch and display actual video data.
    // For now, we're just laying out the structure.

    return (
        <div className="dashboard-layout">
            <Header />
            <div className="dashboard-main-content">
                <Sidebar />
                <div className="dashboard-content-area">
                    <h1 className="dashboard-title">Discover Videos</h1>
                    {/* This is the placeholder where the video cards will eventually be rendered */}
                    <div className="video-grid-placeholder">
                        <p className="placeholder-text">Videos will appear here soon...</p>
                        {/* Once we create the VideoCard component (Step 2.2) and fetch data (later),
                            it will look something like this:
                        {videos.map(video => (
                            <VideoCard key={video.id} video={video} />
                        ))}
                        */}
                    </div>
                </div>
            </div>
            <Footer />
        </div>
    );
};

export default Dashboard;