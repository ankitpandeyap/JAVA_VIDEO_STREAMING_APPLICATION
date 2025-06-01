import React, { useState, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../context/AuthContext";

import axiosInstance from "../api/axiosInstance";
import { toast } from "react-toastify";
import LoadingSpinner from "../components/LoadingSpinner";
import "../css/Login.css";
import Header from "../components/Header";

export default function Login() {
  // Step 1: Form fields
  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const { login } = useContext(AuthContext);
  const navigate = useNavigate();

  // Step 3: Submit handler
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const response = await axiosInstance.post("/auth/login", {
        usernameOrEmail,
        password,
      });
      /*
            const response = await new Promise((resolve, reject) => {
            setTimeout(async () => {
              try {
                const res = await axiosInstance.post('/auth/login', {
                  username,
                  password,
                });
                resolve(res); // Resolve with the actual response
              } catch (err) {
                reject(err); // Reject with the error from axiosInstance.post
              }
            }, 1000); // Delay of 6 seconds
          });
      */
      // Axios normalizes all header keys to lowercase
      const token =
        response.headers.get("authorization") ||
        response.headers["authorization"];

      if (token) {
        login(token); // store in localStorage + update auth state
        toast.success("Login successful!");
        navigate("/dashboard");
      } else {
        toast.error("Login failed: No token received");
      }
    }  catch (error) {
      // NEW: Improved error handling for validation messages from backend filter
      if (error.response && error.response.data && typeof error.response.data === 'object' && error.response.data.error) {
         // Backend filter returns {"error": "field: message, field2: message2"}
         toast.error(error.response.data.error);
      } else {
         // Fallback for other errors (e.g., 401 Unauthorized, 500 Internal Server Error)
         toast.error(error.response?.data || "Invalid username or password");
      }
    } finally {
      setLoading(false);
    }
  };

  // Step 6: JSX UI
 return (
    <>
      <Header />
      <div className="login-page-container">
        <div className="login-container">
          <form onSubmit={handleLoginSubmit} className="login-form">
            <h2 className="login-title">Login</h2>

            <input
              type="text"
              value={usernameOrEmail}
              onChange={(e) => setUsernameOrEmail(e.target.value)}
              placeholder="Username Or Email"
              required
            />
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              required
            />
            {loading ? (
              <LoadingSpinner />
            ) : (
              <button type="submit" className="login-button">
                Login
              </button>
            )}

            {/* --- ADD THIS LINK --- */}
            <div className="text-center mt-2">
              <a href="/forgot-password" className="text-blue-500 hover:underline text-sm">
                Forgot Password?
              </a>
            </div>

          </form>
        </div>
      </div>
    </>
  );
}