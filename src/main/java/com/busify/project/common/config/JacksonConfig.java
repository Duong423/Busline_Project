package com.busify.project.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.util.TimeZone;

/**
 * Cấu hình Jackson ObjectMapper toàn cục cho ứng dụng
 * Đảm bảo datetime được parse và serialize đúng theo múi giờ Việt Nam
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.createXmlMapper(false).build();
        
        // Đăng ký module JavaTime
        mapper.registerModule(new JavaTimeModule());
        
        // Đăng ký custom deserializer cho Instant
        SimpleModule vietnamTimeModule = new SimpleModule("VietnamTimeModule");
        vietnamTimeModule.addDeserializer(Instant.class, new VietnamInstantDeserializer());
        mapper.registerModule(vietnamTimeModule);
        
        // Cấu hình timezone
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        
        // Không serialize dates như timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Bỏ qua các field không xác định trong JSON (không throw exception)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        System.out.println("[JacksonConfig] ObjectMapper configured with VietnamInstantDeserializer");
        
        return mapper;
    }
}
