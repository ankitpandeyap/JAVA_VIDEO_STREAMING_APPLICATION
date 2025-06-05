// src/components/Modal.jsx
import React from 'react';
import '../css/Modal.css'; // Link to its dedicated CSS file

const Modal = ({ children, onClose, title, showCloseButton = true }) => {
    // This prevents clicks on the modal content from closing the modal
    const handleContentClick = (e) => {
        e.stopPropagation();
    };

    return (
        // The modal-backdrop covers the whole screen and closes the modal when clicked outside
        <div className="modal-backdrop" onClick={onClose}>
            {/* The modal-content-container holds the actual modal's visible parts */}
            <div className="modal-content-container" onClick={handleContentClick}>
                <div className="modal-header">
                    {title && <h3 className="modal-title">{title}</h3>}
                    {showCloseButton && (
                        <button className="modal-close-button" onClick={onClose}>
                            &times; {/* HTML entity for a multiplication sign, commonly used as a close 'x' */}
                        </button>
                    )}
                </div>
                <div className="modal-body">
                    {children} {/* This is where all the content you pass to the Modal will appear */}
                </div>
            </div>
        </div>
    );
};

export default Modal;