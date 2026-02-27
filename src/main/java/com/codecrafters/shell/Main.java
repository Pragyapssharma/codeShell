package com.codecrafters.shell;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {

    private static final List<String> BUILTINS = Arrays.asList("echo", "exit", "type", "pwd", "cd", "ls", "help");
    private static StringBuilder currentLine = new StringBuilder();

    public static void main(String[] args) {
        try {
            while (true) {
                // Display prompt
                System.out.print("$ ");
                System.out.flush();
                
                // Read input character by character
                currentLine = new StringBuilder();
                
                while (true) {
                    int ch = System.in.read();
                    
                    if (ch == -1) {
                        return; // EOF
                    }
                    
                    if (ch == '\t') {
                        // Handle tab completion
                        handleTabCompletion();
                        continue;
                    }
                    
                    if (ch == '\n' || ch == '\r') {
                        System.out.println();
                        break;
                    }
                    
                    // Handle backspace
                    if (ch == 127 || ch == '\b') {
                        if (currentLine.length() > 0) {
                            currentLine.deleteCharAt(currentLine.length() - 1);
                            // Move cursor back, overwrite with space, move back again
                            System.out.print("\b \b");
                            System.out.flush();
                        }
                        continue;
                    }
                    
                    // Regular character
                    currentLine.append((char) ch);
                    System.out.print((char) ch);
                    System.out.flush();
                }
                
                String rawInput = currentLine.toString().trim();
                
                if (rawInput.isEmpty()) {
                    continue;
                }
                
                // Handle exit command
                if (rawInput.equals("exit")) {
                    System.exit(0);
                }
                
                // Process the command
                processCommand(rawInput);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void handleTabCompletion() {
        String currentText = currentLine.toString();
        String trimmed = currentText.trim();
        
        // Don't complete if there are spaces (only complete the first word)
        if (trimmed.contains(" ")) {
            return;
        }
        
        // Find matching builtin commands
        List<String> matches = new ArrayList<>();
        for (String cmd : BUILTINS) {
            if (cmd.startsWith(trimmed) && !cmd.equals(trimmed)) {
                matches.add(cmd);
            }
        }
        
        // If exactly one match, complete it
        if (matches.size() == 1) {
            String completion = matches.get(0) + " ";
            
            // Clear the current line
            for (int i = 0; i < currentLine.length(); i++) {
                System.out.print("\b \b");
            }
            
            // Update currentLine and display
            currentLine = new StringBuilder(completion);
            System.out.print("$ " + completion);
            System.out.flush();
        }
    }
    
    private static void processCommand(String input) {
        try {
            List<String> tokens = tokenize(input);
            if (tokens.isEmpty()) {
                return;
            }
            
            String command = tokens.get(0);
            List<String> args = tokens.subList(1, tokens.size());
            
            switch (command) {
                case "echo":
                    handleEcho(args);
                    break;
                case "type":
                    handleType(args);
                    break;
                case "pwd":
                    handlePwd();
                    break;
                case "cd":
                    handleCd(args);
                    break;
                case "ls":
                    handleLs(args);
                    break;
                case "help":
                    handleHelp();
                    break;
                default:
                    executeExternalCommand(command, args);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error processing command: " + e.getMessage());
        }
    }
    
    private static void handleEcho(List<String> args) {
        if (args.isEmpty()) {
            System.out.println();
        } else {
            System.out.println(String.join(" ", args));
        }
    }
    
    private static void handleType(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("type: missing operand");
            return;
        }
        
        String cmd = args.get(0);
        
        if (BUILTINS.contains(cmd)) {
            System.out.printf("%s is a shell builtin\n", cmd);
        } else {
            String path = findInPath(cmd);
            if (path != null) {
                System.out.printf("%s is %s\n", cmd, path);
            } else {
                System.out.printf("%s: not found\n", cmd);
            }
        }
    }
    
    private static void handlePwd() {
        System.out.println(System.getProperty("user.dir"));
    }
    
    private static void handleCd(List<String> args) {
        if (args.isEmpty()) {
            return;
        }
        
        String path = args.get(0);
        Path newPath;
        
        if (path.startsWith("~")) {
            String home = System.getenv("HOME");
            if (home == null) {
                home = System.getenv("USERPROFILE");
            }
            if (path.length() == 1) {
                newPath = Paths.get(home);
            } else {
                newPath = Paths.get(home, path.substring(2));
            }
        } else {
            newPath = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }
        
        if (Files.isDirectory(newPath)) {
            System.setProperty("user.dir", newPath.toString());
        } else {
            System.out.printf("cd: %s: No such file or directory\n", path);
        }
    }
    
    private static void handleLs(List<String> args) {
        String path = args.isEmpty() ? "." : args.get(0);
        Path dir = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        
        if (!Files.exists(dir)) {
            System.out.printf("ls: %s: No such file or directory\n", path);
            return;
        }
        
        if (!Files.isDirectory(dir)) {
            System.out.printf("ls: %s: Not a directory\n", path);
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                System.out.println(entry.getFileName());
            }
        } catch (IOException e) {
            System.out.println("ls: error reading directory");
        }
    }
    
    private static void handleHelp() {
        System.out.println("Available commands:");
        System.out.println("  exit       Exit the shell");
        System.out.println("  echo       Print arguments");
        System.out.println("  type       Display command type");
        System.out.println("  pwd        Show current directory");
        System.out.println("  cd         Change directory");
        System.out.println("  ls         List directory contents");
        System.out.println("  help       Display this help message");
    }
    
    private static void executeExternalCommand(String command, List<String> args) {
        try {
            String executable = findInPath(command);
            if (executable == null) {
                System.out.printf("%s: command not found\n", command);
                return;
            }
            
            ProcessBuilder pb = new ProcessBuilder(new ArrayList<>() {{
                add(executable);
                addAll(args);
            }});
            
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
            
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
        }
    }
    
    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        
        String[] dirs = pathEnv.split(File.pathSeparator);
        
        for (String dir : dirs) {
            Path filePath = Paths.get(dir, command);
            if (Files.isExecutable(filePath)) {
                return filePath.toString();
            }
            
            // Check for .exe on Windows
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Path exePath = Paths.get(dir, command + ".exe");
                if (Files.isExecutable(exePath)) {
                    return exePath.toString();
                }
            }
        }
        
        return null;
    }
    
    private static List<String> tokenize(String input) {
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
            
            if (c == '\\' && !inSingleQuote) {
                if (inDoubleQuote) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\\' || next == '"' || next == '$') {
                            i++;
                            current.append(next);
                        } else {
                            current.append(c);
                        }
                    } else {
                        current.append(c);
                    }
                } else {
                    escaping = true;
                }
                continue;
            }
            
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            
            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        
        return tokens;
    }
}