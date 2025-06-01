import React, { useState } from "react";
import axiosInstance from "../api/axiosInstance"; // Your configured Axios instance
import { toast } from "react-toastify";
import LoadingSpinner from "../components/LoadingSpinner"; // Assuming you have this component
import Header from "../components/Header"; // Assuming you have this component
import "../css/Login.css"; // Reuse existing CSS for consistent styling

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      // Send the email to your backend's forgot-password endpoint
      const response = await axiosInstance.post("/auth/forgot-password", { email });

      // The backend should return a generic success message for security reasons
      // (to prevent email enumeration attacks).
      toast.success(response.data || "If an account with that email exists, a password reset link has been sent.");
      setEmail(""); // Clear the email field after successful submission
    } catch (error) {
      console.error("Forgot password request failed:", error); // Log the error for debugging

      // Improved error handling based on backend response structure
      if (error.response && error.response.data) {
        if (typeof error.response.data === 'string') {
          // Backend might return a plain string error message
          toast.error(error.response.data);
        } else if (error.response.data.error) {
          // Backend might return {"error": "message"} for custom exceptions
          toast.error(error.response.data.error);
        } else if (error.response.data.message) {
          // Standard Spring Boot validation errors or other messages
          toast.error(error.response.data.message);
        } else {
          toast.error("Failed to send password reset request. Please try again.");
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
      <div className="login-page-container"> {/* Reusing the full-page container */}
        <div className="login-container"> {/* Reusing the card-like container */}
          <form onSubmit={handleSubmit} className="login-form">
            <h2 className="login-title">Forgot Password</h2>
            <p className="text-center text-sm text-gray-600 mb-4">
              Enter your registered email address to receive a password reset link.
            </p>
            <input
              type="email" // Use type="email" for better mobile keyboard and validation
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Registered Email"
              required
            />
            {loading ? (
              <LoadingSpinner />
            ) : (
              <button type="submit" className="login-button">
                Send Reset Link
              </button>
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