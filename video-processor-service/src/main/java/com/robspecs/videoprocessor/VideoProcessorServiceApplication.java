package com.robspecs.videoprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = { "com.robspecs.videoprocessor", "com.robspecs.streaming" })
@EnableJpaRepositories(basePackages = "com.robspecs.streaming.repository")
@EntityScan(basePackages = "com.robspecs.streaming.entities")
public class VideoProcessorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VideoProcessorServiceApplication.class, args);
	}

}
