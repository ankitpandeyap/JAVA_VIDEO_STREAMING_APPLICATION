// src/pages/ProfilePage.jsx

import React, { useEffect, useState, useContext } from "react";
import { toast } from "react-toastify";
import axiosInstance from "../api/axiosInstance";
import { AuthContext } from "../context/AuthContext";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import LoadingSpinner from "../components/LoadingSpinner"; // Assuming you have a reusable spinner
import "../css/ProfilePage.css"; // We'll create this CSS file
import { useNavigate } from "react-router-dom";

export default function ProfilePage() {
  const { isAuthenticated, loadingAuth } = useContext(AuthContext); // Use isAuthenticated for clarity
  const navigate = useNavigate();

  const [userProfile, setUserProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    // Only proceed if authentication status is known and user is authenticated
    if (!loadingAuth) {
      if (!isAuthenticated) {
        toast.error("You need to be logged in to view your profile.");
        navigate("/login");
        return;
      }

      const fetchUserProfile = async () => {
        setLoading(true);
        setError(null); // Clear previous errors
        try {
          // Make sure the path matches your backend: /api/users/me
          const response = await axiosInstance.get("/users/me");
          setUserProfile(response.data);
          toast.success("User profile loaded successfully!");
        } catch (err) {
          console.error("Failed to fetch user profile:", err);
          setError("Failed to load profile. Please try again.");

          // Improved error message extraction
          const errorMessage =
            err.response?.data?.message ||
            err.response?.data?.error ||
            "An unexpected error occurred.";
          toast.error(`Failed to load profile: ${errorMessage}`);

          // The axios interceptor in axiosInstance should handle 401 redirection,
          // so explicit navigate('/login') here might be redundant but can serve as a fallback.
          if (err.response && err.response.status === 401) {
            navigate("/login");
          }
        } finally {
          setLoading(false);
        }
      };

      fetchUserProfile();
    }
  }, [isAuthenticated, loadingAuth, navigate]); // Dependencies for useEffect

  // Function to format LocalDateTime from backend (e.g., "2024-06-05T10:30:00")
  const formatDateTime = (isoString) => {
    if (!isoString) return "N/A";
    try {
      const date = new Date(isoString);
      return date.toLocaleString("en-US", {
        year: "numeric",
        month: "long",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: true,
      });
    } catch (e) {
      console.error("Error formatting date:", e);
      return isoString; // Return original if formatting fails
    }
  };

  // Conditional rendering based on loading, error, and data presence
  if (loading) {
    return (
      <>
        <Header />
        <div className="main-layout">
          <Sidebar />
          <div className="profile-content-area loading-profile">
            <LoadingSpinner message="Loading profile..." />
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

  // This case should ideally be caught by loading or error,
  // but as a final safeguard if userProfile is unexpectedly null
  if (!userProfile) {
    return (
      <>
        <Header />
        <div className="main-layout">
          <Sidebar />
          <div className="profile-content-area no-profile-data">
            <p>No user profile data found. Please try logging in again.</p>
            {/* Optionally add a retry button or direct to login */}
            <button onClick={() => navigate("/login")} className="btn-primary">
              Go to Login
            </button>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <Header />
      <div className="main-layout">
        <Sidebar />
        <div className="profile-content-area">
          <div className="profile-container">
            
            <div className="profile-details">
              <div className="profile-item">
                <span className="profile-label">Username:</span>
                <span className="profile-value">{userProfile.username}</span>
              </div>
              {userProfile.name && ( // Only render if userProfile.name has a value
                <div className="profile-item">
                  <span className="profile-label">Full Name:</span>
                  <span className="profile-value">{userProfile.name}</span>
                </div>
              )}
              <div className="profile-item">
                <span className="profile-label">Email:</span>
                <span className="profile-value">{userProfile.email}</span>
              </div>
              {/* Removed Role field as requested */}
              <div className="profile-item">
                <span className="profile-label">Member Since:</span>
                <span className="profile-value">
                  {formatDateTime(userProfile.createdAt)}
                </span>
              </div>
            </div>
            {/* Optional: Add buttons for actions like "Edit Profile" or "Change Password" */}
            <div className="profile-actions">
              {/* <button className="btn-secondary">Edit Profile</button> */}
              {/* <button className="btn-secondary">Change Password</button> */}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
