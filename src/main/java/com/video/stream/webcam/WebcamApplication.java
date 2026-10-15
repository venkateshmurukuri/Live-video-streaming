package com.video.stream.webcam;

import com.video.stream.webcam.config.StreamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StreamProperties.class)
public class WebcamApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebcamApplication.class, args);
    }
}
