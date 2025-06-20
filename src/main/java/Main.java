import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static Path currentDirectory = Paths.get(System.getProperty("user.dir"));
    private static final PrintStream stdOut = System.out;
    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "ls", "help", "cd", "cat"));

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            stdOut.print("$ ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            if ("exit 0".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                scanner.close();
                System.exit(0);
            }

            // Handle output redirection
            if (input.matches(".*\\s(1?>|>)\\s.*")) {
                try {
                    executeCommandWithRedirection(input);
                    System.out.flush();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Command execution was interrupted.");
                }
                continue;
            }

            // Handle cd command separately
            if (handleCd(input)) {
                continue;
            }

            // Handle builtins and external commands
            processCommand(input);
        }
    }

    private static boolean handleCd(String input) {
        if (!input.startsWith("cd")) return false;

        String[] parts = input.split("\\s+", 2);
        Path newDir;
        if (parts.length == 1 || parts[1].equals("~")) {
            newDir = Paths.get(System.getProperty("user.home"));
        } else {
            newDir = currentDirectory.resolve(parts[1]).normalize();
        }

        if (Files.isDirectory(newDir)) {
            currentDirectory = newDir;
        } else {
            System.out.println("cd: no such file or directory: " + parts[1]);
        }
        return true;
    }

    private static void processCommand(String input) {
        if (input.startsWith("echo ")) {
            System.out.println(handleEcho(input.substring(5).trim()));
        } else if (input.startsWith("cat ")) {
            handleCat(input.substring(4).trim());
            System.out.flush();
        } else if ("pwd".equalsIgnoreCase(input)) {
            System.out.println(currentDirectory.toAbsolutePath().normalize());
        } else if (input.startsWith("ls")) {
            executeLsCommand(input);
        } else if (input.startsWith("type ")) {
            String cmd = input.substring(5).trim();
            if (BUILTINS.contains(cmd)) {
                System.out.println(cmd + " is a shell builtin");
            } else {
                findExecutable(cmd);
            }
        } else if ("help".equalsIgnoreCase(input)) {
            System.out.println("Available commands: " + BUILTINS);
        } else {
            executeExternalProgram(input);
        }
    }

    private static String handleEcho(String content) {
        if ((content.startsWith("\"") && content.endsWith("\"")) ||
            (content.startsWith("'") && content.endsWith("'"))) {
            content = content.substring(1, content.length() - 1);
        }
        return content;
    }

    private static void handleCat(String content) {
        List<String> fileNames = Arrays.asList(content.split("\\s+"));
        for (String fileName : fileNames) {
            Path file = currentDirectory.resolve(fileName);
            if (!Files.exists(file)) {
                System.out.println("cat: " + fileName + ": No such file or directory");
                continue;
            }
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.out.println("Error reading file: " + fileName);
            }
        }
    }

    private static void executeLsCommand(String command) {
        executeLsCommandWithOutput(command, System.out);
    }



    private static void findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            System.out.println(command + ": not found");
            return;
        }

        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path filePath = Paths.get(dir, command);
            if (Files.isExecutable(filePath) && !Files.isDirectory(filePath)) {
                System.out.println(command + " is " + filePath.toString());
                return;
            }
        }
        System.out.println(command + ": not found");
    }

    private static void executeExternalProgram(String input) {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", input);
            builder.directory(currentDirectory.toFile());
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }

    private static void executeCommandWithRedirection(String input) throws InterruptedException {
    	Pattern pattern = Pattern.compile("^(.*?)\\s*(?:1?>|>)\\s*(.*?)$");
        Matcher matcher = pattern.matcher(input);

        if (!matcher.matches()) {
            System.out.println("Invalid redirection syntax.");
            return;
        }

        String command = matcher.group(1).trim();
        String outputFile = matcher.group(2).trim().replaceAll("^['\"]|['\"]$", "");

        File file = new File(outputFile);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
        	if (command.equals("cat") || command.startsWith("cat ")) {
                String args = command.length() == 3 ? "" : command.substring(4).trim();
                handleCatForRedirection(args, writer);
                writer.flush();
                return;
            }
            // Handle echo command including exact "echo" with no args
            if (command.equals("echo") || command.startsWith("echo ")) {
                String echoContent = command.length() == 4 ? "" : handleEcho(command.substring(5).trim());
                writer.println(echoContent);
                writer.flush();
                return;
            }

            // For other commands, fallback to ProcessBuilder
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            builder.directory(currentDirectory.toFile());
            builder.redirectOutput(file);
            builder.redirectError(ProcessBuilder.Redirect.appendTo(file));  // append errors to same file

            Process process = builder.start();
            process.waitFor();

        } catch (IOException e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }

    private static void handleCatForRedirection(String content, PrintWriter writer) {
        if (content.isEmpty()) return;

        List<String> fileNames = Arrays.asList(content.split("\\s+"));

        for (String fileName : fileNames) {
            Path filePath = Paths.get(fileName);
            if (!filePath.isAbsolute()) {
                filePath = currentDirectory.resolve(fileName);
            }

            if (!Files.exists(filePath)) {
                writer.println("cat: " + fileName + ": No such file or directory");
                continue;
            }

            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);
                }
            } catch (IOException e) {
                writer.println("cat: " + fileName + ": Error reading file");
            }
        }

        writer.flush();
    }





    private static void executeLsCommandWithOutput(String command, PrintStream out) {
        String[] tokens = command.trim().split("\\s+");

        boolean singleColumn = false;
        List<String> paths = new ArrayList<>();

        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.equals("-1")) {
                singleColumn = true;
            } else {
                paths.add(token);
            }
        }

        if (paths.isEmpty()) {
            paths.add("."); // default to current directory
        }

        for (String pathStr : paths) {
            Path dir = currentDirectory.resolve(pathStr).normalize();
            if (!Files.isDirectory(dir)) {
                out.println("ls: cannot access '" + pathStr + "': No such directory");
                continue;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                List<String> files = new ArrayList<>();
                for (Path entry : stream) {
                    files.add(entry.getFileName().toString());
                }
                Collections.sort(files);
                for (String f : files) {
                    if (singleColumn) {
                        out.println(f);
                    } else {
                        out.print(f + " ");
                    }
                }
                if (!singleColumn) {
                    out.println();
                }
            } catch (IOException e) {
                out.println("ls: error reading directory: " + e.getMessage());
            }
        }
    }

}
