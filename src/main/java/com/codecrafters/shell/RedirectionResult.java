package com.codecrafters.shell;

import java.io.File;
import java.util.List;

class RedirectionResult {
    List<String> commandArgs;
    File stdoutFile;
    File stderrFile;
    boolean appendStdout;
    boolean appendStderr;

    RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile) {
        this.commandArgs = commandArgs;
        this.stdoutFile = stdoutFile;
        this.stderrFile = stderrFile;
        this.appendStdout = false;
        this.appendStderr = false;
    }

    RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile, boolean appendStdout) {
        this.commandArgs = commandArgs;
        this.stdoutFile = stdoutFile;
        this.stderrFile = stderrFile;
        this.appendStdout = appendStdout;
        this.appendStderr = false;
    }

    RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile, boolean appendStdout, boolean appendStderr) {
        this.commandArgs = commandArgs;
        this.stdoutFile = stdoutFile;
        this.stderrFile = stderrFile;
        this.appendStdout = appendStdout;
        this.appendStderr = appendStderr;
    }
}