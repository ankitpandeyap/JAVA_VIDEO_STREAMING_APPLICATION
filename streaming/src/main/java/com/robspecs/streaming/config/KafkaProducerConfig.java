package com.robspecs.streaming.config;

import com.robspecs.streaming.dto.VideoProcessingRequest;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, VideoProcessingRequest> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Add more producer properties if needed, e.g., acks, retries, batch size

        // Configure JsonSerializer to trust all packages (IMPORTANT for deserialization later)
        // In a production environment, you should specify exact packages or use a custom TrustedPackagesConverter
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false); // No type info headers if using standard JSON
        configProps.put(JsonSerializer.TYPE_MAPPINGS, "videoProcessingRequest:com.robspecs.streaming.dto.VideoProcessingRequest");


        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, VideoProcessingRequest> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
