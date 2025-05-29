import java.util.*;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Define shell builtins
        Set<String> builtins = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "ls", "help"));

        while (true) {
            System.out.print("$ "); // Print shell prompt
            String input = scanner.nextLine().trim(); // Read user input

            // Handle "exit 0" command
            if ("exit 0".equalsIgnoreCase(input)) {
                scanner.close();
                System.exit(0);
            }

            // Handle "echo" command
            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
                continue;
            }

            // Handle "type" command with PATH lookup
            if (input.startsWith("type ")) {
                String command = input.substring(5).trim();

                // Check if it's a builtin
                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                    continue;
                }

                // Search for the command in PATH directories
                String pathEnv = System.getenv("PATH"); // Get PATH environment variable
                if (pathEnv != null) {
                    String[] paths = pathEnv.split(":"); // Split by colon
                    for (String dir : paths) {
                        File file = new File(dir, command);
                        if (file.exists() && file.isFile()) {
                            System.out.println(command + " is " + file.getAbsolutePath());
                            continue;
                        }
                    }
                }

                // Command not found
                System.out.println(command + ": not found");
                continue;
            }

            // Handle "pwd" command (dummy path)
            if ("pwd".equalsIgnoreCase(input)) {
                System.out.println("/home/user");
                continue;
            }

            // Handle "ls" command (dummy directory listing)
            if ("ls".equalsIgnoreCase(input)) {
                System.out.println("file1.txt  file2.txt  folder1/");
                continue;
            }

            // Handle "help" command
            if ("help".equalsIgnoreCase(input)) {
                System.out.println("Available commands: " + builtins);
                continue;
            }

            // Default case for unknown commands
            System.out.println(input + ": command not found");
        }
    }
}