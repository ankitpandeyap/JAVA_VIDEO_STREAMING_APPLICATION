// src/pages/MyVideosPage.jsx
import React, { useEffect, useState } from 'react'; // Removed useContext as it's not directly used here
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import Footer from '../components/Footer';
import VideoCard from '../components/VideoCard';
import LoadingSpinner from '../components/LoadingSpinner';
import Modal from '../components/Modal'; // <--- IMPORTANT: This is our new Modal component
import axiosInstance from '../api/axiosInstance';
import { toast } from 'react-toastify';

import '../css/MyVideosPage.css'; // Link to the CSS we just created

const MyVideosPage = () => {
    const [myVideos, setMyVideos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // State for Modals
    const [showEditModal, setShowEditModal] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [selectedVideo, setSelectedVideo] = useState(null); // The video object currently being edited/deleted

    // State for Edit Form Data
    const [editFormData, setEditFormData] = useState({
        title: '',
        description: ''
    });

    // Loading states for actions within modals (for buttons)
    const [deletingVideo, setDeletingVideo] = useState(false);
    const [updatingVideo, setUpdatingVideo] = useState(false);

    // Function to fetch videos uploaded by the current user
    const fetchMyVideos = async () => {
        setLoading(true);
        setError(''); // Clear any previous errors
        try {
            // Assuming your backend has an endpoint like /api/videos/my-videos
            const response = await axiosInstance.get('/videos/my-videos');
            setMyVideos(response.data);
            toast.success('Your videos loaded successfully!');
        } catch (err) {
            console.error('Failed to fetch my videos:', err);
            const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to load your videos. Please try again later.';
            setError(errorMessage);
            toast.error(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    // Effect hook to call fetchMyVideos when the component mounts
    useEffect(() => {
        fetchMyVideos();
    }, []);

    // --- Modal related functions ---

    const openEditModal = (video) => {
        setSelectedVideo(video);
        // Initialize form data with the selected video's current details
        setEditFormData({
            title: video.title,
            description: video.description
        });
        setShowEditModal(true); // Open the edit modal
    };

    const closeEditModal = () => {
        setShowEditModal(false); // Close the edit modal
        setSelectedVideo(null); // Clear selected video
        setEditFormData({ title: '', description: '' }); // Reset form data
    };

    const openDeleteModal = (video) => {
        setSelectedVideo(video);
        setShowDeleteModal(true); // Open the delete confirmation modal
    };

    const closeDeleteModal = () => {
        setShowDeleteModal(false); // Close the delete modal
        setSelectedVideo(null); // Clear selected video
    };

    // --- Form change handler for editing ---
    const handleEditChange = (e) => {
        const { name, value } = e.target;
        setEditFormData(prevData => ({
            ...prevData,
            [name]: value
        }));
    };

    // --- Video Update (Edit) Handler ---
    const handleUpdateVideoSubmit = async (e) => {
        e.preventDefault(); // Prevent default form submission behavior
        if (!selectedVideo) return; // Should not happen if modal is opened correctly

        setUpdatingVideo(true); // Start loading state for the button
        try {
            // Send PUT request to update video details
            await axiosInstance.put(`/videos/${selectedVideo.id}`, editFormData);
            toast.success('Video updated successfully!');
            fetchMyVideos(); // Re-fetch videos to update the displayed list
            closeEditModal(); // Close the modal
        } catch (err) {
            console.error('Failed to update video:', err);
            const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to update video. Please try again.';
            toast.error(errorMessage);
        } finally {
            setUpdatingVideo(false); // End loading state
        }
    };

    // --- Video Delete Handler ---
    const handleDeleteVideoConfirm = async () => {
        if (!selectedVideo) return; // Should not happen if modal is opened correctly

        setDeletingVideo(true); // Start loading state for the button
        try {
            // Send DELETE request to remove the video
            await axiosInstance.delete(`/videos/${selectedVideo.id}`);
            toast.success('Video deleted successfully!');
            fetchMyVideos(); // Re-fetch videos to update the displayed list
            closeDeleteModal(); // Close the modal
        } catch (err) {
            console.error('Failed to delete video:', err);
            const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to delete video. Please try again.';
            toast.error(errorMessage);
        } finally {
            setDeletingVideo(false); // End loading state
        }
    };

    return (
        <div className="my-videos-layout">
            <Header />
            <div className="my-videos-main-content">
                <Sidebar />
                <div className="my-videos-content-area">
                    <h1 className="my-videos-title">My Uploaded Videos</h1>
                    {loading ? (
                        <LoadingSpinner /> // Show full-page spinner while loading videos
                    ) : error ? (
                        <p className="error-message-my-videos">{error}</p> // Display error message
                    ) : myVideos.length > 0 ? (
                        <div className="video-grid">
                            {myVideos.map(video => (
                                <VideoCard
                                    key={video.id}
                                    video={video}
                                    showActions={true} // <--- Important: Tell VideoCard to show edit/delete buttons
                                    onEdit={() => openEditModal(video)} // Pass function to open edit modal
                                    onDelete={() => openDeleteModal(video)} // Pass function to open delete modal
                                />
                            ))}
                        </div>
                    ) : (
                        // Message if no videos are found
                        <p className="placeholder-text">You haven't uploaded any videos yet. Start sharing!</p>
                    )}
                </div>
            </div>
            <Footer />

            {/* --- Edit Video Modal --- */}
            {/* Render the Modal only if showEditModal is true and a video is selected */}
            {showEditModal && selectedVideo && (
                <Modal title="Edit Video Details" onClose={closeEditModal}>
                    <form onSubmit={handleUpdateVideoSubmit} className="modal-form">
                        <div className="form-group">
                            <label htmlFor="title">Video Title</label>
                            <input
                                type="text"
                                id="title"
                                name="title"
                                value={editFormData.title}
                                onChange={handleEditChange}
                                required
                                maxLength="100"
                            />
                        </div>
                        <div className="form-group">
                            <label htmlFor="description">Description</label>
                            <textarea
                                id="description"
                                name="description"
                                value={editFormData.description}
                                onChange={handleEditChange}
                                rows="4"
                                maxLength="500"
                            ></textarea>
                        </div>
                        <div className="modal-actions">
                            <button type="button" className="btn-secondary" onClick={closeEditModal} disabled={updatingVideo}>
                                Cancel
                            </button>
                            <button type="submit" className="btn-primary" disabled={updatingVideo}>
                                {updatingVideo ? <LoadingSpinner small={true} /> : 'Save Changes'} {/* Small spinner on button */}
                            </button>
                        </div>
                    </form>
                </Modal>
            )}

            {/* --- Delete Video Confirmation Modal --- */}
            {/* Render the Modal only if showDeleteModal is true and a video is selected */}
            {showDeleteModal && selectedVideo && (
                <Modal title="Confirm Deletion" onClose={closeDeleteModal}>
                    <p className="modal-body-text">
                        Are you sure you want to delete the video "<strong>{selectedVideo.title}</strong>"?
                        This action cannot be undone.
                    </p>
                    <div className="modal-actions">
                        <button type="button" className="btn-secondary" onClick={closeDeleteModal} disabled={deletingVideo}>
                            Cancel
                        </button>
                        <button type="button" className="btn-danger" onClick={handleDeleteVideoConfirm} disabled={deletingVideo}>
                                {deletingVideo ? <LoadingSpinner small={true} /> : 'Delete Video'} {/* Small spinner on button */}
                        </button>
                    </div>
                </Modal>
            )}
        </div>
    );
};

export default MyVideosPage;