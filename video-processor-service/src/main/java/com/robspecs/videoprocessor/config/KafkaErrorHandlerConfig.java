package com.robspecs.videoprocessor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaErrorHandlerConfig.class);

    @Bean
    public DefaultErrorHandler errorHandler() {
        // Configure retry with a fixed backoff of 5 seconds for 2 retries.
        // This means the message will be attempted a total of 3 times (initial attempt + 2 retries).
        FixedBackOff fixedBackOff = new FixedBackOff(5000L, 2L); // 5 seconds interval, 2 retries

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((consumerRecord, exception) -> {
            logger.error("Kafka Message processing failed after all retries for record with key: {}, topic: {}. Error: {}",
                    consumerRecord.key(),
                    consumerRecord.topic(),
                    exception.getMessage(),
                    exception);
            // The logic to update video status to FAILED and send an email
            // is handled by the catch blocks in VideoProcessorService after retries are exhausted.
        }, fixedBackOff);

        // Optional: Add exceptions that should NOT be retried (e.g., deserialization errors)
        // errorHandler.addNotRetryableExceptions(JsonDeserializationException.class);

        return errorHandler;
    }
}