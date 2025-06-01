package com.robspecs.streaming.serviceImpl;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.service.VideoService;

public class VideoServiceImpl implements VideoService {

	@Override
	public Video uploadVideo(Video v, MultipartFile file) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Video getVideo(Long Id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Video searchByTitle(String videoName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long updateViews(Long VideoId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Video> getAllVideos() {
		// TODO Auto-generated method stub
		return null;
	}

}
