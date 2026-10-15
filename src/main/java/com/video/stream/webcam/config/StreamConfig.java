package com.video.stream.webcam.config;

import com.video.stream.webcam.capture.ProcessLauncher;
import com.video.stream.webcam.stream.RetryPolicy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StreamConfig {

    /** One thread per live stream; it blocks on the FFmpeg process for the stream's whole life. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService streamExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "stream-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public ProcessLauncher processLauncher() {
        return ProcessLauncher.system();
    }

    @Bean
    public RetryPolicy retryPolicy(StreamProperties props) {
        return RetryPolicy.from(props);
    }
}
