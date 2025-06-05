import React, { useState } from "react";
import axiosInstance from "../api/axiosInstance";
import "../css/Register.css";
import { useNavigate } from "react-router-dom";
import { toast } from "react-toastify";

// Import CSS and component for LoadingSpinner
// Note: It's good practice to keep component-specific styles with the component
const LoadingSpinner = ({ className }) => {
  return (
    <div className={className}>
      <div className="spinner"></div>
      {/* Conditionally render text only if it's not a button spinner */}
      {/* This ensures "Loading, please wait..." doesn't appear inside buttons */}
      {className !== "button-spinner" && <p className="loading-text">Loading, please wait...</p>}
    </div>
  );
};

// LoadingSpinner CSS (Overriding for Register Page)
// const loadingSpinnerCSS = `
// /* Styles for the container when used inside a button */
// .button-spinner {
//   display: flex;
//   align-items: center; /* Center vertically within the button space */
//   justify-content: center; /* Center horizontally */
//   height: 2.5rem; /* Match your button's typical height */
//   width: 100%; /* Take full width of button */
// }

// /* Styles for the spinner itself - significantly smaller for button context */
// .spinner {
//   width: 1em;   /* Even smaller: 1em, which is roughly 16px by default */
//   height: 1em;  /* Even smaller */
//   border: 0.1em solid rgba(255, 255, 255, 0.4); /* Thinner border, slightly more transparent */
//   border-top: 0.1em solid #ffffff; /* White border for spinner on dark buttons */
//   border-radius: 50%;
//   animation: rotate 1.2s linear infinite;
//   /* Removed margin-bottom and margin-right for a compact, button-only spinner */
// }

// /* Styles for the loading text when used with a spinner (e.g., page-level loading) */
// /* This part is generally for a larger, page-level spinner, not for buttons */
// .loading-text {
//   font-size: 0.8rem;
//   color: #555; /* Adjust color as needed for your theme */
//   font-style: normal;
//   font-weight: 400;
//   margin-top: 0.5rem; /* Add some space above text if spinner and text are stacked */
// }

// /* Keyframe animation for rotation */
// @keyframes rotate {
//   0% {
//     transform: rotate(0deg);
//   }
//   100% {
//     transform: rotate(360deg);
//   }
// }
// `;

export default function Register() {
  const [email, setEmail] = useState("");
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  // const [passkey, setPasskey] = useState(""); // Removed passkey state
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState(1);
  const [name, setName] = useState("");
  const [role, setRole] = useState("USER");
  const navigate = useNavigate();

  const [sendingOtp, setSendingOtp] = useState(false);
  const [verifyingOtp, setVerifyingOtp] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [otpVerified, setOtpVerified] = useState(false);
  // const [showPasskey, setShowPasskey] = useState(false); // Removed showPasskey state

  // useEffect(() => {
  //   const style = document.createElement("style");
  //   style.textContent = loadingSpinnerCSS;
  //   document.head.appendChild(style);

  //   return () => {
  //     document.head.removeChild(style);
  //   };
  // }, []);

  const handleSendOtp = async (e) => {
    e.preventDefault();
    setSendingOtp(true);
    try {
      const res = await axiosInstance.post(
        `/auth/otp/request?email=${encodeURIComponent(email)}`
      );
      toast.success(res.data);
      setStep(2);
    } catch (err) {
      toast.error(err.response?.data || "Failed to send OTP");
    } finally {
      setSendingOtp(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setVerifyingOtp(true);
    try {
      const res = await axiosInstance.post(
        `/auth/otp/verify?email=${encodeURIComponent(
          email
        )}&otp=${encodeURIComponent(otp)}`
      );

      if (res.data && res.data.verified === true) {
        setOtpVerified(true);
        setStep(3);
        toast.success(res.data.message || "OTP verified successfully!");
      } else {
        toast.error(
          res.data?.message || "OTP verification failed. Please try again."
        );
      }
    } catch (err) {
      toast.error(
        err.response?.data?.message ||
          "OTP verification failed: An unexpected error occurred."
      );
    } finally {
      setVerifyingOtp(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    if (!otpVerified) {
      toast.error("Please verify OTP first.");
      return;
    }
    // Removed passkey length validation as passkey is no longer part of DTO
    // if (passkey.length < 16) {
    //   toast.error("Passkey must be at least 16 characters long.");
    //   return;
    // }

    setRegistering(true);
    try {
      const res = await axiosInstance.post("/auth/register", {
        email: email,
        userName: userName,
        password: password,
        // passkey: passkey, // Removed passkey from the payload
        role: role,
        name: name,
      });
      toast.success(res.data);
      navigate("/login");
    } catch (err) {
      if (
        err.response &&
        err.response.status === 400 &&
        typeof err.response.data === "object"
      ) {
        const errors = err.response.data;
        Object.keys(errors).forEach((field) => {
          toast.error(`${field}: ${errors[field]}`);
        });
      } else {
        toast.error(err.response?.data || "Registration failed");
      }
    } finally {
      setRegistering(false);
    }
  };

  return (
    <div className="register-container">
      <div className="register-card">
        <h2 className="register-title">
          {step === 1 && "Step 1: Send OTP"}
          {step === 2 && "Step 2: Verify OTP"}
          {step === 3 && "Step 3: Complete Registration"}
        </h2>
        {step === 1 && (
          <form onSubmit={handleSendOtp} className="register-form">
            <input
              type="email"
              placeholder="Enter your email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              title="Please enter a valid email address (e.g., user@example.com)"
            />
            {sendingOtp ? (
              <LoadingSpinner className="button-spinner" />
            ) : (
              <button
                type="submit"
                className="btn-primary otp-button"
                disabled={sendingOtp}
              >
                Send OTP
              </button>
            )}
          </form>
        )}
        {step === 2 && (
          <form onSubmit={handleVerifyOtp} className="register-form">
            <input
              type="text"
              placeholder="Enter OTP"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              required
              minlength="6"
              maxlength="6"
              pattern="\d{6}"
              title="Please enter the 6-digit OTP"
            />
            {verifyingOtp ? (
              <LoadingSpinner className="button-spinner" />
            ) : (
              <button
                type="submit"
                className="btn-primary otp-button"
                disabled={verifyingOtp}
              >
                Verify OTP
              </button>
            )}
          </form>
        )}
        {step === 3 && otpVerified && (
          <form onSubmit={handleRegister} className="register-form">
            <input
              type="text"
              placeholder="Username"
              value={userName}
              onChange={(e) => setUserName(e.target.value)}
              required
              minlength="3"
              maxlength="20"
              title="Username must be between 3 and 20 characters."
            />
            <input
              type="text"
              placeholder="Full Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              minlength="2"
              maxlength="50"
              title="Full Name must be between 2 and 50 characters."
            />
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minlength="8"
              // Optional: Add a pattern for stronger passwords if needed (e.g., from DTO's commented pattern)
              // pattern="^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*]).{8,}$"
              // title="Password must be at least 8 characters, including at least one digit, one lowercase, one uppercase, and one special character."
            />
            {/* Removed the passkey input container */}
            {/* <div className="passkey-input-container">
              <input
                type={showPasskey ? "text" : "password"}
                placeholder="Passkey (secret phrase for encryption) Minimum 16 Characters"
                value={passkey}
                onChange={(e) => setPasskey(e.target.value)}
                required
                minlength="16"
                title="Passkey must be at least 16 characters long."
              />
              <button
                type="button"
                className="passkey-toggle-btn"
                onClick={() => setShowPasskey(!showPasskey)}
              >
                {showPasskey ? "Hide" : "Show"}
              </button>
            </div> */}
            <select
              value={role}
              onChange={(e) => setRole(e.target.value)}
              className="register-dropdown"
            >
              <option value="USER">User</option>
              <option value="ADMIN">Admin</option>
            </select>
            {registering ? (
              <LoadingSpinner className="button-spinner" />
            ) : (
              <button
                type="submit"
                className="btn-primary complete-register"
                disabled={registering}
              >
                Complete Registration
              </button>
            )}
          </form>
        )}
      </div>
    </div>
  );
}