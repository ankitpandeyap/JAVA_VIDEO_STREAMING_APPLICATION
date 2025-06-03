package com.robspecs.streaming.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CORSConfig implements WebMvcConfigurer {

	private static final Logger logger = LoggerFactory.getLogger(CORSConfig.class);

	@Value("${cors.allowed.origins}")
	private String[] allowedOrigins;

	@Value("${cors.allowed.methods}")
	private String[] allowedMethods;

	@Value("${cors.allowed.headers}")
	private String allowedHeaders;

	@Value("${cors.allowed.credentials}")
	private boolean allowedCredentials;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		logger.debug("Configuring CORS mappings");
		// TODO Auto-generated method stub
		registry.addMapping("/**").allowCredentials(allowedCredentials).allowedHeaders(allowedHeaders)
				.allowedMethods(allowedMethods).allowedOrigins(allowedOrigins).maxAge(3600)
				.exposedHeaders("Authorization");
		logger.debug("CORS mapping added for path '/**'");
		logger.debug("Allowed Origins: {}", String.join(",", allowedOrigins));
		logger.debug("Allowed Methods: {}", String.join(",", allowedMethods));
		logger.debug("Allowed Headers: {}", allowedHeaders);
		logger.debug("Allow Credentials: {}", allowedCredentials);
	}
}