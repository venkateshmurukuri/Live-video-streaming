package com.video.stream.webcam.service.impl;

import com.video.stream.webcam.dto.StreamStatus;
import com.video.stream.webcam.service.FFmpegService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FFmpegServiceImpl implements FFmpegService {
    
    private final Map<UUID, StreamStatus> streamStatusMap = new ConcurrentHashMap<>();
    
    @Value("${ffmpeg.path}")
    private String ffmpegPath;
    
    // FFmpeg command template
    private String getFFmpegCommandTemplate() {
        return String.format("\"%s\" -f dshow -rtbufsize 50M -i video=\"%%s\" -f lavfi -i anullsrc " +
            "-vcodec libx264 -preset ultrafast -tune zerolatency -crf 28 " +
            "-maxrate 2000k -bufsize 4000k -pix_fmt yuv420p -g 30 -r 30 " +
            "-keyint_min 30 -acodec aac -b:a 128k -ar 44100 -threads 2 " +
            "-avoid_negative_ts make_zero -fflags +genpts -f flv %%s", ffmpegPath);
    }
    
    @Qualifier("ffmpegTaskExecutor")
    private final Executor ffmpegTaskExecutor;
    
    @Override
    public StreamStatus startStreaming(UUID streamId, String webcamName, String rtmpDestination) {
        log.info("Starting FFmpeg streaming for streamId: {}, webcam: {}, destination: {}", 
                streamId, webcamName, rtmpDestination);
        log.info("Using FFmpeg path: {}", ffmpegPath);
        
        // Validate FFmpeg path
        if (ffmpegPath == null || ffmpegPath.trim().isEmpty()) {
            log.error("FFmpeg path is not configured");
            throw new IllegalStateException("FFmpeg path is not configured");
        }
        
        // Check webcam availability before starting
        if (!isWebcamAvailable(webcamName)) {
            log.error("Webcam '{}' is not available or already in use", webcamName);
            throw new IllegalStateException("Webcam '" + webcamName + "' is not available or already in use");
        }
        
        // Create initial stream status
        StreamStatus streamStatus = StreamStatus.builder()
                .streamId(streamId.toString())
                .status("STARTING")
                .startTime(LocalDateTime.now())
                .retryCount(0)
                .isActive(true)
                .build();
        
        streamStatusMap.put(streamId, streamStatus);
        
        // Start streaming in a separate thread
        ffmpegTaskExecutor.execute(() -> runFFmpegStream(streamId, webcamName, rtmpDestination));
        
        return streamStatus;
    }
    

    
    @Async("ffmpegTaskExecutor")
    protected void runFFmpegStream(UUID streamId, String webcamName, String rtmpDestination) {
        log.info("Starting FFmpeg streaming thread for streamId: {}", streamId);
        
        Thread currentThread = Thread.currentThread();
        Process process = null;
        
        while (isStreamActive(streamId)) {
            try {
                // Check if webcam is available
                if (!isWebcamAvailable(webcamName)) {
                    log.error("Webcam '{}' is not available, attempting to force release", webcamName);
                    Thread.sleep(3000);
                    handleRetry(streamId, webcamName, rtmpDestination);
                    continue;
                }
                
                String command = buildFFmpegCommand(webcamName, rtmpDestination);
                log.info("Executing FFmpeg command for streamId: {}: {}", streamId, command);
                
                process = executeFFmpegCommand(command);
                if (process != null) {
                    updateStreamStatus(streamId, "RUNNING", process, currentThread);
                    logFFmpegOutput(streamId, process);
                    
                    int exitCode = process.waitFor();
                    log.warn("FFmpeg process exited with code: {} for streamId: {}", exitCode, streamId);
                    
                    if (!isStreamActive(streamId)) {
                        log.info("Stream {} stopped by user, exiting retry loop", streamId);
                        break;
                    }
                    
                    // Ensure process is properly destroyed
                    if (process.isAlive()) {
                        process.destroy();
                        if (!process.waitFor(3, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                    }
                    
                    // Retry logic
                    handleRetry(streamId, webcamName, rtmpDestination);
                }
            } catch (InterruptedException e) {
                // This is expected when stopping the stream
                log.info("FFmpeg streaming thread interrupted for streamId: {} - stream is being stopped", streamId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in FFmpeg streaming for streamId: {}", streamId, e);
                
                // Ensure process is properly destroyed on error
                if (process != null && process.isAlive()) {
                    process.destroy();
                    try {
                        if (!process.waitFor(3, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                handleRetry(streamId, webcamName, rtmpDestination);
            }
        }
        
        log.info("FFmpeg streaming thread ended for streamId: {}", streamId);
    }
    
    private String buildFFmpegCommand(String webcamName, String rtmpDestination) {
        return String.format(getFFmpegCommandTemplate(), webcamName, rtmpDestination);
    }
    
    private Process executeFFmpegCommand(String command) {
        try {
            log.debug("Executing command: {}", command);
            return Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            log.error("Failed to execute FFmpeg command: {}", command, e);
            return null;
        }
    }
    
    private void logFFmpegOutput(UUID streamId, Process process) {
        // Log FFmpeg output in separate threads
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg [{}] STDOUT: {}", streamId, line);
                }
            } catch (IOException e) {
                log.error("Error reading FFmpeg stdout for streamId: {}", streamId, e);
            }
        }, "ffmpeg-stdout-" + streamId).start();
        
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("FFmpeg [{}] STDERR: {}", streamId, line);
                }
            } catch (IOException e) {
                log.error("Error reading FFmpeg stderr for streamId: {}", streamId, e);
            }
        }, "ffmpeg-stderr-" + streamId).start();
    }
    
    private void handleRetry(UUID streamId, String webcamName, String rtmpDestination) {
        StreamStatus status = streamStatusMap.get(streamId);
        if (status == null || !status.isActive()) {
            return;
        }
        
        status.setRetryCount(status.getRetryCount() + 1);
        status.setStatus("RETRYING");
        status.setLastRetryTime(LocalDateTime.now());
        status.setLastError("FFmpeg process exited, retrying...");
        
        log.warn("Retrying FFmpeg stream for streamId: {}, retry count: {}", 
                streamId, status.getRetryCount());
        
        try {
            Thread.sleep(2000); // 2 second wait time
        } catch (InterruptedException e) {
            log.warn("Retry wait interrupted for streamId: {}", streamId);
            Thread.currentThread().interrupt();
            return;
        }
        
        if (isStreamActive(streamId)) {
            log.info("Retrying FFmpeg stream for streamId: {}", streamId);
        }
    }
    
    private void updateStreamStatus(UUID streamId, String status, Process process, Thread thread) {
        StreamStatus streamStatus = streamStatusMap.get(streamId);
        if (streamStatus != null) {
            streamStatus.setStatus(status);
            streamStatus.setProcess(process);
            streamStatus.setThread(thread);
            streamStatusMap.put(streamId, streamStatus);
            
            // Log the process ID for better tracking
            if (process != null) {
                try {
                    long pid = process.pid();
                    log.info("Stream {} process ID: {}", streamId, pid);
                } catch (UnsupportedOperationException e) {
                    log.debug("Process ID not available for stream {}", streamId);
                }
            }
        }
    }
    
    @Override
    public boolean stopStreaming(UUID streamId) {
        log.info("Stopping FFmpeg streaming for streamId: {}", streamId);
        
        StreamStatus status = streamStatusMap.get(streamId);
        if (status == null) {
            log.warn("Stream not found for streamId: {}", streamId);
            return false;
        }
        
        // Mark as inactive first
        status.setActive(false);
        status.setStatus("STOPPING");
        
        // Stop the process gracefully first
        Process process = status.getProcess();
        if (process != null && process.isAlive()) {
            log.info("Terminating FFmpeg process for streamId: {}", streamId);
            process.destroy();
            
            // Wait for graceful termination
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("Graceful termination failed, force killing FFmpeg process for streamId: {}", streamId);
                    process.destroyForcibly();
                    
                    // Wait a bit more to ensure it's killed
                    if (!process.waitFor(3, TimeUnit.SECONDS)) {
                        log.error("Failed to kill FFmpeg process for streamId: {}", streamId);
                    }
                } else {
                    log.info("FFmpeg process terminated gracefully for streamId: {}", streamId);
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for process termination for streamId: {}", streamId);
                Thread.currentThread().interrupt();
                // Force kill if interrupted
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }
        
        // Interrupt the thread
        Thread thread = status.getThread();
        if (thread != null && thread.isAlive()) {
            log.info("Interrupting streaming thread for streamId: {}", streamId);
            thread.interrupt();
            
            // Wait for thread to finish (with timeout)
            try {
                thread.join(5000); // Wait up to 5 seconds
                if (thread.isAlive()) {
                    log.warn("Streaming thread did not terminate within timeout for streamId: {}", streamId);
                } else {
                    log.info("Streaming thread terminated successfully for streamId: {}", streamId);
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for thread termination for streamId: {}", streamId);
                Thread.currentThread().interrupt();
            }
        }
        
        // Update final status
        status.setStatus("STOPPED");
        status.setProcess(null);
        status.setThread(null);
        
        log.info("Successfully stopped FFmpeg streaming for streamId: {}", streamId);
        return true;
    }
    
    @Override
    public StreamStatus getStreamStatus(UUID streamId) {
        return streamStatusMap.get(streamId);
    }
    
    @Override
    public boolean isStreamActive(UUID streamId) {
        StreamStatus status = streamStatusMap.get(streamId);
        return status != null && status.isActive();
    }
    
    /**
     * PreDestroy method to ensure all FFmpeg processes are stopped when the application shuts down
     */
    @PreDestroy
    public void cleanupOnShutdown() {
        log.info("Application shutting down - cleaning up all FFmpeg processes");
        
        // Stop all active streams first
        for (Map.Entry<UUID, StreamStatus> entry : streamStatusMap.entrySet()) {
            UUID streamId = entry.getKey();
            StreamStatus status = entry.getValue();
            
            if (status != null && status.isActive()) {
                log.info("Stopping stream {} during shutdown", streamId);
                stopStreaming(streamId);
            }
        }
        
        // Only force kill any remaining FFmpeg processes if we still have active streams
        boolean hasActiveStreams = streamStatusMap.values().stream()
                .anyMatch(status -> status != null && status.isActive());
        
        if (hasActiveStreams) {
            try {
                log.info("Force killing any remaining FFmpeg processes");
                String killCommand = "taskkill /f /im ffmpeg.exe";
                Process killProcess = Runtime.getRuntime().exec(killCommand);
                
                if (killProcess.waitFor(5, TimeUnit.SECONDS)) {
                    log.info("Successfully killed remaining FFmpeg processes");
                } else {
                    killProcess.destroyForcibly();
                    log.warn("Timeout while killing FFmpeg processes during shutdown");
                }
            } catch (Exception e) {
                log.error("Error killing FFmpeg processes during shutdown: {}", e.getMessage());
            }
        } else {
            log.info("No active streams found, skipping force kill of FFmpeg processes");
        }
        
        log.info("Cleanup completed");
    }
    

    
    /**
     * Check if webcam is available by testing a brief FFmpeg command
     */
    private boolean isWebcamAvailable(String webcamName) {
        log.info("Checking availability of webcam: {}", webcamName);
        
        // Test webcam availability with a brief FFmpeg command
        String testCommand = String.format("\"%s\" -f dshow -list_devices true -i dummy", ffmpegPath);
        
        try {
            Process process = Runtime.getRuntime().exec(testCommand);
            
            // Read the output to check if our webcam is listed
            boolean webcamFound = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(webcamName)) {
                        webcamFound = true;
                        break;
                    }
                }
            }
            
            // Wait for process to complete
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Device listing process timed out");
                return false;
            }
            
            if (!webcamFound) {
                log.error("Webcam '{}' not found in available devices", webcamName);
                return false;
            }
            
            // Now test if the webcam can be opened
            String testOpenCommand = String.format("\"%s\" -f dshow -i video=\"%s\" -t 1 -f null -", 
                    ffmpegPath, webcamName);
            
            Process testProcess = Runtime.getRuntime().exec(testOpenCommand);
            
            // Wait for a short time to see if it can open the device
            if (testProcess.waitFor(3, TimeUnit.SECONDS)) {
                int exitCode = testProcess.exitValue();
                if (exitCode == 0) {
                    log.info("Webcam '{}' is available and can be opened", webcamName);
                    return true;
                } else {
                    log.error("Webcam '{}' cannot be opened, exit code: {}", webcamName, exitCode);
                    return false;
                }
            } else {
                // If it takes too long, the device might be in use
                testProcess.destroyForcibly();
                log.error("Webcam '{}' appears to be in use (timeout on open test)", webcamName);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error checking webcam availability for '{}': {}", webcamName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Force release webcam device by killing any FFmpeg processes that might be using it
     * This is only used as a fallback when we can't access the webcam
     */
    // private void forceReleaseWebcam(String webcamName) {
    //     log.info("Attempting to force release webcam: {}", webcamName);
        
    //     try {
    //         // On Windows, we can use tasklist to find FFmpeg processes
    //         String findCommand = "tasklist /fi \"imagename eq ffmpeg.exe\" /fo csv /nh";
    //         Process findProcess = Runtime.getRuntime().exec(findCommand);
            
    //         StringBuilder output = new StringBuilder();
    //         try (BufferedReader reader = new BufferedReader(new InputStreamReader(findProcess.getInputStream()))) {
    //             String line;
    //             while ((line = reader.readLine()) != null) {
    //                 output.append(line).append("\n");
    //             }
    //         }
            
    //         if (findProcess.waitFor(5, TimeUnit.SECONDS) && output.length() > 0) {
    //             // Only kill FFmpeg processes if they exist and we can't access the webcam
    //             log.info("Found existing FFmpeg processes, terminating them to release webcam");
    //             String killCommand = "taskkill /f /im ffmpeg.exe";
    //             Process killProcess = Runtime.getRuntime().exec(killCommand);
                
    //             if (killProcess.waitFor(5, TimeUnit.SECONDS)) {
    //                 log.info("Terminated existing FFmpeg processes to release webcam");
    //             } else {
    //                 killProcess.destroyForcibly();
    //                 log.warn("Timeout while terminating FFmpeg processes");
    //             }
    //         } else {
    //             log.info("No existing FFmpeg processes found");
    //         }
            
    //         // Wait a bit for the system to release the device
    //         Thread.sleep(2000);
            
    //     } catch (Exception e) {
    //         log.error("Error force releasing webcam: {}", e.getMessage());
    //     }
    // }
}
