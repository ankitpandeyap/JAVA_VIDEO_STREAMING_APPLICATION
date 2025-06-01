import { Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";

import Register from "./pages/Register";
import ProtectedRoute from "./components/ProtectedRoute";
import { useLocation } from "react-router-dom";
import Footer from "./components/Footer";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";

import ProfilePage from "./pages/ProfilePage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";

export default function App() {
  const location = useLocation(); // 🔍 Detect current route

  return (
    <>
      <Routes>
        <Route path="/" element={<Navigate to="/login" />} />
        <Route path="/register" element={<Register />} />
        <Route path="/login" element={<Login />} />

        {/* --- NEW ROUTES FOR PASSWORD RESET --- */}
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        {/* Important: This path must match the one in your backend's application.properties */}
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        <Route
          path="/profile" // <--- NEW ROUTE PATH
          element={
            <ProtectedRoute>
              <ProfilePage />{" "}
              {/* <--- Renders your new ProfilePage component */}
            </ProtectedRoute>
          }
        />
      </Routes>
      {location.pathname === "/login" && <Footer />}
      <ToastContainer position="top-center" autoClose={1000} />
    </>
  );
}
