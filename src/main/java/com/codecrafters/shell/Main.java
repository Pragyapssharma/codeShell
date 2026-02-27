package com.codecrafters.shell;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jline.reader.*;
import org.jline.terminal.*;
import org.jline.reader.impl.*;
import org.jline.reader.impl.completer.*;

public class Main {

    private static final List<String> BUILTINS = Arrays.asList("echo", "exit", "type", "pwd", "cd", "ls", "help");

    public static void main(String[] args) {
        try {
            // Disable JLine logging
            System.setProperty("org.jline.log.level", "OFF");
            
            // Redirect stderr to prevent warnings
            System.setErr(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) { }
                @Override
                public void write(byte[] b, int off, int len) { }
            }));

            // Configure terminal
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(true)
                    .build();

            // Create parser with escape chars disabled
            DefaultParser parser = new DefaultParser();
            parser.setEscapeChars(new char[0]);

            // Create a custom completer that mimics Python's readline behavior
            Completer completer = new Completer() {
                @Override
                public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
                    String buffer = line.line();
                    
                    // Split the line to check if we're at the beginning (typing command)
                    String[] parts = buffer.trim().split("\\s+");
                    
                    // If we're at the beginning of the command (first word or empty line)
                    if (parts.length == 0 || (parts.length == 1 && !buffer.endsWith(" "))) {
                        String textToComplete = parts.length > 0 ? parts[0] : "";
                        
                        // Complete builtin commands that start with the text
                        for (String cmd : BUILTINS) {
                            if (cmd.startsWith(textToComplete) && !textToComplete.equals(cmd)) {
                                // Add space after completion like Python version
                                candidates.add(new Candidate(cmd + " ", cmd, null, null, null, null, true));
                            }
                        }
                    }
                }
            };

            // Build LineReader
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .parser(parser)
                    .option(LineReader.Option.AUTO_FRESH_LINE, false)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .build();

            while (true) {
                try {
                    String rawInput = lineReader.readLine("$ ");
                    
                    if (rawInput == null) {
                        break;
                    }

                    String trimmedInput = rawInput.trim();
                    
                    // Handle exit command
                    if (trimmedInput.equals("exit")) {
                        System.exit(0);
                    }

                    if (trimmedInput.isEmpty()) {
                        continue;
                    }

                    List<String> tokens = tokenize(trimmedInput);
                    if (tokens.isEmpty()) {
                        continue;
                    }

                    RedirectionResult redir = parseCommandWithRedirection(tokens);
                    handleRedirection(redir);

                    List<String> commandArgs = redir.commandArgs;
                    if (commandArgs.isEmpty()) {
                        continue;
                    }

                    String command = commandArgs.get(0);
                    String argsCleaned = String.join(" ", commandArgs);

                    switch (command) {
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
                } catch (UserInterruptException e) {
                    System.out.println("^C");
                } catch (EndOfFileException e) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
            if (!result.commandArgs.isEmpty()) {
                System.err.printf("%s: command not found\n", result.commandArgs.get(0));
            } else {
                System.err.println("Command not found");
            }
        } catch (Exception e) {
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
                appendStderr = false;
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
                // Silent fail
            }
        }
        if (result.stderrFile != null) {
            try {
                Files.createDirectories(result.stderrFile.getParentFile().toPath());
            } catch (IOException e) {
                // Silent fail
            }
        }
    }
}