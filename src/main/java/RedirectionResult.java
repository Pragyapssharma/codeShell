import java.io.File;
import java.util.List;

class RedirectionResult {
    List<String> commandArgs;
    File stdoutFile;
    File stderrFile;

    RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile) {
        this.commandArgs = commandArgs;
        this.stdoutFile = stdoutFile;
        this.stderrFile = stderrFile;
    }
}
