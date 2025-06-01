package com.robspecs.streaming.serviceImpl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.robspecs.streaming.dto.VideoDetailsDTO;
import com.robspecs.streaming.dto.VideoUploadDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.entities.Video;
import com.robspecs.streaming.repository.VideosRepository;
import com.robspecs.streaming.service.VideoService;

import jakarta.annotation.PostConstruct;


@Service
public class VideoServiceImpl implements VideoService {

	private final VideosRepository videoRepository;
	
	public VideoServiceImpl(VideosRepository videoRepository) {
		this.videoRepository = videoRepository;
	}
	
	@Value("${files.video}")
	String DIR;
	
	@PostConstruct
	public void init() {
		File file = new File(DIR);
		if(file.exists() == false) {
				file.mkdirs();
		}
		else {
			//log
		}
		
	}
	
	@Override
	public void uploadVideo(VideoUploadDTO v, User user) throws IOException {
	    MultipartFile file = v.getFile();

	    if (file == null || file.isEmpty()) {
	        throw new IOException("Uploaded file is empty or null.");
	    }

	    if (!file.getContentType().startsWith("video/")) {
	        throw new IOException("Invalid file type. Only video files are allowed.");
	    }

	    String cleanFileName = StringUtils.cleanPath(file.getOriginalFilename());
	    String cleanUserName = StringUtils.cleanPath(user.getUsername());
	    Path cleanBaseDir = Paths.get(DIR).toAbsolutePath().normalize();

	    Path userDirPath = cleanBaseDir.resolve(cleanUserName);
	    Files.createDirectories(userDirPath);

	    Path filePath = userDirPath.resolve(cleanFileName);

	    try (InputStream fileStream = file.getInputStream()) {
	        Files.copy(fileStream, filePath, StandardCopyOption.REPLACE_EXISTING);
	    }
	    Video newVideo = new Video();
	    newVideo.setContentType(file.getContentType());
	    newVideo.setUploadUser(user);
	    newVideo.setVideoName(v.getTitle());
	    newVideo.setVideoURL(filePath.toString());
	    newVideo.setDescription(v.getDescription());
	    videoRepository.save(newVideo);
	    
	    
	}

	@Override
	public VideoDetailsDTO getVideo(Long Id,User user) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public VideoDetailsDTO searchByTitle(String videoName,User user) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long updateViews(Long VideoId,User user) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<VideoDetailsDTO> getAllVideos() {
		// TODO Auto-generated method stub
		return null;
	}

}
