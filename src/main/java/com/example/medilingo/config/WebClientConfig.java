package com.example.medilingo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient openFdaWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.fda.gov")
                .build();
    }

    @Bean
    public WebClient bingWebClient(@Value("${bing.endpoint}") String endpoint) {
        return WebClient.builder().baseUrl(endpoint).build();
    }
}
