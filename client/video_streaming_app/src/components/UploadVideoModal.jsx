// src/components/UploadVideoModal.jsx

import React, { useState } from 'react';
import axios from 'axios'; // Assuming you use axios for API calls
import '../css/UploadVideoModal.css'; // Styling for the modal

// Dummy API URL for demonstration. Replace with your actual backend endpoint.
const UPLOAD_API_URL = 'http://localhost:5000/api/videos/upload'; // **IMPORTANT: Replace with your actual backend URL**

const UploadVideoModal = ({ onClose, onUploadSuccess }) => {
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [videoFile, setVideoFile] = useState(null);
    const [thumbnailFile, setThumbnailFile] = useState(null);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadError, setUploadError] = useState('');
    const [uploadSuccess, setUploadSuccess] = useState(false);

    const handleFileChange = (e, type) => {
        if (type === 'video') {
            setVideoFile(e.target.files[0]);
        } else if (type === 'thumbnail') {
            setThumbnailFile(e.target.files[0]);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setUploadError('');
        setUploadSuccess(false);

        if (!title || !description || !videoFile || !thumbnailFile) {
            setUploadError('Please fill in all fields and select both files.');
            return;
        }

        setIsUploading(true);
        setUploadProgress(0); // Reset progress

        const formData = new FormData();
        formData.append('title', title);
        formData.append('description', description);
        formData.append('video', videoFile);
        formData.append('thumbnail', thumbnailFile);

        try {
            const response = await axios.post(UPLOAD_API_URL, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                    // Add authorization header if your API requires it
                    // 'Authorization': `Bearer ${localStorage.getItem('token')}`
                },
                onUploadProgress: (progressEvent) => {
                    const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                    setUploadProgress(percentCompleted);
                }
            });

            if (response.status === 200 || response.status === 201) {
                setUploadSuccess(true);
                // Optionally, pass the uploaded video data back to the parent component
                // onUploadSuccess(response.data.video);
                setTitle('');
                setDescription('');
                setVideoFile(null);
                setThumbnailFile(null);

                // Auto-close modal or show success message then close
                setTimeout(() => {
                    onClose();
                    // If you have a way to refresh dashboard data, call it here
                    // onUploadSuccess();
                }, 2000); // Close after 2 seconds
            } else {
                setUploadError(response.data.message || 'Video upload failed.');
            }
        } catch (error) {
            console.error('Upload error:', error);
            if (error.response) {
                setUploadError(error.response.data.message || 'Server error during upload.');
            } else if (error.request) {
                setUploadError('No response from server. Check your network connection.');
            } else {
                setUploadError('Error setting up the upload request.');
            }
        } finally {
            setIsUploading(false);
        }
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <button className="modal-close-button" onClick={onClose}>&times;</button>
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
                            onChange={(e) => handleFileChange(e, 'video')}
                            disabled={isUploading}
                        />
                        {videoFile && <span className="file-name">{videoFile.name}</span>}
                    </div>

                    <div className="form-group">
                        <label htmlFor="thumbnailFile">Select Thumbnail Image</label>
                        <input
                            type="file"
                            id="thumbnailFile"
                            accept="image/*"
                            onChange={(e) => handleFileChange(e, 'thumbnail')}
                            disabled={isUploading}
                        />
                        {thumbnailFile && <span className="file-name">{thumbnailFile.name}</span>}
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

                    {uploadError && <p className="upload-error-message">{uploadError}</p>}
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