package com.video.stream.webcam.api;

import com.video.stream.webcam.api.dto.CaptureInfoResponse;
import com.video.stream.webcam.api.dto.StreamRequest;
import com.video.stream.webcam.api.dto.StreamStatusResponse;
import com.video.stream.webcam.capture.CaptureBackend;
import com.video.stream.webcam.capture.FFmpegCommandBuilder;
import com.video.stream.webcam.config.StreamProperties;
import com.video.stream.webcam.stream.StreamManager;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StreamController {

    private final StreamManager streams;
    private final FFmpegCommandBuilder commands;
    private final StreamProperties props;

    @GetMapping("/capture")
    public CaptureInfoResponse capture() {
        CaptureBackend backend = commands.backend();
        return new CaptureInfoResponse(backend.name().toLowerCase(Locale.ROOT), backend.defaultDevice(),
                backend.deviceHint(), props.getDefaultDestination());
    }

    @PostMapping("/streams")
    @ResponseStatus(HttpStatus.CREATED)
    public StreamStatusResponse create(@Valid @RequestBody StreamRequest request) {
        return StreamStatusResponse.from(streams.start(request.device().trim(), request.destination().trim()));
    }

    @GetMapping("/streams")
    public List<StreamStatusResponse> list() {
        return streams.list().stream().map(StreamStatusResponse::from).toList();
    }

    @GetMapping("/streams/{id}")
    public StreamStatusResponse get(@PathVariable UUID id) {
        return StreamStatusResponse.from(streams.get(id));
    }

    @DeleteMapping("/streams/{id}")
    public StreamStatusResponse stop(@PathVariable UUID id) {
        return StreamStatusResponse.from(streams.stop(id));
    }
}
