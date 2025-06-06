import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
	
	private static String currentDirectory = System.getProperty("user.dir"); // Track manually
	
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        Set<String> builtins = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "ls", "help"));
        

        while (true) {
            System.out.print("$ "); // Shell prompt
            String input = scanner.nextLine().trim();
            
            if ("exit 0".equalsIgnoreCase(input)) {
                scanner.close();
                System.exit(0);
            }
            
            if (input.contains(">") || input.contains("1>")) {
                executeCommandWithRedirection(input);
                continue;
            }

            if (input.startsWith("echo ")) {
                handleEcho(input.substring(5).trim());
                continue;
            }
            
            if (input.startsWith("cat ")) {
                handleCat(input.substring(4).trim());
                continue;
            }

            if (input.startsWith("type ")) {
                String command = input.substring(5).trim();
                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    findExecutable(command);
                }
                continue;
            }
            
            if (input.startsWith("cd ")) {
                String path = input.substring(3).trim();
                changeDirectory(path);
                continue;
            }

            if ("pwd".equalsIgnoreCase(input)) {
            	System.out.println(currentDirectory); // Use manually tracked directory
                continue;
            }

/*
            if ("pwd".equalsIgnoreCase(input)) {
            	System.out.println(System.getProperty("user.dir"));
                continue;
            }
*/
            if ("ls".equalsIgnoreCase(input)) {
                System.out.println("file1.txt  file2.txt  folder1/");
                continue;
            }

            if ("help".equalsIgnoreCase(input)) {
                System.out.println("Available commands: " + builtins);
                continue;
            }

            // Handle external program
            if (input.contains(">") || input.contains("1>")) {
                executeCommandWithRedirection(input);
                continue;
            } else if (input.startsWith("echo ")) {
                handleEcho(input.substring(5).trim());
            } else if (input.startsWith("cat ")) {
                handleCat(input.substring(4).trim());
            } else if (input.startsWith("type ")) {
                String command = input.substring(5).trim();
                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    findExecutable(command);
                }
            } else if (input.startsWith("cd ")) {
                String path = input.substring(3).trim();
                changeDirectory(path);
            } else {
                executeExternalProgram(input);
            }
            
        }
    }

    private static void findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            System.out.println(command + ": not found");
            return;
        }

        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path filePath = Paths.get(dir, command);
            if (Files.isExecutable(filePath)) {
                System.out.println(command + " is " + filePath);
                return;
            }
        }
        System.out.println(command + ": not found");
    }

    private static void executeExternalProgram(String input) {
        String command = input;
        String outputFile = null;

        if (input.contains(">")) {
            String[] parts = input.split(">");
            command = parts[0].trim();
            outputFile = parts[1].trim();
        } else if (input.contains("1>")) {
            String[] parts = input.split("1>");
            command = parts[0].trim();
            outputFile = parts[1].trim();
        }

        if (command.startsWith("echo ")) {
            String echoOutput = getEchoOutput(command.substring(5).trim());
            if (outputFile != null) {
                try (FileWriter writer = new FileWriter(outputFile)) {
                    writer.write(echoOutput);
                } catch (IOException e) {
                    System.out.println("Error writing to file");
                }
            } else {
                System.out.println(echoOutput);
            }
        } else {
        	try {
                Process process = Runtime.getRuntime().exec(input);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                System.out.println("Error executing command");
            }
        }
    }
    
    private static void executeCommand(String input, Set<String> builtins) {
        if (input.startsWith("echo ")) {
            handleEcho(input.substring(5).trim());
        } else if (input.startsWith("cd ")) {
            String path = input.substring(3).trim();
            changeDirectory(path);
        } else if ("pwd".equalsIgnoreCase(input)) {
            System.out.println(currentDirectory);
        } else if ("ls".equalsIgnoreCase(input)) {
            System.out.println("file1.txt  file2.txt  folder1/");
        } else if ("help".equalsIgnoreCase(input)) {
            System.out.println("Available commands: [exit, help, ls, echo, type, pwd, cd]");
        } else if (input.startsWith("type ")) {
            String command = input.substring(5).trim();
            if (builtins.contains(command)) {
                System.out.println(command + " is a shell builtin");
            } else {
                findExecutable(command);
            }
        } else if (input.startsWith("cat ")) {
            handleCat(input.substring(4).trim());
        } else {
            System.out.println("Error executing command");
        }
    }

    private static void executeCommandWithRedirection(String input) {
        String command = input;
        String outputFile = null;
        String redirectOperator = null;

        if (input.contains(">")) {
            String[] parts = input.split(">", 2);
            command = parts[0].trim();
            outputFile = parts[1].trim();
            redirectOperator = ">";
        } else if (input.contains("1>")) {
            String[] parts = input.split("1>", 2);
            command = parts[0].trim();
            outputFile = parts[1].trim();
            redirectOperator = "1>";
        }

        try (FileWriter writer = new FileWriter(outputFile)) {
            if (command.startsWith("echo ")) {
                String echoOutput = getEchoOutput(command.substring(5).trim());
                writer.write(echoOutput);
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);

                PrintStream oldOut = System.out;
                System.setOut(ps);

                if (command.startsWith("ls")) {
                    executeLsCommand(command);
                } else {
                    executeExternalProgram(command);
                }

                System.out.flush();
                System.setOut(oldOut);

                writer.write(baos.toString().trim());
            }
        } catch (IOException e) {
            System.out.println("Error writing to file");
        }
    }

    private static String getEchoOutput(String content) {
        StringBuilder result = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escapeNext = false;

        for (char c : content.toCharArray()) {
            if (escapeNext) {
                result.append(c);
                escapeNext = false;
            } else if (c == '\\' && !inSingleQuote) {
                escapeNext = true;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (result.length() > 0 && !Character.isWhitespace(result.charAt(result.length() - 1))) {
                    result.append(' ');
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static void changeDirectory(String newPath) {
        Path newDirPath;

        // Handle the '~' character as the home directory
        if (newPath.equals("~")) {
            String homeDir = System.getenv("HOME");
            if (homeDir != null) {
                newDirPath = Paths.get(homeDir);
            } else {
                System.out.println("cd: HOME environment variable is not set");
                return;
            }
        } else if (newPath.startsWith("/")) {
            newDirPath = Paths.get(newPath); // Absolute path
        } else {
            newDirPath = Paths.get(currentDirectory, newPath).normalize(); // Relative path
        }

        File newDir = newDirPath.toFile();

        if (newDir.exists() && newDir.isDirectory()) {
            currentDirectory = newDirPath.toAbsolutePath().toString();
        } else {
            System.out.println("cd: " + newPath + ": No such file or directory");
        }
    }
    
    private static String handleEcho(String content) {
        StringBuilder result = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escapeNext = false;

        for (char c : content.toCharArray()) {
            if (escapeNext) {
                result.append(c);
                escapeNext = false;
            } else if (c == '\\' && !inSingleQuote) {
                escapeNext = true;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (result.length() > 0 && !Character.isWhitespace(result.charAt(result.length() - 1))) {
                    result.append(' ');
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static void handleCat(String content) {
        List<String> fileNames = new ArrayList<>();
        StringBuilder currentFileName = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escapeNext = false;

        for (char c : content.toCharArray()) {
            if (escapeNext) {
                currentFileName.append(c);
                escapeNext = false;
            } else if (c == '\\' && !inSingleQuote && !inDoubleQuote) {
                escapeNext = true;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (currentFileName.length() > 0) {
                    fileNames.add(currentFileName.toString());
                    currentFileName.setLength(0);
                }
            } else {
                currentFileName.append(c);
            }
        }

        if (currentFileName.length() > 0) {
            fileNames.add(currentFileName.toString());
        }

        if (fileNames.isEmpty()) return;

        ProcessBuilder pb = new ProcessBuilder("cat");
        pb.command().addAll(fileNames);

        try {
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Print file content
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println("Error reading files");
        }
    }
    
    private static void executeLsCommand(String command) {
        String[] parts = command.split("\\s+");
        File directory = new File(currentDirectory);
        if (parts.length > 1) {
            List<String> options = new ArrayList<>();
            List<String> directories = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].startsWith("-")) {
                    options.add(parts[i]);
                } else {
                    directories.add(parts[i]);
                }
            }
            if (!directories.isEmpty()) {
                directory = new File(directories.get(0));
            }
            if (directory.exists() && directory.isDirectory()) {
                String[] files = directory.list();
                if (files != null) {
                    Arrays.sort(files); // sort the files
                    for (String file : files) {
                        System.out.println(file);
                    }
                }
            } else {
                System.out.println("ls: cannot access '" + directory.getAbsolutePath() + "': No such file or directory");
            }
        } else {
            // list current directory
            String[] files = directory.list();
            if (files != null) {
                Arrays.sort(files); // sort the files
                for (String file : files) {
                    System.out.println(file);
                }
            }
        }
    }

}