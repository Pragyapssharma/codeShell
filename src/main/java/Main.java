import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        
    	System.out.print("$ ");
        
    	Scanner scanner = new Scanner(System.in);
        final PrintStream stdout = System.out;
        
        while (scanner.hasNextLine()) 
        {
            String input = scanner.nextLine();
            input = handleRedirection(input, stdout);
            List<String> tokens = tokenize(input);
            String argsCleaned = String.join(" ", tokens);
            String command = tokens.get(0);

            switch (command) {
                case "exit" -> System.exit(0);
                case "echo" -> System.out.println(argsCleaned.substring(5));
                case "type" -> type(argsCleaned.substring(5).trim());
                case "pwd" -> System.out.println(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize());
                case "cd" -> changeDirectory(tokens);
                case "ls" -> lsCommand(tokens);
                case "cat" -> catCommand(tokens);
                default -> commandExec(tokens, input);
            }
            
            System.setOut(stdout);
            System.out.print("$ ");
        }
    }

    static void type(String command) {
        String[] Builtins = {"exit", "echo", "type", "pwd", "cd", "ls", "cat"};
        for (String validCommand : Builtins) {
            if (validCommand.equals(command)) {
                System.out.printf("%s is a shell builtin\n", command);
                return;
            }
        }
        String[] PATH = System.getenv("PATH").split(File.pathSeparator);
        for (String path : PATH) {
            File[] directory = new File(path).listFiles();
            if (directory != null) {
                for (File file : directory) {
                    if (file.getName().equals(command)) {
                        System.out.printf("%s is %s\n", command, file.getAbsolutePath());
                        return;
                    }
                }
            }
        }
        System.out.printf("%s: not found\n", command);
    }

    static void changeDirectory(List<String> tokens) {
        if (tokens.size() == 1) {
            System.setProperty("user.dir", System.getProperty("user.home"));
        } else {
            String path = tokens.get(1);
            if (path.charAt(0) == '~') {
                String part1 = System.getProperty("user.home");
                String part2 = path.substring(1).trim();
                Path path1 = Paths.get(part1);
                Path path2 = Paths.get(part2);
                Path resolvedPath = path1.resolve(path2);
                if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                    System.setProperty("user.dir", resolvedPath.toString());
                } else {
                    System.out.printf("cd: %s: No such file or directory\n", path);
                }
            } else {
                Path workingDir = Paths.get(System.getProperty("user.dir"));
                Path normalizedPath = Paths.get(path);
                Path resolvedPath = workingDir.resolve(normalizedPath).normalize();
                if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                    System.setProperty("user.dir", resolvedPath.toString());
                } else {
                    System.out.printf("cd: %s: No such file or directory\n", path);
                }
            }
        }
    }

    static void lsCommand(List<String> tokens) {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"));
            if (tokens.size() > 1) {
                dir = Paths.get(tokens.get(1));
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    System.out.println(entry.getFileName());
                }
            }
        } catch (Exception e) {
            System.out.println("ls: error reading directory");
        }
    }

    static void catCommand(List<String> tokens) {
        try {
            if (tokens.size() > 1) {
                for (int i = 1; i < tokens.size(); i++) {
                    Path file = Paths.get(tokens.get(i));
                    if (!Files.exists(file)) {
                        System.out.println("cat: " + tokens.get(i) + ": No such file or directory");
                        continue;
                    }
                    try (BufferedReader reader = Files.newBufferedReader(file)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    } catch (Exception e) {
                        System.out.println("cat: " + tokens.get(i) + ": Error reading file");
                    }
                }
            } else {
                System.out.println("cat: missing file operand");
            }
        } catch (Exception e) {
            System.out.println("cat: error reading file");
        }
    }

    static void commandExec(List<String> tokens, String input) {
        try {
            ProcessBuilder builder = new ProcessBuilder(tokens);
            builder.directory(Paths.get(System.getProperty("user.dir")).toFile());
            Process process = builder.start();
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                System.out.println(line);
            }
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = stderrReader.readLine()) != null) {
                System.err.println(line);
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.printf("%s: command not found\n", input);
        }
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

    public static String handleRedirection(String input, PrintStream stdout) throws IOException {
        if (input.contains(" 1> ") || input.contains(" > ")) {
            String[] parts = input.split("( 1> )|( > )");
            String commandPart = parts[0].trim();
            String outputPathStr = parts[1].trim();
            Path logPath = Paths.get(outputPathStr);
            Path parentDir = logPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            if (Files.exists(logPath)) {
                Files.delete(logPath);
            }
            Files.createFile(logPath);
            System.setOut(new PrintStream(Files.newOutputStream(logPath)));
            return commandPart;
        } else {
            return input;
        }
    }
}