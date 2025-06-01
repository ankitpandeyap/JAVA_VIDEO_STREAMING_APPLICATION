import React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import "../css/Sidebar.css";

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation(); // Get current location

  const handleComposeClick = () => {
    navigate("/compose");
  };

  const handleInboxClick = () => {
    navigate("/dashboard");
  };

  const handleSentClick = () => {
    navigate("/sent");
  };

   const handleProfileClick = () => {
    navigate("/profile");
  };

  // Determine if we are on the /sent page
  const isOnSentPage = location.pathname === "/sent";
  // NEW: Determine if we are on the /profile page
  const isOnProfilePage = location.pathname === "/profile";


  return (
    <div className="sidebar-container">
      <nav className="sidebar-nav">
        {/*
          Compose Button:
          - Visible if on /dashboard page (original logic)
          - OR visible if on /sent page (new requirement to show all three)
          - OR visible if on /profile page (new requirement to show all three)
          - Active if on /compose page
        */}
        {(location.pathname === "/dashboard" || isOnSentPage || isOnProfilePage) && ( // <--- MODIFIED CONDITION
          <button
            className={`sidebar-button compose-button ${
              location.pathname === "/compose" ? "active" : ""
            }`}
            onClick={handleComposeClick}
          >
            <span className="icon">+</span> Compose
          </button>
        )}

        {/*
          Inbox Button:
          - Visible if on /compose page (original logic)
          - OR visible if on /sent page (new requirement to show all three)
          - OR visible if on /profile page (new requirement to show all three)
          - Active if on /dashboard page
        */}
        {(location.pathname === "/compose" || isOnSentPage || isOnProfilePage) && ( // <--- MODIFIED CONDITION
          <button
            className={`sidebar-button ${
              location.pathname === "/dashboard" ? "active" : ""
            }`}
            onClick={handleInboxClick}
          >
            <span className="icon">✉️</span> Inbox
          </button>
        )}

        {/*
          Sent Messages Button:
          - Always visible (original logic)
          - Active AND disabled if on /sent page
        */}
        <button
          className={`sidebar-button ${
            isOnSentPage ? "active" : ""
          }`}
          onClick={handleSentClick}
          disabled={isOnSentPage} // Disable when on the sent page
        >
          <span className="icon">➡️</span> Sent
        </button>

        {/*
          Profile Button: NEWLY ADDED
          - Always visible (assuming you want it consistently available like "Sent")
          - Active AND disabled if on /profile page
        */}
        <button
          className={`sidebar-button ${
            isOnProfilePage ? "active" : ""
          }`}
          onClick={handleProfileClick}
          disabled={isOnProfilePage} // Disable when on the profile page
        >
          <span className="icon">👤</span> Profile {/* Using a generic user icon */}
        </button>
      </nav>
    </div>
  );
}
