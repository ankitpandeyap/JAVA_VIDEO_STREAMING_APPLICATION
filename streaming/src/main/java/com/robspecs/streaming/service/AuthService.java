package com.robspecs.streaming.service;

import com.robspecs.streaming.dto.RegistrationDTO;
import com.robspecs.streaming.dto.ResetPasswordRequest;
import com.robspecs.streaming.entities.User;

public interface AuthService {

	User registerNewUser(RegistrationDTO regDTO);

	void processForgotPassword(String email); // <--- ADD THIS METHOD

	void resetPassword(ResetPasswordRequest request); // <--- ADD THIS METHOD
}
