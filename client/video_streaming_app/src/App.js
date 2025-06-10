import React, { useContext } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";
import Register from "./pages/Register";
import ProtectedRoute from "./components/ProtectedRoute";
import { useLocation } from "react-router-dom";
import Footer from "./components/Footer";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import { AuthContext } from "./context/AuthContext";
import MyVideosPage from "./pages/MyVideosPage";
import ProfilePage from "./pages/ProfilePage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import LoadingSpinner from "./components/LoadingSpinner";
import Dashboard from "./pages/Dashboard";
import VideoPlayerPage from "./pages/VideoPlayerPage";

export default function App() {
  const location = useLocation();
  const { isAuthenticated, loadingAuth } = useContext(AuthContext);

  // Conditional redirect for the root path (/)
  const renderRootRoute = () => {
    if (loadingAuth) {
      // Render the LoadingSpinner while authentication status is being determined
      // Wrap it in a div for full-screen centering if needed, or adjust LoadingSpinner's own CSS
      return (
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            minHeight: "100vh", // Take full viewport height
            backgroundColor: "var(--bg-color-dark)", // Match app background
          }}
        >
          <LoadingSpinner message="Checking authentication..." />
        </div>
      );
    }
    // Once loadingAuth is false, navigate based on isAuthenticated
    return isAuthenticated ? (
      <Navigate to="/dashboard" />
    ) : (
      <Navigate to="/login" />
    );
  };

  return (
    <>
      <Routes>
        {/* Root path: Redirect based on authentication status */}
        <Route path="/" element={renderRootRoute()} />

        {/* Public Routes */}
        {/*
          // --- HIGHLIGHT START ---
          // Prevent authenticated users from accessing Login and Register pages.
          // If authenticated, redirect to /dashboard. Otherwise, render the component.
        */}
        <Route
          path="/register"
          element={isAuthenticated ? <Navigate to="/dashboard" /> : <Register />}
        />
        <Route
          path="/login"
          element={isAuthenticated ? <Navigate to="/dashboard" /> : <Login />}
        />
        {/* // --- HIGHLIGHT END --- */}

        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        {/* Protected Routes */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/profile"
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/my-videos" // This is the URL path for your new page
          element={
            <ProtectedRoute>
              <MyVideosPage />{" "}
              {/* This tells React to render MyVideosPage when the path matches */}
            </ProtectedRoute>
          }
        />
        <Route
          path="/videos/:videoId" // Dynamic route for video playback
          element={
            <ProtectedRoute>
              <VideoPlayerPage />
            </ProtectedRoute>
          }
        />
      </Routes>

      {/* Footer only on the login page */}
      {location.pathname === "/login" && <Footer />}
      <ToastContainer position="top-center" autoClose={1000} />
    </>
  );
}
