package com.robspecs.streaming.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;

public interface VideoService {

	void uploadVideo(VideoUploadDTO v,User user) throws IOException;
	VideoDetailsDTO getVideo(Long Id , User user);
	VideoDetailsDTO searchByTitle(String videoName,User user);
	Long updateViews(Long VideoId,User user);
	//Admin
	List<VideoDetailsDTO> getAllVideos();
	
	
}
