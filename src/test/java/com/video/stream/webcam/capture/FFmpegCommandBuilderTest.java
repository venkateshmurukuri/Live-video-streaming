package com.video.stream.webcam.capture;

import static org.assertj.core.api.Assertions.assertThat;

import com.video.stream.webcam.config.StreamProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class FFmpegCommandBuilderTest {

    private static final String DEST = "rtmp://localhost:1935/hls/cam";

    private static StreamProperties props() {
        StreamProperties props = new StreamProperties();
        props.setFfmpegPath("/opt/ffmpeg/bin/ffmpeg");
        props.setFps(30);
        props.setVideoSize("1280x720");
        props.setVideoBitrateKbps(2000);
        return props;
    }

    @Test
    void usesConfiguredBinaryAndEndsWithTheRtmpDestination() {
        List<String> cmd = new FFmpegCommandBuilder(props(), CaptureBackend.AVFOUNDATION).build("0", DEST);

        assertThat(cmd.get(0)).isEqualTo("/opt/ffmpeg/bin/ffmpeg");
        assertThat(cmd).containsSequence("-f", "avfoundation").containsSequence("-i", "0");
        assertThat(cmd).containsSequence("-f", "flv", DEST);
        assertThat(cmd.get(cmd.size() - 1)).isEqualTo(DEST);
    }

    @Test
    void deviceNamesWithSpacesStayOneArgument() {
        List<String> cmd = new FFmpegCommandBuilder(props(), CaptureBackend.DSHOW).build("HD Webcam", DEST);

        assertThat(cmd).contains("video=HD Webcam");
        assertThat(cmd).doesNotContain("video=\"HD", "Webcam\"");
    }

    @Test
    void testDeviceUsesSyntheticSourceRegardlessOfBackend() {
        List<String> cmd = new FFmpegCommandBuilder(props(), CaptureBackend.V4L2).build("test", DEST);

        assertThat(cmd).containsSequence("-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30");
        assertThat(cmd).doesNotContain("v4l2");
    }

    @Test
    void encodesLowLatencyH264WithTwoSecondGopAndSilentAac() {
        List<String> cmd = new FFmpegCommandBuilder(props(), CaptureBackend.V4L2).build("/dev/video0", DEST);

        assertThat(cmd).containsSequence("-c:v", "libx264")
                .containsSequence("-tune", "zerolatency")
                .containsSequence("-g", "60")
                .containsSequence("-keyint_min", "60")
                .containsSequence("-sc_threshold", "0")
                .containsSequence("-b:v", "2000k", "-maxrate", "2000k", "-bufsize", "4000k")
                .containsSequence("-c:a", "aac");
        assertThat(cmd).contains("anullsrc=channel_layout=stereo:sample_rate=44100");
        assertThat(cmd).containsSequence("-map", "0:v:0", "-map", "1:a:0");
    }

    @Test
    void versionCommandUsesTheSameBinary() {
        assertThat(new FFmpegCommandBuilder(props(), CaptureBackend.TEST).versionCommand())
                .containsExactly("/opt/ffmpeg/bin/ffmpeg", "-version");
    }
}
