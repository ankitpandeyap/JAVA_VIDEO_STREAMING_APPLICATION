package com.robspecs.videoprocessor.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service

public class EmailService {

	private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

	private final JavaMailSender mailSender;

	public EmailService(JavaMailSender mailSender) {
		super();
		this.mailSender = mailSender;
	}

	public void sendProcessingSuccessEmail(String toEmail, String videoName) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom("noreply@robspecs-streaming.com"); // Your sender email
		message.setTo(toEmail);
		message.setSubject("Your Video '" + videoName + "' is Ready!");
		message.setText("Dear user,\n\nYour video '" + videoName
				+ "' has been successfully processed and is now ready for streaming.\n\nEnjoy!\n\nRobSpecs Streaming Team");

		try {
			mailSender.send(message);
			logger.info("Successfully sent success email to {} for video {}", toEmail, videoName);
		} catch (MailException e) {
			logger.error("Failed to send success email to {} for video {}: {}", toEmail, videoName, e.getMessage());
			// You might want to re-queue, log to a dead-letter queue, or notify admin
		}
	}

	public void sendProcessingFailureEmail(String toEmail, String videoName, String errorMessage) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom("noreply@robspecs-streaming.com"); // Your sender email
		message.setTo(toEmail);
		message.setSubject("Video Processing Failed for '" + videoName + "'");
		message.setText("Dear user,\n\nWe regret to inform you that there was an issue processing your video '"
				+ videoName + "'.\n\nError details: " + errorMessage
				+ "\n\nPlease try re-uploading the video or contact support.\n\nRobSpecs Streaming Team");

		try {
			mailSender.send(message);
			logger.info("Successfully sent failure email to {} for video {}: {}", toEmail, videoName, errorMessage);
		} catch (MailException e) {
			logger.error("Failed to send failure email to {} for video {}: {}", toEmail, videoName, e.getMessage());
		}
	}
}