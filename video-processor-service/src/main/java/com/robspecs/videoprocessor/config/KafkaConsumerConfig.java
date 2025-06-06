package com.robspecs.videoprocessor.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.robspecs.videoprocessor.dto.VideoProcessingRequest; // Import the DTO from its new location

@EnableKafka // Enables Kafka listener annotation processing
@Configuration
public class KafkaConsumerConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${spring.kafka.consumer.group-id}")
	private String groupId;

	 private final DefaultErrorHandler errorHandler;
	 public KafkaConsumerConfig(DefaultErrorHandler errorHandler) {
	        this.errorHandler = errorHandler;
	    }

	@Bean
	public ConsumerFactory<String, VideoProcessingRequest> consumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

		// Configure JsonDeserializer for the value
		JsonDeserializer<VideoProcessingRequest> jsonDeserializer = new JsonDeserializer<>(
				VideoProcessingRequest.class);
		jsonDeserializer.setRemoveTypeHeaders(false); // Keep type headers for safer deserialization
		jsonDeserializer.addTrustedPackages("*"); // Trust all packages for deserialization
		jsonDeserializer.setUseTypeHeaders(false);

		return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), jsonDeserializer);
	}

	@Bean
    public ConcurrentKafkaListenerContainerFactory<String, VideoProcessingRequest> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, VideoProcessingRequest> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1); // Keep concurrency at 1 for long-running tasks per message

        factory.setCommonErrorHandler(errorHandler); // This line tells the factory to use our custom error handler
         return factory;
    }
}