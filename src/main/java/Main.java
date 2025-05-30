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
        Matcher matcher = Pattern.compile("'(.*?)'|\\S+").matcher(input);

        while (matcher.find()) {
            args.add(matcher.group().replaceAll("^'|'$", "")); // Remove quotes only from quoted arguments
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
        List<String> extractedWords = new ArrayList<>();
        Matcher matcher = Pattern.compile("'([^']*)'|\\S+").matcher(content);

        StringBuilder concatenated = new StringBuilder();
        boolean lastWasQuoted = false;

        while (matcher.find()) {
            String match = matcher.group(1) != null ? matcher.group(1) : matcher.group();

            if (lastWasQuoted && matcher.group(1) != null) {
                concatenated.append(match); // Merge adjacent quoted words without spaces
            } else {
                if (!concatenated.isEmpty()) {
                    extractedWords.add(concatenated.toString()); // Add merged text
                    concatenated.setLength(0); // Reset buffer
                }
                concatenated.append(match);
            }
            lastWasQuoted = (matcher.group(1) != null);
        }

        if (!concatenated.isEmpty()) {
            extractedWords.add(concatenated.toString());
        }

        System.out.println(String.join(" ", extractedWords));
    }

    private static void handleCat(String content) {
        List<String> fileNames = new ArrayList<>();
        Matcher matcher = Pattern.compile("'([^']*)'|\\S+").matcher(content);

        while (matcher.find()) {
            fileNames.add(matcher.group(1) != null ? matcher.group(1) : matcher.group());
        }

        if (fileNames.isEmpty()) return;

        ProcessBuilder pb = new ProcessBuilder(fileNames);
        try {
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Print file content
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(fileNames.get(0) + ": command not found");
        }
    }

}