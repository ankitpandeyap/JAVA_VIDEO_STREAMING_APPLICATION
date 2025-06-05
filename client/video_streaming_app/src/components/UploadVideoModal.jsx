// src/components/UploadVideoModal.jsx - COMPLETE CODE USING axiosInstance
import React, { useState } from 'react';
import axiosInstance from '../api/axiosInstance'; // --- IMPORTANT: Changed to import axiosInstance
import { toast } from 'react-toastify'; // Import toast for consistent notifications
import '../css/UploadVideoModal.css';

// The path will be relative to API_BASE_URL defined in axiosInstance.js
const UPLOAD_PATH = '/videos/upload'; 

const UploadVideoModal = ({ onClose, onUploadSuccess }) => {
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [videoFile, setVideoFile] = useState(null);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [isUploading, setIsUploading] = useState(false);
    // Renamed uploadError to be consistent with toast usage, but kept for immediate form feedback
    const [formError, setFormError] = useState(''); 
    const [uploadSuccess, setUploadSuccess] = useState(false);

    const handleFileChange = (e) => {
        setVideoFile(e.target.files[0]);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setFormError(''); // Clear previous form-specific errors
        setUploadSuccess(false);

        if (!title || !description || !videoFile) {
            setFormError('Please fill in all fields and select a video file.');
            toast.error('Please fill in all fields and select a video file.'); // Toast for form validation
            return;
        }

        setIsUploading(true);
        setUploadProgress(0); // Reset progress

        const formData = new FormData();
        formData.append('title', title);
        formData.append('description', description);
        formData.append('file', videoFile); // Backend's DTO expects 'file'

        try {
            // Token handling is now automatically done by axiosInstance.interceptors.request.use
            const response = await axiosInstance.post(UPLOAD_PATH, formData, { // --- IMPORTANT: Using axiosInstance.post
                headers: {
                    'Content-Type': 'multipart/form-data',
                    // 'Authorization' header is added by the interceptor
                },
                onUploadProgress: (progressEvent) => {
                    const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                    setUploadProgress(percentCompleted);
                }
            });

            if (response.status === 200 || response.status === 201) {
                setUploadSuccess(true);
                toast.success('Video upload initiated successfully! Processing will begin shortly.'); // Success toast
                setTitle('');
                setDescription('');
                setVideoFile(null);

                setTimeout(() => {
                    onClose(); // Close modal
                    if (onUploadSuccess) {
                        onUploadSuccess(); // Callback for parent component (e.g., to refresh video list)
                    }
                }, 2000); // Close after 2 seconds
            } else {
                // This block might be less hit if axios interceptors handle specific statuses
                const message = response.data.message || 'Video upload failed due to an unknown reason.';
                setFormError(message);
                toast.error(message);
            }
        } catch (error) {
            console.error('Upload error:', error);
            // Error handling from axiosInstance.js takes precedence for 401s (token expiry, etc.).
            // For other errors, display specific messages using toast.
            if (error.response) {
                // For HTTP errors (e.g., 400 Bad Request, 500 Internal Server Error)
                const errorMessage = error.response.data.message || error.response.data.error || `Server error: ${error.response.status}`;
                setFormError(errorMessage); // For immediate form feedback
                toast.error(errorMessage); // For global notification
            } else if (error.request) {
                // For network errors (no response from server)
                setFormError('No response from server. Please check your network connection.');
                toast.error('No response from server. Please check your network connection.');
            } else {
                // Other unexpected errors
                setFormError('An unexpected error occurred during upload setup.');
                toast.error('An unexpected error occurred during upload setup.');
            }
        } finally {
            setIsUploading(false); // End uploading state
        }
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <button className="modal-close-button" onClick={onClose} disabled={isUploading}>&times;</button>
                <form className="upload-form" onSubmit={handleSubmit}>
                    <h2>Upload Your Video</h2>

                    <div className="form-group">
                        <label htmlFor="title">Video Title</label>
                        <input
                            type="text"
                            id="title"
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                            placeholder="e.g., My Awesome Vlog"
                            disabled={isUploading}
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="description">Description</label>
                        <textarea
                            id="description"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder="Tell us about your video..."
                            rows="4"
                            disabled={isUploading}
                        ></textarea>
                    </div>

                    <div className="form-group">
                        <label htmlFor="videoFile">Select Video File</label>
                        <input
                            type="file"
                            id="videoFile"
                            accept="video/*"
                            onChange={handleFileChange}
                            disabled={isUploading}
                        />
                        {videoFile && <span className="file-name">{videoFile.name}</span>}
                    </div>

                    {isUploading && (
                        <div className="progress-bar-container">
                            <div
                                className="progress-bar"
                                style={{ width: `${uploadProgress}%` }}
                            ></div>
                            <span className="progress-text">{uploadProgress}%</span>
                        </div>
                    )}

                    {/* Display form-specific errors directly in the form */}
                    {formError && <p className="upload-error-message">{formError}</p>}
                    {/* Display form-specific success message for a moment */}
                    {uploadSuccess && <p className="upload-success-message">Upload Successful!</p>}

                    <button type="submit" className="upload-button" disabled={isUploading}>
                        {isUploading ? `Uploading... (${uploadProgress}%)` : 'Upload Video'}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default UploadVideoModal;