package com.robspecs.streaming.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;

public interface VideoService {

    // Updated return type: now returns the saved Video entity.
    // IOException is now wrapped in FileStorageException (a RuntimeException),
    // so it doesn't need to be declared here.
    Video uploadVideo(VideoUploadDTO v, User user);

    // This method will now fetch a single video's details for playback or display.
    VideoDetailsDTO getVideo(Long Id , User user);

    // For searching videos by title.
    VideoDetailsDTO searchByTitle(String videoName, User user);

    // This is where we'll implement the view count increment with pessimistic locking.
    Long updateViews(Long videoId, User user);

    // Admin function to get all videos.
    List<VideoDetailsDTO> getAllVideos();
}
