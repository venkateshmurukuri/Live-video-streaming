package com.video.stream.webcam.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "ffmpeg")
public class FFmpegConfig {
    
    @Value("${ffmpeg.path}")
    private String ffmpegPath;
    
    @PostConstruct
    public void validateFFmpegPath() {
        log.info("Validating FFmpeg configuration...");
        log.info("FFmpeg path: {}", ffmpegPath);
        
        if (ffmpegPath == null || ffmpegPath.trim().isEmpty()) {
            log.error("FFmpeg path is not configured. Please set ffmpeg.path in application.properties");
            throw new IllegalStateException("FFmpeg path is not configured");
        }
        
        File ffmpegFile = new File(ffmpegPath);
        if (!ffmpegFile.exists()) {
            log.error("FFmpeg executable not found at path: {}", ffmpegPath);
            throw new IllegalStateException("FFmpeg executable not found at: " + ffmpegPath);
        }
        
        if (!ffmpegFile.canExecute()) {
            log.error("FFmpeg executable is not executable at path: {}", ffmpegPath);
            throw new IllegalStateException("FFmpeg executable is not executable at: " + ffmpegPath);
        }
        
        log.info("FFmpeg configuration validated successfully");
    }
    
    public String getFfmpegPath() {
        return ffmpegPath;
    }
    
    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }
}
