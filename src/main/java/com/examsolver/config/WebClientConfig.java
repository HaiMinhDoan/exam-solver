package com.examsolver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

//    @Value("${claude.api.key}")
//    private String claudeApiKey;
//
//    @Value("${claude.api.base-url:https://api.anthropic.com}")
//    private String claudeBaseUrl;
//
//    @Bean
//    public WebClient claudeWebClient() {
//        return WebClient.builder()
//                .baseUrl(claudeBaseUrl)
//                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//                .defaultHeader("x-api-key", claudeApiKey)
//                .defaultHeader("anthropic-version", "2023-06-01")
//                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
//                .build();
//    }

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com") // Bỏ /v1beta ở đây
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
