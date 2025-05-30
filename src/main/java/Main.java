import java.util.*;
import java.io.*;
import java.nio.file.*;

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
                System.out.println(input.substring(5));
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
            	System.out.println(Paths.get(currentDirectory).toAbsolutePath()); // Use manually tracked directory
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
        String[] parts = input.split("\\s+");
        ProcessBuilder pb = new ProcessBuilder(parts); // Correct initialization

        try {
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Print output from external program
            }

            process.waitFor(); // Ensure program execution completes before proceeding
        } catch (IOException | InterruptedException e) {
            System.out.println(parts[0] + ": command not found");
        }
    }

    private static void changeDirectory(String newPath) {
        Path newDirPath = Paths.get(currentDirectory, newPath).normalize(); // Normalize relative paths
        File newDir = newDirPath.toFile(); // Convert Path to File

        if (newDir.exists() && newDir.isDirectory()) {
            currentDirectory = newDirPath.toAbsolutePath().toString(); // Update manually
        } else {
            System.out.println("cd: " + newPath + ": No such file or directory");
        }
    }

}