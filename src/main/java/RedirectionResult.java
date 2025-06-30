import java.io.File;
import java.util.List;

class RedirectionResult {
    List<String> commandArgs;
    File stdoutFile;
    File stderrFile;
    boolean appendStdout;

    RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile) {
        this.commandArgs = commandArgs;
        this.stdoutFile = stdoutFile;
        this.stderrFile = stderrFile;
        this.appendStdout = false;
    }

    RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile, boolean appendStdout) {
        this.commandArgs = commandArgs;
        this.stdoutFile = stdoutFile;
        this.stderrFile = stderrFile;
        this.appendStdout = appendStdout;
    }
}

