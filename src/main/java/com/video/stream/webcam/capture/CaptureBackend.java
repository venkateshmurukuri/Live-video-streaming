package com.video.stream.webcam.capture;

import java.util.List;
import java.util.Locale;

/** The FFmpeg input device used to grab the camera, chosen per operating system. */
public enum CaptureBackend {

    DSHOW("Integrated Camera",
            "device name as listed by: ffmpeg -list_devices true -f dshow -i dummy"),
    AVFOUNDATION("0",
            "device index or name as listed by: ffmpeg -f avfoundation -list_devices true -i \"\""),
    V4L2("/dev/video0",
            "device node, usually /dev/video0 (v4l2-ctl --list-devices)"),
    /** Synthetic colour bars from lavfi; needs no hardware. */
    TEST("test",
            "no camera needed, a moving test pattern is generated");

    private final String defaultDevice;
    private final String deviceHint;

    CaptureBackend(String defaultDevice, String deviceHint) {
        this.defaultDevice = defaultDevice;
        this.deviceHint = deviceHint;
    }

    public String defaultDevice() {
        return defaultDevice;
    }

    public String deviceHint() {
        return deviceHint;
    }

    /** Picks the native backend for an {@code os.name} value. */
    public static CaptureBackend detect(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return AVFOUNDATION;
        }
        if (os.contains("win")) {
            return DSHOW;
        }
        return V4L2;
    }

    /** Resolves the {@code stream.capture-format} property; {@code auto} (or blank) means {@link #detect}. */
    public static CaptureBackend fromProperty(String value, String osName) {
        if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value.trim())) {
            return detect(osName);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown capture format '" + value + "'; use auto, dshow, avfoundation, v4l2 or test");
        }
    }

    /** FFmpeg input options for this backend. */
    public List<String> inputArgs(String device, int fps, String videoSize) {
        String rate = Integer.toString(fps);
        return switch (this) {
            case DSHOW -> List.of("-f", "dshow", "-rtbufsize", "100M",
                    "-framerate", rate, "-video_size", videoSize, "-i", "video=" + device);
            case AVFOUNDATION -> List.of("-f", "avfoundation",
                    "-framerate", rate, "-video_size", videoSize, "-pixel_format", "uyvy422", "-i", device);
            case V4L2 -> List.of("-f", "v4l2",
                    "-framerate", rate, "-video_size", videoSize, "-i", device);
            // -re paces the synthetic source at real time, as a camera would
            case TEST -> List.of("-re", "-f", "lavfi", "-i", "testsrc2=size=" + videoSize + ":rate=" + rate);
        };
    }
}
