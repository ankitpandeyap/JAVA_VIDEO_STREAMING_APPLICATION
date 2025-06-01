// src/pages/ProfilePage.jsx (or src/components/ProfilePage.jsx)

import React, { useEffect, useState, useContext } from 'react';
import { toast } from 'react-toastify';
import axiosInstance from '../api/axiosInstance'; // Assuming this is where your axios setup is
import { AuthContext } from '../context/AuthContext'; // Assuming you have an AuthContext for accessToken
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import '../css/ProfilePage.css'; // We'll create this CSS file next
import { useNavigate } from 'react-router-dom';

export default function ProfilePage() {
    const { accessToken, loadingAuth } = useContext(AuthContext);
    const navigate = useNavigate();

    const [userProfile, setUserProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        // Redirect to login if not authenticated or auth is still loading
        if (!loadingAuth && !accessToken) {
            toast.error("You need to be logged in to view your profile.");
            navigate('/login');
            return;
        }

        const fetchUserProfile = async () => {
            try {
                setLoading(true);
                setError(null); // Clear previous errors

                const response = await axiosInstance.get('users/me');
                setUserProfile(response.data);
                toast.success("User profile loaded successfully!");
            } catch (err) {
                console.error("Failed to fetch user profile:", err);
                setError("Failed to load profile. Please try again.");
                toast.error("Failed to load profile: " + (err.response?.data?.message || err.message));

                // If it's a 401 and not handled by interceptor, or interceptor fails
                if (err.response && err.response.status === 401) {
                    // The interceptor should handle 401 and redirect,
                    // but as a fallback/redundancy, we can ensure redirection here.
                    // However, relying primarily on the interceptor is best.
                    // navigate('/login'); // Uncomment if interceptor issues are persistent
                }
            } finally {
                setLoading(false);
            }
        };

        if (accessToken) { // Only fetch if accessToken is available
            fetchUserProfile();
        }
    }, [accessToken, loadingAuth, navigate]); // Depend on accessToken and loadingAuth

    if (loading) {
        return (
            <>
                <Header />
                <div className="main-layout">
                    <Sidebar />
                    <div className="profile-content-area loading-profile">
                        <p>Loading profile...</p>
                    </div>
                </div>
            </>
        );
    }

    if (error) {
        return (
            <>
                <Header />
                <div className="main-layout">
                    <Sidebar />
                    <div className="profile-content-area error-profile">
                        <p className="error-message">{error}</p>
                    </div>
                </div>
            </>
        );
    }

    if (!userProfile) {
        return (
            <>
                <Header />
                <div className="main-layout">
                    <Sidebar />
                    <div className="profile-content-area no-profile-data">
                        <p>No user profile data found.</p>
                    </div>
                </div>
            </>
        );
    }

    // Function to format LocalDateTime
    const formatDateTime = (isoString) => {
        if (!isoString) return 'N/A';
        try {
            const date = new Date(isoString);
            return date.toLocaleString('en-US', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: true
            });
        } catch (e) {
            console.error("Error formatting date:", e);
            return isoString; // Return original if formatting fails
        }
    };

    return (
        <>
            <Header />
            <div className="main-layout">
                <Sidebar />
                <div className="profile-content-area">
                  
                    <div className="profile-details">
                        <div className="profile-item">
                            <span className="profile-label">Username:</span>
                            <span className="profile-value">{userProfile.username}</span>
                        </div>
                        <div className="profile-item">
                            <span className="profile-label">Email:</span>
                            <span className="profile-value">{userProfile.email}</span>
                        </div>
                        <div className="profile-item">
                            <span className="profile-label">Member Since:</span>
                            <span className="profile-value">{formatDateTime(userProfile.createdAt)}</span>
                        </div>
                        {/* Add more profile fields here as needed from UserProfileDTO */}
                    </div>
                    {/* Optional: Add a button to edit profile or change password */}
                    {/* <button className="edit-profile-btn">Edit Profile</button> */}
                </div>
            </div>
        </>
    );
}