import React, { useState, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom"; // Import useLocation to get URL params
import axiosInstance from "../api/axiosInstance"; // Your configured Axios instance
import { toast } from "react-toastify";
import LoadingSpinner from "../components/LoadingSpinner";
import Header from "../components/Header";
import "../css/Login.css"; // Reuse existing CSS

export default function ResetPasswordPage() {
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [token, setToken] = useState(null); // State to store the token from URL
  const [isValidToken, setIsValidToken] = useState(false); // To indicate if token is present/valid
  const location = useLocation(); // Hook to access URL's query parameters
  const navigate = useNavigate();

  useEffect(() => {
    // 1. Extract token from URL on component mount
    const queryParams = new URLSearchParams(location.search);
    const urlToken = queryParams.get("token");

    if (urlToken) {
      setToken(urlToken);
      setIsValidToken(true); // Assume valid for now, backend will truly validate
      console.log("Token found in URL:", urlToken); // For debugging
    } else {
      setIsValidToken(false);
      toast.error("No reset token found in the URL. Please use the link from your email.");
      // Optionally redirect after a short delay
      // setTimeout(() => navigate("/forgot-password"), 3000);
    }
  }, [location.search]); // Re-run if URL query params change

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!token) {
      toast.error("Reset token is missing.");
      return;
    }

    if (newPassword !== confirmPassword) {
      toast.error("New password and confirm password do not match.");
      return;
    }

    // Basic password strength check (you might want a more robust one)
    if (newPassword.length < 8) {
      toast.error("New password must be at least 8 characters long.");
      return;
    }

    setLoading(true);
    try {
      // Send the token and new password to your backend
      const response = await axiosInstance.post("/auth/reset-password", {
        token,
        newPassword,
      });

      toast.success(response.data || "Your password has been successfully reset!");
      setNewPassword("");
      setConfirmPassword("");
      setIsValidToken(false); // Invalidate client-side token status
      navigate("/login"); // Redirect to login page after successful reset
    } catch (error) {
      console.error("Password reset failed:", error); // Log the error for debugging

      if (error.response && error.response.data) {
        if (typeof error.response.data === 'string') {
          toast.error(error.response.data);
        } else if (error.response.data.error) {
          toast.error(error.response.data.error);
        } else if (error.response.data.message) {
          toast.error(error.response.data.message);
        } else {
          toast.error("Failed to reset password. Please try again.");
        }
      } else {
        toast.error("Network error or server is unreachable. Please try again later.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Header />
      <div className="login-page-container">
        <div className="login-container">
          <form onSubmit={handleSubmit} className="login-form">
            <h2 className="login-title">Reset Password</h2>

            {!isValidToken ? (
              <p className="text-center text-red-500">
                Invalid or missing reset link. Please try the forgot password process again.
              </p>
            ) : (
              <>
                <p className="text-center text-sm text-gray-600 mb-4">
                  Enter your new password below.
                </p>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="New Password"
                  required
                />
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Confirm New Password"
                  required
                />
                {loading ? (
                  <LoadingSpinner />
                ) : (
                  <button type="submit" className="login-button">
                    Reset Password
                  </button>
                )}
              </>
            )}
            <div className="text-center mt-4">
              <a href="/login" className="text-blue-500 hover:underline">
                Back to Login
              </a>
            </div>
          </form>
        </div>
      </div>
    </>
  );
}