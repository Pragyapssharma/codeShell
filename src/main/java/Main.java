import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.print("$ ");

        Scanner scanner = new Scanner(System.in);
        final PrintStream stdout = System.out;
        final PrintStream stderr = System.err;

        while (scanner.hasNextLine()) {
            System.setOut(stdout);
            System.setErr(stderr);

            String rawInput = scanner.nextLine();

            if (rawInput.trim().isEmpty()) {
                System.out.print("$ ");
                continue;
            }

            // Parse command and redirection info
            Redirection redir = handleRedirection(rawInput);

            List<String> tokens = tokenize(redir.command);
            if (tokens.isEmpty()) {
                System.out.print("$ ");
                continue;
            }

            String command = tokens.get(0);

            if (redir.stdoutFile != null || redir.stderrFile != null) {
                // For builtins, temporarily redirect System.out and System.err
                if (isBuiltin(command)) {
                    PrintStream originalOut = System.out;
                    PrintStream originalErr = System.err;

                    try (PrintStream psOut = redir.stdoutFile != null
                            ? new PrintStream(Files.newOutputStream(redir.stdoutFile))
                            : null;
                         PrintStream psErr = redir.stderrFile != null
                            ? new PrintStream(Files.newOutputStream(redir.stderrFile))
                            : null) {

                        if (psOut != null) System.setOut(psOut);
                        if (psErr != null) System.setErr(psErr);

                        runCommand(command, tokens, rawInput);

                    } catch (IOException e) {
                        System.err.println("Error opening redirection file: " + e.getMessage());
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }

                } else {
                    // For external commands, use ProcessBuilder redirection
                    commandExecWithRedirection(tokens, redir.stdoutFile, redir.stderrFile);
                }
            } else {
                // No redirection, just run command normally
                runCommand(command, tokens, rawInput);
            }

            System.out.print("$ ");
        }
    }

    // Helper to check if command is builtin
    static boolean isBuiltin(String cmd) {
        return switch (cmd) {
            case "exit", "echo", "type", "pwd", "cd", "help", "ls" -> true;
            default -> false;
        };
    }

    static void runCommand(String command, List<String> tokens, String input) {
        switch (command) {
            case "exit" -> System.exit(0);
            case "echo" -> {
                if (tokens.size() > 1) {
                    System.out.println(String.join(" ", tokens.subList(1, tokens.size())));
                } else {
                    System.out.println();
                }
            }
            case "type" -> type(input);
            case "pwd" -> System.out.println(getPath(System.getProperty("user.dir")).toAbsolutePath().normalize());
            case "cd" -> {
                if (tokens.size() > 1) {
                    changeDirectory(tokens.get(1));
                } else {
                    System.out.println("cd: missing operand");
                }
            }
            case "ls" -> lsCommand(input);
            case "help" -> helpCommand();
            default -> commandExecWithRedirection(tokens, null, null);
        }
    }

    static void commandExecWithRedirection(List<String> args, Path stdoutFile, Path stderrFile) {
        try {
            ProcessBuilder builder = new ProcessBuilder(args);
            builder.directory(new File(System.getProperty("user.dir")));

            if (stdoutFile != null) {
                builder.redirectOutput(stdoutFile.toFile());
            }
            if (stderrFile != null) {
                builder.redirectError(stderrFile.toFile());
            }

            Process process = builder.start();

            // If no stdout redirection, print output to System.out
            if (stdoutFile == null) {
                try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }

            // If no stderr redirection, print error to System.err
            if (stderrFile == null) {
                try (BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        System.err.println(line);
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            System.err.printf("%s: command not found\n", String.join(" ", args));
        }
    }

    static void type(String input) {
        String[] builtins = {"exit", "echo", "type", "pwd", "cd", "help", "ls"};

        String[] parts = input.trim().split(" ", 2);
        if (parts.length < 2) {
            System.out.println("type: missing operand");
            return;
        }
        String command = parts[1];

        for (String builtin : builtins) {
            if (builtin.equals(command)) {
                System.out.printf("%s is a shell builtin\n", command);
                return;
            }
        }

        String pathEnv = System.getenv("PATH");
        String pathSeparator = System.getProperty("path.separator");
        String[] PATH = pathEnv.split(pathSeparator);

        String pathextEnv = System.getenv("PATHEXT");
        String[] extensions;
        if (pathextEnv != null && !pathextEnv.isEmpty()) {
            extensions = pathextEnv.toLowerCase().split(";");
        } else {
            extensions = new String[]{""};
        }

        for (String path : PATH) {
            File directory = new File(path);
            if (directory.isDirectory()) {
                for (String ext : extensions) {
                    File file = new File(directory, command + ext);
                    if (file.exists() && file.canExecute()) {
                        System.out.printf("%s is %s\n", command, file.getAbsolutePath());
                        return;
                    }
                }
            }
        }

        System.out.printf("%s: not found\n", command);
    }

    static void changeDirectory(String path) {
        if (path.charAt(0) == '~') {
            String part1 = System.getenv("HOME");
            if (part1 == null) {
                part1 = System.getenv("USERPROFILE");
            }
            String part2 = path.substring(1).trim();
            Path path1 = getPath(part1);
            Path path2 = getPath(part2);
            Path resolvedPath = path1.resolve(path2);

            if (Files.exists(resolvedPath) &&
                    Files.isDirectory(resolvedPath)) {
                System.setProperty("user.dir", resolvedPath.toString());
            } else {
                System.out.printf("cd: %s: No such file or directory\n",
                        path);
            }
        } else {
            Path workingDir = getPath(System.getProperty("user.dir"));
            Path normalizedPath = getPath(path);
            Path resolvedPath = workingDir.resolve(normalizedPath);
            if (Files.exists(resolvedPath) &&
                    Files.isDirectory(resolvedPath)) {
                System.setProperty("user.dir", resolvedPath.toString());
            } else {
                System.out.printf("cd: %s: No such file or directory\n",
                        path);
            }
        }
    }

    static Path getPath(String path) {
        return Paths.get(path);
    }

    public static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\') {
                if (inSingleQuote) {
                    current.append(c);
                } else if (inDoubleQuote) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\\' || next == '"' || next == '$' ||
                                next == '\n') {
                            i++;
                            current.append(input.charAt(i));
                        } else {
                            current.append(c);
                        }
                    } else {
                        current.append(c);
                    }
                } else {
                    escaping = true;
                }
            } else if (c == '\'') {
                if (inDoubleQuote) {
                    current.append(c);
                } else {
                    inSingleQuote = !inSingleQuote;
                }
            } else if (c == '"') {
                if (inSingleQuote) {
                    current.append(c);
                } else {
                    inDoubleQuote = !inDoubleQuote;
                }
            } else if (Character.isWhitespace(c)) {
                if (inSingleQuote || inDoubleQuote) {
                    current.append(c);
                } else {
                    if (!current.isEmpty()) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    public static Redirection handleRedirection(String input) throws IOException {
        Redirection redir = new Redirection(input);

        // Parse stderr redirection " 2> "
        int stderrIndex = input.indexOf(" 2> ");
        if (stderrIndex != -1) {
            String before = input.substring(0, stderrIndex);
            String after = input.substring(stderrIndex + 4).trim();
            redir.command = before.trim();
            redir.stderrFile = getPath(after);
            createFileIfNeeded(redir.stderrFile);
            input = redir.command; // update input for stdout parsing
        }

        // Parse stdout redirection " 1> " or " > "
        int stdoutIndex = input.indexOf(" 1> ");
        int stdoutIndexAlt = input.indexOf(" > ");
        int index = -1;
        if (stdoutIndex != -1) index = stdoutIndex;
        else if (stdoutIndexAlt != -1) index = stdoutIndexAlt;

        if (index != -1) {
            String before = input.substring(0, index);
            String after = input.substring(index + (input.charAt(index + 1) == '1' ? 4 : 3)).trim();
            redir.command = before.trim();
            redir.stdoutFile = getPath(after);
            createFileIfNeeded(redir.stdoutFile);
        }

        return redir;
    }

    private static void createFileIfNeeded(Path file) throws IOException {
        Path parentDir = file.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        if (Files.exists(file)) {
            Files.delete(file);
        }
        Files.createFile(file);
    }

    static void lsCommand(String input) {
        List<String> args = tokenize(input);
        List<String> params = args.size() > 1 ? args.subList(1, args.size()) : List.of();

        List<String> options = new ArrayList<>();
        List<String> paths = new ArrayList<>();

        for (String param : params) {
            if (param.startsWith("-")) {
                options.add(param);
            } else {
                paths.add(param);
            }
        }

        Path dir;
        if (paths.isEmpty()) {
            dir = getPath(System.getProperty("user.dir"));
        } else {
            dir = getPath(paths.get(0));
            if (!dir.isAbsolute()) {
                dir = getPath(System.getProperty("user.dir")).resolve(dir);
            }
        }

        dir = dir.toAbsolutePath().normalize();

        if (!Files.exists(dir)) {
            System.out.printf("ls: %s: No such file or directory\n", paths.isEmpty() ? "" : paths.get(0));
            return;
        }
        if (!Files.isDirectory(dir)) {
            System.out.printf("ls: %s: Not a directory\n", paths.get(0));
            return;
        }

        try {
            Files.list(dir).forEach(path -> System.out.println(path.getFileName()));
        } catch (IOException e) {
            System.out.println("ls: error reading directory");
        }
    }

    static void helpCommand() {
        System.out.println("Available commands:");
        System.out.println("  exit       Exit the shell");
        System.out.println("  echo       Print arguments");
        System.out.println("  type       Display command type");
        System.out.println("  pwd        Show current directory");
        System.out.println("  cd         Change directory");
        System.out.println("  ls         List directory contents");
        System.out.println("  help       Display this help message");
    }

    // Helper class to hold redirection info
    static class Redirection {
        String command;
        Path stdoutFile;
        Path stderrFile;

        Redirection(String cmd) {
            this.command = cmd;
        }
    }
}
