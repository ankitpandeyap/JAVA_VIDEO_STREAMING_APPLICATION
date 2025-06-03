package com.robspecs.streaming.service;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;

import java.util.List;

public interface VideoService {

    Video uploadVideo(VideoUploadDTO videoUploadDTO, User user);

    VideoDetailsDTO getVideo(Long videoId, User user);

    VideoDetailsDTO searchByTitle(String videoName, User user);

    Long updateViews(Long videoId, User user);

    List<VideoDetailsDTO> getAllVideos();

    // You might also want a method to retrieve a video by its path for streaming purposes
    // This could return a Resource or simply the Path, depending on how your controller handles it
    Video getVideoByFilePath(String relativeFilePath);
}
