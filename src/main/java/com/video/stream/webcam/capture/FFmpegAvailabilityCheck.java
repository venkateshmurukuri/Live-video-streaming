package com.video.stream.webcam.capture;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Fails startup with a clear message if FFmpeg cannot be executed, instead of failing on the first stream. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FFmpegAvailabilityCheck {

    private final FFmpegCommandBuilder commands;
    private final ProcessLauncher launcher;

    @PostConstruct
    void verify() {
        List<String> cmd = commands.versionCommand();
        try {
            Process process = launcher.launch(cmd);
            String banner = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (banner == null) {
                        banner = line;
                    }
                }
            }
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("'" + String.join(" ", cmd) + "' did not exit cleanly");
            }
            log.info("{} | capture backend: {}", banner, commands.backend());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "FFmpeg not found at '" + cmd.get(0) + "'. Install it or set FFMPEG_PATH.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking FFmpeg", e);
        }
    }
}
