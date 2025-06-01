import React, { useContext } from "react";
import { AuthContext } from "../context/AuthContext";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import axiosInstance from "../api/axiosInstance";
import "../css/Header.css";

export default function Header() {
  // Get both logout and isAuthenticated from AuthContext
  const { isAuthenticated, logout } = useContext(AuthContext);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  let pageTitle = "";
  if (pathname === "/dashboard") pageTitle = "Your Inbox";
  else if (pathname === "/compose") pageTitle = "Compose Message";
  else if (pathname === "/sent") pageTitle = "Sent Messages";
  else if (pathname === "/profile")  pageTitle = "Your Profile";
  const handleLogout = async () => {
    try {
      await axiosInstance.post("/auth/logout");
      logout();
      toast.success("Logged out successfully");
      navigate("/login");
    } catch (error) {
      console.error("Logout failed:", error); // Keeping console.error for critical failures
      toast.error("Logout failed: " + (error.response?.data || "Server error"));
    }
  };
  return (
    <header className="header-bar">
      <div className="header-logo">CRYPTO_APP</div>
      <div className="header-title">{pageTitle}</div>
      <nav className="header-nav">
        {isAuthenticated && pathname !== "/login" && (
          <button className="header-logout-btn" onClick={handleLogout}>
            Logout
          </button>
        )}
      </nav>
    </header>
  );
}
