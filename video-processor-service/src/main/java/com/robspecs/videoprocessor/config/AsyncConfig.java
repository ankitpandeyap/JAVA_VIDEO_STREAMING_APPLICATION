package com.robspecs.videoprocessor.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync // This annotation enables Spring's asynchronous method execution capability
public class AsyncConfig {

	 @Bean(name = "taskExecutor") // You can name your executor bean; "taskExecutor" is a common default
	    public Executor taskExecutor() {
	        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
	        executor.setCorePoolSize(5);         // Minimum number of threads in the pool
	        executor.setMaxPoolSize(10);         // Maximum number of threads in the pool
	        executor.setQueueCapacity(25);      // Maximum number of tasks in the queue before new threads are created (up to maxPoolSize) or tasks are rejected
	        executor.setThreadNamePrefix("EmailAsync-"); // Prefix for the names of the threads in the pool
	        executor.initialize();
	        return executor;
	    }

    // You can define other executors here if you have different async needs
    // For example:
    /*
    @Bean(name = "videoProcessingTaskExecutor")
    public Executor videoProcessingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("VideoProcessor-");
        executor.initialize();
        return executor;
    }
    */
	 
	  @Bean(name = "videoProcessingExecutor") // Give it a specific name
	    public Executor videoProcessingExecutor() {
	        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
	        executor.setCorePoolSize(2); // Number of core threads (e.g., number of CPU cores)
	        executor.setMaxPoolSize(5);  // Maximum number of threads in the pool
	        executor.setQueueCapacity(500); // Capacity of the queue for pending tasks
	        executor.setThreadNamePrefix("VideoProcessor-"); // Prefix for thread names
	        executor.initialize();
	        return executor;
	    }
	 
	 
}