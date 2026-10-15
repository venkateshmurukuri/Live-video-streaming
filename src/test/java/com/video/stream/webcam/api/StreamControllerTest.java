package com.video.stream.webcam.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.video.stream.webcam.capture.CaptureBackend;
import com.video.stream.webcam.capture.FFmpegCommandBuilder;
import com.video.stream.webcam.config.StreamProperties;
import com.video.stream.webcam.stream.StreamManager;
import com.video.stream.webcam.stream.StreamNotFoundException;
import com.video.stream.webcam.stream.StreamSession;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StreamController.class)
@EnableConfigurationProperties(StreamProperties.class)
class StreamControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StreamManager streams;

    @MockitoBean
    private FFmpegCommandBuilder commands;

    @Test
    void createReturns201WithTheNewStream() throws Exception {
        StreamSession session = new StreamSession(UUID.randomUUID(), "0", "rtmp://localhost:1935/hls/cam");
        when(streams.start("0", "rtmp://localhost:1935/hls/cam")).thenReturn(session);

        mvc.perform(post("/api/streams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device\":\"0\",\"destination\":\"rtmp://localhost:1935/hls/cam\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(session.id().toString()))
                .andExpect(jsonPath("$.state").value("STARTING"))
                .andExpect(jsonPath("$.retryCount").value(0));
    }

    @Test
    void createRejectsNonRtmpDestinationAsProblemDetail() throws Exception {
        mvc.perform(post("/api/streams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device\":\"0\",\"destination\":\"http://localhost/hls/cam\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", containsString("destination")));
    }

    @Test
    void createRejectsMissingDevice() throws Exception {
        mvc.perform(post("/api/streams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device\":\" \",\"destination\":\"rtmp://localhost/hls/cam\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("device")));
    }

    @Test
    void unknownStreamIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(streams.get(id)).thenThrow(new StreamNotFoundException(id));

        mvc.perform(get("/api/streams/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString(id.toString())));
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(delete("/api/streams/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("not-a-uuid")));
    }

    @Test
    void captureInfoDescribesTheDetectedBackend() throws Exception {
        when(commands.backend()).thenReturn(CaptureBackend.AVFOUNDATION);

        mvc.perform(get("/api/capture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend").value("avfoundation"))
                .andExpect(jsonPath("$.defaultDevice").value("0"))
                .andExpect(jsonPath("$.defaultDestination").value("rtmp://localhost:1935/hls/cam"));
    }

    @Test
    void stopReturnsTheStoppedStream() throws Exception {
        StreamSession session = new StreamSession(UUID.randomUUID(), "test", "rtmp://localhost/hls/x");
        when(streams.stop(any())).thenReturn(session);

        mvc.perform(delete("/api/streams/" + session.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.id().toString()));
    }
}
