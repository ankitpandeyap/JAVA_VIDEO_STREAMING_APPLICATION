// src/main/java/com/robspecs/videoprocessor/service/FileStorageService.java
package com.robspecs.videoprocessor.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import com.robspecs.streaming.exception.FileNotFoundException; // Reuse your custom exception
import com.robspecs.streaming.exception.FileStorageException; // Reuse your custom exception

@Service
public class FileStorageService {
	  private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
	private final Path fileStorageLocation;

	public FileStorageService(@Value("${files.video.base-path}") String uploadDir) {
		this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.fileStorageLocation);
		} catch (Exception ex) {
			throw new FileStorageException("Could not create the directory where the uploaded files will be stored.",
					ex);
		}
	}

	/**
	 * Stores an input stream as a file in a specified subdirectory structure.
	 * Example: storeFile(inputStream, "video.mp4", "user123", "processed", "720p")
	 * will store at /app/videos/user123/videos/processed/720p/video.mp4
	 *
	 * @param inputStream    The input stream of the file.
	 * @param fileName       The desired name of the file.
	 * @param subdirectories A list of subdirectories to create, e.g., "username",
	 *                       "videos", "raw".
	 * @return The relative path to the stored file from the base storage location.
	 */
	public String storeFile(InputStream inputStream, String fileName, String... subdirectories) {
		Path targetLocation = this.fileStorageLocation;
		for (String sub : subdirectories) {
			targetLocation = targetLocation.resolve(sub);
		}

		try {
			Files.createDirectories(targetLocation); // Ensure all subdirectories exist
			Path filePath = targetLocation.resolve(fileName);
			Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
			return this.fileStorageLocation.relativize(filePath).toString(); // Return relative path
		} catch (IOException ex) {
			throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
		}
	}

	/**
	 * Loads a file as a Resource from a relative path.
	 *
	 * @param relativeFilePath The relative path to the file from the base storage
	 *                         location.
	 * @return The Resource representing the file.
	 * @throws FileNotFoundException if the file does not exist.
	 */
	public Resource loadFileAsResource(String relativeFilePath) {
		try {
			Path filePath = this.fileStorageLocation.resolve(relativeFilePath).normalize();
			Resource resource = new UrlResource(filePath.toUri());
			if (resource.exists()) {
				return resource;
			} else {
				throw new FileNotFoundException("File not found " + relativeFilePath);
			}
		} catch (MalformedURLException ex) {
			throw new FileNotFoundException("File not found " + relativeFilePath, ex);
		}
	}

	/**
	 * Resolves a path to a file within the base storage location. Useful for
	 * getting the absolute path to pass to external tools like FFmpeg.
	 *
	 * @param relativeFilePath The relative path to the file.
	 * @return The absolute Path object.
	 */
	public Path resolvePath(String relativeFilePath) {
		return this.fileStorageLocation.resolve(relativeFilePath).normalize();
	}

	/**
	 * Creates a directory relative to the base path.
	 *
	 * @param pathParts The parts of the path to create (e.g., "user123",
	 *                  "processed", "hls")
	 * @return The absolute Path object of the created directory.
	 */
	public Path createDirectory(String... pathParts) {
		Path targetLocation = this.fileStorageLocation;
		for (String part : pathParts) {
			targetLocation = targetLocation.resolve(part);
		}
		try {
			Files.createDirectories(targetLocation);
			return targetLocation;
		} catch (IOException ex) {
			throw new FileStorageException("Could not create directory " + targetLocation, ex);
		}
	}

	/**
	 * Deletes a directory and its contents.
	 *
	 * @param relativePath The relative path of the directory to delete.
	 */
	public void deleteDirectory(String relativePath) {
		Path directoryPath = this.fileStorageLocation.resolve(relativePath).normalize();
		try {
			FileSystemUtils.deleteRecursively(directoryPath);
		} catch (IOException ex) {
			// Log the error but don't necessarily throw a runtime exception
			// as deletion might not be critical for main flow
			System.err.println("Could not delete directory: " + directoryPath + ". Error: " + ex.getMessage());
		}
	}

	/**
	 * Lists all files in a given directory relative to the base path.
	 *
	 * @param relativePath The relative path to the directory.
	 * @return A stream of Path objects.
	 */
	public Stream<Path> loadAll(String relativePath) {
		try {
			Path directoryPath = this.fileStorageLocation.resolve(relativePath).normalize();
			return Files.walk(directoryPath, 1).filter(path -> !path.equals(directoryPath))
					.map(directoryPath::relativize);
		} catch (IOException e) {
			throw new FileStorageException("Failed to read stored files", e);
		}
	}

	/**
	 * Gets the absolute path to the processed video directory for a specific video.
	 * This path is structured as: base_path/username/videos/processed/videoId/
	 *
	 * @param uuserid The userId of the video uploader.
	 * @param videoId  The ID of the video.
	 * @return The absolute Path object for the processed video directory.
	 */
	public Path getProcessedVideoDirectory(Long userId, Long videoId) {
		String cleanUsername = StringUtils.cleanPath(userId.toString());
		return this.fileStorageLocation.resolve(cleanUsername).resolve("videos").resolve("processed")
				.resolve(String.valueOf(videoId)).normalize();
	}

	/**
	 * Deletes a single file given its relative path.
	 *
	 * @param relativeFilePath The relative path of the file to delete from the base
	 *                         storage location.
	 * @return true if the file was deleted, false otherwise (e.g., file didn't
	 *         exist).
	 * @throws IOException          if an I/O error occurs during deletion.
	 * @throws FileStorageException if the path is outside the allowed storage
	 *                              location.
	 */
	public boolean deleteFile(String relativeFilePath) throws IOException {
        if (!StringUtils.hasText(relativeFilePath)) {
            logger.warn("Attempted to delete a file with an empty or null relative path.");
            return false;
        }
        Path filePath = resolvePath(relativeFilePath); // Use resolvePath for validation and absolute path

        if (!Files.exists(filePath)) {
            logger.info("File to delete does not exist: {}", filePath);
            return false;
        }

        try {
            Files.delete(filePath);
            return true;
        } catch (IOException e) {
            // Log the actual error that prevented deletion
            logger.error("Failed to delete file {}. It might be in use or permissions are insufficient. Error: {}", filePath, e.getMessage());
            // Re-throw the IOException so the calling service can handle it,
            // e.g., mark video as FAILED or log a warning if deletion isn't critical.
            throw e; // Important: Re-throw to propagate the failure
        }
    }

	/**
	 * Copies a file from a source absolute path to a destination absolute path.
	 *
	 * @param sourcePath      The absolute Path of the source file.
	 * @param destinationPath The absolute Path of the destination file.
	 * @return The absolute Path of the copied file.
	 * @throws IOException          if an I/O error occurs during the copy
	 *                              operation.
	 * @throws FileStorageException if source or destination paths are outside
	 *                              allowed storage location.
	 */
	public Path copyFile(Path sourcePath, Path destinationPath) throws IOException {
		// Ensure source and destination are within the base storage location
		if (!sourcePath.startsWith(this.fileStorageLocation) || !destinationPath.startsWith(this.fileStorageLocation)) {
			throw new FileStorageException("Attempted to copy file to/from outside of storage directory.");
		}
		Files.createDirectories(destinationPath.getParent()); // Ensure parent directory exists
		return Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Converts an absolute Path object to a relative path string based on the base
	 * storage location.
	 *
	 * @param absolutePath The absolute Path to convert.
	 * @return The relative path string.
	 * @throws FileStorageException if the absolute path is not within the base
	 *                              storage location.
	 */
	public String getRelativePath(Path absolutePath) {
		if (!absolutePath.startsWith(this.fileStorageLocation)) {
			throw new FileStorageException(
					"Attempted to get relative path for a path outside of storage directory: " + absolutePath);
		}
		return this.fileStorageLocation.relativize(absolutePath).toString();
	}
}