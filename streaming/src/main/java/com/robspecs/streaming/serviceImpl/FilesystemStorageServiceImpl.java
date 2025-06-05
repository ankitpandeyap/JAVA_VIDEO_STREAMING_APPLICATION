package com.robspecs.streaming.serviceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.robspecs.streaming.exceptions.FileNotFoundException;
import com.robspecs.streaming.exceptions.FileStorageException;
import com.robspecs.streaming.service.FileStorageService;

import jakarta.annotation.PostConstruct;

@Service
public class FilesystemStorageServiceImpl implements FileStorageService {

	private final Path fileStorageLocation;
	public FilesystemStorageServiceImpl(@Value("${files.video.base-path}") String videoBasePath) {
		this.fileStorageLocation = Paths.get(videoBasePath).toAbsolutePath().normalize();
	}

	@PostConstruct
	public void init() {
		try {
			// Ensure the root directory for video storage exists
			Files.createDirectories(this.fileStorageLocation);
		} catch (IOException ex) {
			throw new FileStorageException("Could not create the directory where the uploaded files will be stored.",
					ex);
		}
	}

	@Override
	public String storeFile(InputStream inputStream, String fileName, Long userId, String typeSubdirectory)
			throws IOException {
		// Clean filename and username to prevent path traversal attacks
		String cleanFileName = StringUtils.cleanPath(Objects.requireNonNull(fileName));
		String cleanUsername = StringUtils.cleanPath(Objects.requireNonNull(userId.toString()));

		// Construct the target directory path:
		// base_path/username/videos/[typeSubdirectory]/
		Path targetDirectory = this.fileStorageLocation.resolve(cleanUsername).resolve("videos"); // Fixed 'videos'
																									// subdirectory

		if (StringUtils.hasText(typeSubdirectory)) {
			targetDirectory = targetDirectory.resolve(StringUtils.cleanPath(typeSubdirectory));
		}

		try {
			Files.createDirectories(targetDirectory); // Create user-specific and type-specific directories if they
														// don't exist
		} catch (IOException ex) {
			throw new FileStorageException("Could not create the directory for storing the file: " + targetDirectory,
					ex);
		}

		// Resolve the full path to the file
		Path targetFilePath = targetDirectory.resolve(cleanFileName);

		try {
			// Copy file to the target location
			Files.copy(inputStream, targetFilePath, StandardCopyOption.REPLACE_EXISTING);

			// Construct and return the relative path (from base_path)
			// This path is what we will store in the database.
			return this.fileStorageLocation // Use the absolute Path object initialized in constructor
				    .relativize(targetFilePath).toString();

		} catch (IOException ex) {
			throw new FileStorageException("Could not store file " + cleanFileName + ". Please try again!", ex);
		}
	}

	@Override
	public Resource loadFileAsResource(String relativeFilePath) {
		try {
			Path filePath = this.fileStorageLocation.resolve(relativeFilePath).normalize();
			Resource resource = new UrlResource(filePath.toUri());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			} else {
				throw new FileNotFoundException("File not found or not readable: " + relativeFilePath);
			}
		} catch (MalformedURLException ex) {
			throw new FileNotFoundException("File not found or not readable: " + relativeFilePath, ex);
		}
	}

	@Override
	public Path getFilePath(String relativeFilePath) {
		if (!StringUtils.hasText(relativeFilePath)) {
			throw new IllegalArgumentException("Relative file path cannot be empty.");
		}
		// Resolve the full absolute path
		Path fullPath = this.fileStorageLocation.resolve(relativeFilePath).normalize();
		// Check if the resolved path is actually within the base storage location
		if (!fullPath.startsWith(this.fileStorageLocation)) {
			throw new FileStorageException(
					"Attempted to access file outside of storage directory: " + relativeFilePath);
		}
		return fullPath;
	}

	@Override
	public boolean deleteFile(String relativeFilePath) throws IOException {
		if (!StringUtils.hasText(relativeFilePath)) {
			return false; // Nothing to delete
		}
		Path filePath = getFilePath(relativeFilePath); // Use getFilePath for path validation
		if (Files.exists(filePath)) {
			Files.delete(filePath);
			return true;
		}
		return false;
	}
	
	
	
	 @Override
	    public boolean deleteDirectory(String relativeDirectoryPath) throws IOException { // <--- NEW METHOD IMPLEMENTATION
	        if (!StringUtils.hasText(relativeDirectoryPath)) {
	            return false;
	        }
	        Path directoryPath = getFilePath(relativeDirectoryPath); // Use getFilePath for path validation and normalization
	        if (Files.exists(directoryPath) && Files.isDirectory(directoryPath)) {
	            try (Stream<Path> walk = Files.walk(directoryPath)) {
	                walk.sorted(Comparator.reverseOrder()) // Ensures files are deleted before their parent directories
	                    .forEach(path -> {
	                        try {
	                            Files.delete(path);
	                        } catch (IOException e) {
	                            // Log the error but don't re-throw to allow deletion of other files/subdirectories to proceed
	                            System.err.println("Failed to delete " + path + ": " + e.getMessage()); // Consider using a logger here instead of System.err
	                        }
	                    });
	            }
	            return true;
	        }
	        return false; // Directory did not exist or was not a directory
	    }
}