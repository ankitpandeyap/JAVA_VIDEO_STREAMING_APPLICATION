// src/main/java/com/robspecs/streaming/service/FileStorageService.java
package com.robspecs.streaming.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.springframework.core.io.Resource;

public interface FileStorageService {



    /**
     * Loads a file as a Spring Resource (e.g., for serving via HTTP).
     *
     * @param relativeFilePath The relative path of the file from the base path (e.g., "username/videos/raw/unique_id.mp4").
     * @return A Spring Resource representing the file.
     * @throws IOException If the file does not exist or cannot be read.
     */
    Resource loadFileAsResource(String relativeFilePath) throws IOException;

    /**
     * Returns the Path object for a stored file.
     *
     * @param relativeFilePath The relative path of the file from the base path.
     * @return A Path object for the specified file.
     */
    Path getFilePath(String relativeFilePath);

    /**
     * Deletes a file.
     *
     * @param relativeFilePath The relative path of the file to delete.
     * @return true if the file was successfully deleted, false otherwise.
     * @throws IOException If an I/O error occurs during deletion.
     */
    boolean deleteFile(String relativeFilePath) throws IOException;


    /**
     * Stores a file from an InputStream to the configured base path,
     * organizing it under a user-specific and type-specific subdirectory.
     *
     * @param inputStream The input stream of the file to store.
     * @param fileName The desired name of the file (e.g., unique_id.mp4).
     * @param UserID The useric of the uploader, used for user-specific folder.
     * @param typeSubdirectory An optional subdirectory within the user's video folder (e.g., "raw", "1080p", "720p", "hls"). Can be null or empty for root of user's video folder.
     * @return The relative path to the stored file from the base path (e.g., "username/videos/raw/unique_id.mp4").
     * @throws IOException If an I/O error occurs during storage.
     */
	String storeFile(InputStream inputStream, String fileName, Long userId, String typeSubdirectory) throws IOException;
}