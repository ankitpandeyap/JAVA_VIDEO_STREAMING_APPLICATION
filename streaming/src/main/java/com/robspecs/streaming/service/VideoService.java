package com.robspecs.streaming.service;

import java.util.List;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUpdateRequest;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.exceptions.FileNotFoundException;

public interface VideoService {

    Video uploadVideo(VideoUploadDTO videoUploadDTO, User user);

    VideoDetailsDTO getVideo(Long videoId, User user);

    VideoDetailsDTO searchByTitle(String videoName, User user);

    Long updateViews(Long videoId, User user);

    Video getActualVideoEntity(Long videoId, User user);

    List<VideoDetailsDTO> getAllVideos();

    // You might also want a method to retrieve a video by its path for streaming purposes
    // This could return a Resource or simply the Path, depending on how your controller handles it
    Video getVideoByFilePath(String relativeFilePath);

    /**
    * Retrieves all videos uploaded by a specific user.
    * @param user The authenticated user.
    * @return A list of VideoDetailsDTOs for the user's videos.
    */
   List<VideoDetailsDTO> getVideosByCurrentUser(User user); // <--- NEW METHOD SIGNATURE

   /**
    * Updates details of an existing video.
    * @param videoId The ID of the video to update.
    * @param updateRequest DTO containing updated video information.
    * @param currentUser The authenticated user (for authorization).
    * @return The updated VideoDetailsDTO.
    */
   VideoDetailsDTO updateVideo(Long videoId, VideoUpdateRequest updateRequest, User currentUser); // <--- NEW METHOD SIGNATURE

   /**
    * Deletes a video.
    * @param videoId The ID of the video to delete.
    * @param currentUser The authenticated user (for authorization).
    * @throws com.robspecs.streaming.exceptions.FileNotFoundException if the video is not found.
    * @throws java.lang.SecurityException if the user is not authorized to delete the video.
    * @throws java.io.IOException if an I/O error occurs during file deletion.
    */
   void deleteVideo(Long videoId, User currentUser); // <--- NEW METHOD SIGNATURE
   
   Video findVideoById(Long videoId) throws FileNotFoundException;
}
