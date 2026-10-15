package com.video.stream.webcam.capture;

import java.io.IOException;
import java.util.List;

/** Starts an external process; abstracted so the stream supervisor can be tested without FFmpeg. */
@FunctionalInterface
public interface ProcessLauncher {

    Process launch(List<String> command) throws IOException;

    /** Runs the command with stderr folded into stdout, so one reader sees everything FFmpeg says. */
    static ProcessLauncher system() {
        return command -> new ProcessBuilder(command).redirectErrorStream(true).start();
    }
}
