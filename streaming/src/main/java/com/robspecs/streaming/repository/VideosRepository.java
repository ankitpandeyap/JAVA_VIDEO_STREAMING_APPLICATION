package com.robspecs.streaming.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;

public interface VideosRepository extends JpaRepository<Video, Long> {
	
	List<Video> findByVideoNameAndUploadUser(String videoName, User user);
	Optional<Video> findByVideoIdAndUploadUser(Long id,User user);
	List<Video> findAllByUploadUser(User user);

}
