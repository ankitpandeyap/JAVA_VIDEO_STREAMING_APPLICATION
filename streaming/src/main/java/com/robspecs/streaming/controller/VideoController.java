package com.robspecs.streaming.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.service.VideoService;

@RestController
@RequestMapping("/api/video")
public class VideoController {

	private final VideoService videoService;

	public VideoController(VideoService videoService) {
		this.videoService = videoService;

	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> uploadVideo(@ModelAttribute VideoUploadDTO videoDTO,
			@AuthenticationPrincipal User currentUser) {

		if (videoDTO.getFile() == null || videoDTO.getFile().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("message", "No file provided"));
		}

		try {
			videoService.uploadVideo(videoDTO, currentUser);
			return ResponseEntity.ok(Map.of("message", "Upload successful"));
		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Upload failed: " + e.getMessage()));
		}

	}
}
