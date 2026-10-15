package com.video.stream.webcam.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CaptureBackendTest {

    @ParameterizedTest
    @CsvSource({
            "Windows 11, DSHOW",
            "Mac OS X, AVFOUNDATION",
            "Darwin, AVFOUNDATION",
            "Linux, V4L2",
            "FreeBSD, V4L2"
    })
    void detectsNativeBackendFromOsName(String osName, CaptureBackend expected) {
        assertThat(CaptureBackend.detect(osName)).isEqualTo(expected);
    }

    @Test
    void autoAndBlankResolveToDetectedBackend() {
        assertThat(CaptureBackend.fromProperty("auto", "Mac OS X")).isEqualTo(CaptureBackend.AVFOUNDATION);
        assertThat(CaptureBackend.fromProperty("  ", "Linux")).isEqualTo(CaptureBackend.V4L2);
        assertThat(CaptureBackend.fromProperty(null, "Windows 10")).isEqualTo(CaptureBackend.DSHOW);
    }

    @Test
    void explicitValueOverridesDetection() {
        assertThat(CaptureBackend.fromProperty("v4l2", "Mac OS X")).isEqualTo(CaptureBackend.V4L2);
        assertThat(CaptureBackend.fromProperty("TEST", "Windows 11")).isEqualTo(CaptureBackend.TEST);
    }

    @Test
    void unknownValueIsRejectedWithTheValidOptions() {
        assertThatThrownBy(() -> CaptureBackend.fromProperty("gstreamer", "Linux"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gstreamer")
                .hasMessageContaining("avfoundation");
    }

    @Test
    void dshowPassesDeviceNameAsOneArgumentEvenWithSpaces() {
        assertThat(CaptureBackend.DSHOW.inputArgs("HD Pro Webcam C920", 30, "1280x720"))
                .containsSequence("-f", "dshow")
                .contains("video=HD Pro Webcam C920");
    }

    @Test
    void testBackendNeedsNoDevice() {
        assertThat(CaptureBackend.TEST.inputArgs("ignored", 25, "640x360"))
                .containsSequence("-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25");
    }
}
