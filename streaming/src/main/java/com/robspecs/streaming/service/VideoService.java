package com.robspecs.streaming.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.robspecs.streaming.entities.Video;

public interface VideoService {

	Video uploadVideo(Video v,MultipartFile file);
	Video getVideo(Long Id);
	Video searchByTitle(String videoName);
	Long updateViews(Long VideoId);
	//Admin
	List<Video> getAllVideos();
	
	
}
