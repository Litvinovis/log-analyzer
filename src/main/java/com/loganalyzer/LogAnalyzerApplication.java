package com.loganalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.loganalyzer.config.LogAnalyzerConfig;

@SpringBootApplication
@EnableConfigurationProperties(LogAnalyzerConfig.class)
public class LogAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogAnalyzerApplication.class, args);
    }
}
