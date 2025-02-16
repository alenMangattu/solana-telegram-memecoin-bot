package com.lunax.telegramBot.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import java.time.Duration;

@Slf4j
@Component
public class ApiRequestUtils {
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final ObjectMapper objectMapper;

    public ApiRequestUtils(
        @Value("${solana.api.base-url:http://localhost:3000}") String apiBaseUrl,
        ObjectMapper objectMapper
    ) {
        this.apiBaseUrl = apiBaseUrl;
        this.objectMapper = objectMapper;
        
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(60))
            .setReadTimeout(Duration.ofSeconds(60))
            .build();
    }

    public <T> ResponseEntity<T> makeRequest(
        String endpoint,
        HttpMethod method,
        Object body,
        Class<T> responseType
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> requestEntity = body != null
                ? new HttpEntity<>(body, headers)
                : new HttpEntity<>(headers);

            log.info("Making {} request to {}", method, endpoint);
            if (body != null) {
                log.debug("Request body: {}", objectMapper.writeValueAsString(body));
            }

            ResponseEntity<T> response = restTemplate.exchange(
                apiBaseUrl + endpoint,
                method,
                requestEntity,
                responseType
            );

            log.info("Response status: {}", response.getStatusCode());
            log.debug("Response body: {}", objectMapper.writeValueAsString(response.getBody()));

            return response;
        } catch (Exception e) {
            log.error("Error making request to {}: {}", endpoint, e.getMessage(), e);
            throw new RuntimeException("API request failed: " + e.getMessage(), e);
        }
    }
} 