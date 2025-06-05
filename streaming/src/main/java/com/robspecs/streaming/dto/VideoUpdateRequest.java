package com.robspecs.streaming.dto;

public class VideoUpdateRequest {
    private String title;       // Optional: for updating videoName
    private String description; // Optional: for updating description

    // Constructors
    public VideoUpdateRequest() {
    }

    public VideoUpdateRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}