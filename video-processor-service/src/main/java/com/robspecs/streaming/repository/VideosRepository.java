package com.robspecs.streaming.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;

import jakarta.persistence.LockModeType;

@Repository
public interface VideosRepository extends JpaRepository<Video, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Override // It's good practice to mark overridden methods explicitly
	Optional<Video> findById(Long id);

	Optional<Video> findByVideoNameAndUploadUser(String videoName, User user);

	Optional<Video> findByVideoIdAndUploadUser(Long id, User user);

	List<Video> findAllByUploadUser(User user);

}
