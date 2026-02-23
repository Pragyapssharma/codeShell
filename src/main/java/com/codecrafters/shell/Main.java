package com.codecrafters.shell;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jline.reader.*;
import org.jline.utils.*;
import org.jline.terminal.*;
import org.jline.terminal.impl.*;

public class Main {

    public static void main(String[] args) {
        try {
            // Setup terminal
            System.err.flush();
            System.out.flush();
            System.out.print("$ ");
            System.out.flush();

            List<String> commands = new ArrayList<>();
            commands.add("echo");
            commands.add("exit");
            
            // Create a custom completer for 'echo' and 'exit'
            Completer completer = (lineReader, parsedLine, candidates) -> {
                String buffer = parsedLine.line().trim();  // Get the entire line
                
                // Check for matches with builtin commands
                if ("echo".startsWith(buffer) && !buffer.isEmpty()) {
                    // Add a trailing space after the completed command
                    candidates.add(new Candidate("echo ", "echo", null, null, null, null, true));
                } else if ("exit".startsWith(buffer) && !buffer.isEmpty()) {
                    candidates.add(new Candidate("exit ", "exit", null, null, null, null, true));
                }
            };

            // Terminal setup
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)  // Use the completer variable directly
                    .build();

            final PrintStream stdout = System.out;
            final PrintStream stderr = System.err;

            while (true) {
                try {
                    System.setOut(stdout);
                    System.setErr(stderr);

                    String rawInput = lineReader.readLine("$ ");
                    if (rawInput == null) {
                        break;  // Exit on EOF (Ctrl-D)
                    }

                    rawInput = rawInput.trim();  // Remove leading/trailing spaces
                    if (rawInput.isEmpty()) {
                        continue;  // Skip empty inputs
                    }

                    List<String> tokens = tokenize(rawInput);
                    if (tokens.isEmpty()) {
                        continue;
                    }
                    
                    if (rawInput.equals("exit")) {
                        break;  // Exit the loop if "exit" is typed
                    }
                    
                    System.out.println("You typed: " + rawInput);

                    // Parse redirection (if any)
                    RedirectionResult redir = parseCommandWithRedirection(tokens);

                    // Set redirection (stderr, stdout) if needed
                    handleRedirection(redir);

                    List<String> commandArgs = redir.commandArgs;
                    if (commandArgs.isEmpty()) {
                        continue;
                    }

                    String command = commandArgs.get(0);
                    String argsCleaned = String.join(" ", commandArgs);

                    // Handle specific commands
                    switch (command) {
                        case "exit":
                            System.exit(0);
                        case "echo":
                            handleEcho(commandArgs);
                            break;
                        case "type":
                            type(argsCleaned);
                            break;
                        case "pwd":
                            System.out.println(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
                            break;
                        case "cd":
                            handleCd(commandArgs);
                            break;
                        case "ls":
                            lsCommand(argsCleaned);
                            break;
                        case "help":
                            helpCommand();
                            break;
                        default:
                            commandExec(redir);
                            break;
                    }

                    // Always print prompt after command finishes
                    System.setOut(stdout);
                    System.setErr(stderr);
                } catch (org.jline.reader.UserInterruptException e) {
                    System.out.println("^C");
                } catch (org.jline.reader.EndOfFileException e) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Rest of your existing methods remain the same...
    static void handleEcho(List<String> commandArgs) {
        if (commandArgs.size() > 1) {
            System.out.println(String.join(" ", commandArgs.subList(1, commandArgs.size())));
        } else {
            System.out.println();
        }
    }

    static void handleCd(List<String> commandArgs) {
        if (commandArgs.size() > 1) {
            changeDirectory(commandArgs.get(1));
        } else {
            System.err.println("cd: missing operand");
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
        String[] PATH = pathEnv.split(System.getProperty("path.separator"));
        String pathextEnv = System.getenv("PATHEXT");
        String[] extensions = pathextEnv != null ? pathextEnv.toLowerCase().split(";") : new String[]{""};

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

    static void lsCommand(String input) {
        List<String> args = tokenize(input);
        List<String> paths = args.size() > 1 ? args.subList(1, args.size()) : List.of();

        Path dir = paths.isEmpty() ? getPath(System.getProperty("user.dir")) : getPath(paths.get(0));
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

    static void changeDirectory(String path) {
        Path resolvedPath;
        if (path.charAt(0) == '~') {
            String home = System.getenv("HOME");
            if (home == null) home = System.getenv("USERPROFILE");
            resolvedPath = Paths.get(home).resolve(path.substring(1).trim()).normalize();
        } else {
            resolvedPath = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }

        if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
            System.setProperty("user.dir", resolvedPath.toString());
        } else {
            System.out.printf("cd: %s: No such file or directory\n", path);
        }
    }

    private static void commandExec(RedirectionResult result) {
        try {
            ProcessBuilder builder = new ProcessBuilder(result.commandArgs);
            builder.directory(new File(System.getProperty("user.dir")));

            if (result.stdoutFile != null) {
                if (result.appendStdout) {
                    builder.redirectOutput(ProcessBuilder.Redirect.appendTo(result.stdoutFile));
                } else {
                    builder.redirectOutput(result.stdoutFile);
                }
            } else {
                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (result.stderrFile != null) {
                if (result.appendStderr) {
                    builder.redirectError(ProcessBuilder.Redirect.appendTo(result.stderrFile));
                } else {
                    builder.redirectError(result.stderrFile);
                }
            } else {
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = builder.start();
            process.waitFor();
        } catch (IOException e) {
            // Typically means command not found
            if (!result.commandArgs.isEmpty()) {
                System.err.printf("%s: command not found\n", result.commandArgs.get(0));
            } else {
                System.err.println("Command not found");
            }
        } catch (Exception e) {
            // Handle other types of errors
            System.err.println("Error executing command: " + e.getMessage());
        }
    }

    static RedirectionResult parseCommandWithRedirection(List<String> tokens) {
        List<String> cmd = new ArrayList<>();
        File stdoutFile = null, stderrFile = null;
        boolean expectRedirectFile = false;
        String redirectType = null;
        boolean appendStdout = false, appendStderr = false;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if ("2>".equals(token)) {
                redirectType = "stderr";
                expectRedirectFile = true;
                appendStdout = false;
            } else if ("2>>".equals(token)) {
                redirectType = "stderr";
                expectRedirectFile = true;
                appendStderr = true;
            } else if ("1>".equals(token) || ">".equals(token)) {
                redirectType = "stdout";
                expectRedirectFile = true;
                appendStdout = false;
            } else if ("1>>".equals(token) || ">>".equals(token)) {
                redirectType = "stdout";
                expectRedirectFile = true;
                appendStdout = true;
            } else if (expectRedirectFile) {
                File f = new File(token);
                if ("stdout".equals(redirectType)) {
                    stdoutFile = f;
                } else if ("stderr".equals(redirectType)) {
                    stderrFile = f;
                }
                expectRedirectFile = false;
            } else {
                cmd.add(token);
            }
        }
        return new RedirectionResult(cmd, stdoutFile, stderrFile, appendStdout, appendStderr);
    }

    static Path getPath(String path) {
        return Paths.get(path);
    }

    static List<String> tokenize(String input) {
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
                        if (next == '\\' || next == '"' || next == '$' || next == '\n') {
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

    static void handleRedirection(RedirectionResult result) {
        if (result.stdoutFile != null) {
            try {
                Files.createDirectories(result.stdoutFile.getParentFile().toPath());
            } catch (IOException e) {
                System.err.println("Error creating directories for stdout redirection");
            }
        }
        if (result.stderrFile != null) {
            try {
                Files.createDirectories(result.stderrFile.getParentFile().toPath());
            } catch (IOException e) {
                System.err.println("Error creating directories for stderr redirection");
            }
        }
    }

    // Class to store information about redirection results
    static class RedirectionResult {
        List<String> commandArgs;
        File stdoutFile;
        File stderrFile;
        boolean appendStdout;
        boolean appendStderr;

        RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile, boolean appendStdout, boolean appendStderr) {
            this.commandArgs = commandArgs;
            this.stdoutFile = stdoutFile;
            this.stderrFile = stderrFile;
            this.appendStdout = appendStdout;
            this.appendStderr = appendStderr;
        }
    }
}