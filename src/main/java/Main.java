import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static Path currentDirectory = Paths.get(System.getProperty("user.dir"));
    private static final PrintStream stdOut = System.out;
    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "ls", "help", "cd", "cat"));

    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
        	
        	

            if (System.console() != null) {
                stdOut.print("$ ");
                stdOut.flush();
            }

            String input = scanner.nextLine();
            
            if (input == null) break;  // EOF
            input = input.trim();
            
            if (input.isEmpty()) continue;

            if ("exit 0".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                scanner.close();
                System.exit(0);
            }

            // Handle output redirection
            if (input.contains(">")) {
            	try {
                    executeCommandWithRedirection(input);
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
        	if (System.getProperty("os.name").toLowerCase().contains("win")) {
                executeExternalProgram("cmd.exe /c dir " + input.substring(2).trim());
            } else {
                executeLsCommand(input);
            }
        } else if (input.startsWith("type ")) {
            String cmd = input.substring(5).trim();
            if (BUILTINS.contains(cmd)) {
                System.out.println(cmd + " is a shell builtin");
            } else {
                findExecutable(cmd);
            }
        }else if ("help".equalsIgnoreCase(input)) {
            System.out.println("Available commands: " + BUILTINS);
        } else {
            executeExternalProgram(input);
        }
    }

    private static String handleEcho(String content) {
        if (content.startsWith("\"") && content.endsWith("\"")) {
            content = content.substring(1, content.length() - 1);

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '\\' && i + 1 < content.length()) {
                    char next = content.charAt(i + 1);
                    switch (next) {
                        case '\\':
                        case '\'':
                        case '"':
                            result.append(next);
                            i++; // skip escaped char
                            break;
                        default:
                            result.append(c); // keep the backslash
                    }
                } else {
                    result.append(c);
                }
            }
            return result.toString();

        } else if (content.startsWith("'") && content.endsWith("'")) {
            // Single-quoted strings: no escape handling
            return content.substring(1, content.length() - 1);
        }

        return content;
    }




    private static void handleCat(String content) {
        List<String> fileNames = Arrays.asList(content.split("\\s+"));
        for (String fileName : fileNames) {
            Path file = currentDirectory.resolve(fileName).normalize();
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
            	System.out.println("cat: " + fileName + ": Error reading file");
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
        	List<String> cmd = new ArrayList<>();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                cmd.addAll(Arrays.asList("cmd.exe", "/c", input));
            } else {
                cmd.addAll(Arrays.asList("sh", "-c", input));
            }

            ProcessBuilder builder = new ProcessBuilder(cmd);
            
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
        String commandPart = input;
        String stdoutFile = null;
        String stderrFile = null;

        // Extract stderr redirection
        Matcher stderrMatcher = Pattern.compile("^(.*?)(\\s+2>)(\\s*[^>]+)").matcher(commandPart);
        if (stderrMatcher.find()) {
            commandPart = stderrMatcher.group(1).trim();
            stderrFile = stderrMatcher.group(3).trim().replaceAll("^['\"]|['\"]$", "");
        }

        // Extract stdout redirection (1> or >)
        Matcher stdoutMatcher = Pattern.compile("^(.*?)(\\s+(?:1>)|\\s+>)(\\s*[^>]+)").matcher(commandPart);
        if (stdoutMatcher.find()) {
            commandPart = stdoutMatcher.group(1).trim();
            stdoutFile = stdoutMatcher.group(3).trim().replaceAll("^['\"]|['\"]$", "");
        }

        if (stdoutFile == null && stderrFile == null) {
            System.out.println("Invalid redirection syntax.");
            return;
        }

        File stdoutF = stdoutFile != null ? new File(stdoutFile) : null;
        File stderrF = stderrFile != null ? new File(stderrFile) : null;

        if (stdoutF != null && stdoutF.getParentFile() != null) stdoutF.getParentFile().mkdirs();
        if (stderrF != null && stderrF.getParentFile() != null) stderrF.getParentFile().mkdirs();

        try {
            boolean handledManually = false;
            boolean onlyStdout = stdoutF != null && stderrF == null;


            // Manually handle only if both stdout and stderr go to same place (legacy behavior)
            if ((stderrFile == null || stdoutFile != null && stdoutFile.equals(stderrFile))) {
                if (stdoutF != null && stderrF == null) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(stdoutF), true)) {
                    	if (onlyStdout && (commandPart.equals("cat") || commandPart.startsWith("cat "))) {
                            try (PrintWriter writer1 = new PrintWriter(new FileWriter(stdoutF), true)) {
                                handleCatOnlyStdout(commandPart.substring(4).trim(), writer1);
                            }
                            return;

                        }

                        if (commandPart.equals("echo") || commandPart.startsWith("echo ")) {
                            String echoContent = commandPart.length() == 4 ? "" : handleEcho(commandPart.substring(5).trim());
                            writer.println(echoContent);
                            writer.flush();
                            return;
                        }

                        if (commandPart.equals("ls") || commandPart.startsWith("ls ")) {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            PrintStream tempOut = new PrintStream(buffer);
                            executeLsCommandWithOutput(commandPart, tempOut);
                            writer.write(buffer.toString());
                            writer.flush();
                            return;
                        }

                        if (commandPart.startsWith("type ")) {
                            String cmd = commandPart.substring(5).trim();
                            if (BUILTINS.contains(cmd)) {
                                writer.println(cmd + " is a shell builtin");
                            } else {
                                writer.println(cmd + ": not found");
                            }
                            writer.flush();
                            return;
                        }

                        if ("pwd".equals(commandPart)) {
                            writer.println(currentDirectory.toAbsolutePath().normalize());
                            writer.flush();
                            return;
                        }

                        handledManually = true;
                    }
                }
            }

            // Use ProcessBuilder for all other cases
            if (!handledManually) {
                ProcessBuilder builder;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    builder = new ProcessBuilder("cmd.exe", "/c", commandPart);
                } else {
                    builder = new ProcessBuilder("sh", "-c", commandPart);
                }

                builder.directory(currentDirectory.toFile());

                if (stdoutF != null) builder.redirectOutput(stdoutF);
                if (stderrF != null) builder.redirectError(stderrF);

                Process process = builder.start();
                process.waitFor();
            }

        } catch (IOException e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }

    private static void handleCatOnlyStdout(String content, PrintWriter writer) {
        if (content.isEmpty()) return;

        List<String> fileNames = Arrays.asList(content.split("\\s+"));
        for (String fileName : fileNames) {
            Path file = fileName.startsWith("/") ? Paths.get(fileName) : currentDirectory.resolve(fileName);
            file = file.normalize();

            if (!Files.exists(file)) {
                System.err.println("cat: " + fileName + ": No such file or directory");
                continue;
            }

            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);
                }
            } catch (IOException e) {
                System.err.println("cat: " + fileName + ": Error reading file");
            }
        }
    }

    private static void handleCatForRedirection(String content, PrintWriter writer) {
        if (content.isEmpty()) return;

        List<String> fileNames = Arrays.asList(content.split("\\s+"));

        for (String originalName : fileNames) {
        	
        	Path filePath;
        	
        	if (originalName.startsWith("/")) {
        	    filePath = Paths.get(originalName).normalize();
        	} else {
        	    filePath = currentDirectory.resolve(originalName).normalize();
        	}


            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                // Write error to redirected output
            	writer.println("cat: " + originalName + ": No such file or directory");
                continue;
            }

            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);  // Write file content to redirected output
                }
            } catch (IOException e) {
            	writer.println("cat: " + originalName + ": Error reading file");
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
