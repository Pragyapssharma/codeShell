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
                    continue;
                }
                findExecutable(command);
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

            // Handle external program execution
            executeExternalProgram(input);
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
        List<String> args = new ArrayList<>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        StringBuilder currentArg = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                }
            } else {
                currentArg.append(c);
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        if (args.isEmpty()) return;

        ProcessBuilder pb = new ProcessBuilder(args);
        
        try {
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Print output from external program
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(args.get(0) + ": command not found");
        }
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
    
    private static void handleEcho(String content) {
        StringBuilder result = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (char c : content.toCharArray()) {
            if (c == '\'' && !inDoubleQuote) {
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

        System.out.println(result.toString());
    }

    private static void handleCat(String content) {
        List<String> fileNames = new ArrayList<>();
        StringBuilder currentFileName = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (char c : content.toCharArray()) {
            if (c == '\'' && !inDoubleQuote) {
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

}