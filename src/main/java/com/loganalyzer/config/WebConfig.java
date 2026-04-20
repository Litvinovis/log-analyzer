package com.loganalyzer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LogAnalyzerConfig config;

    public WebConfig(LogAnalyzerConfig config) {
        this.config = config;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(config.getCorsAllowedOrigin())
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}
