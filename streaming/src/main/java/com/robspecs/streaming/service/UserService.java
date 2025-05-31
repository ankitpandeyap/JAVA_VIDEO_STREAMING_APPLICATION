package com.robspecs.streaming.service;

import java.util.List;
import java.util.Optional;

import com.robspecs.streaming.dto.UserDTO;
import com.robspecs.streaming.dto.UserProfileDTO;
import com.robspecs.streaming.entities.User;

public interface UserService {
	public List<UserDTO> getAllUsers(User user);

	Optional<User> findByUserName(String userName);

	UserProfileDTO getUserProfile(String username);
}
