package com.robspecs.streaming.serviceImpl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.robspecs.streaming.dto.UserDTO;
import com.robspecs.streaming.dto.UserProfileDTO;
import com.robspecs.streaming.entities.User;
import com.robspecs.streaming.exceptions.ResourceNotFoundException;
import com.robspecs.streaming.repository.UserRepository;
import com.robspecs.streaming.service.UserService;



@Service
public class UserServiceImpl implements UserService {

	private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class); // Add this line
	private final UserRepository userRepository;

	public UserServiceImpl(UserRepository userRepository) {
		super();
		this.userRepository = userRepository;
	}

	@Override
	public List<UserDTO> getAllUsers(User authUser) {
		return userRepository.findAll().stream().filter(user -> !user.getUserName().equals(authUser.getUsername()))
				.map(user -> new UserDTO(user.getUserId(), user.getUserName(), user.getEmail()))
				.collect(Collectors.toList());
	}

	// MINIMAL CHANGE: Add this method
    @Override
    public Optional<User> findByUserName(String userName) {
        // Leveraging existing findByEmailOrUserName as you don't have a direct findByUserName
        // This assumes userName is unique and can be found by findByEmailOrUserName
        return userRepository.findByEmailOrUserName(userName);
    }

	// MINIMAL CHANGE: Add this method
    @Override
    public UserProfileDTO getUserProfile(String username) {
        log.info("Attempting to fetch user profile for username: {}", username);
        User user = findByUserName(username) // Use the findByUserName method we just added
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        log.debug("Found user: {}, creating UserProfileDTO.", username);
        return new UserProfileDTO(user.getUserName(), user.getEmail(), user.getCreatedAt());
    }
}