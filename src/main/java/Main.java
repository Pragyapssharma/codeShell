import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Define shell builtins
        Set<String> builtins = new HashSet<>();
        builtins.add("echo");
        builtins.add("exit");
        builtins.add("type");

        while (true) {
            System.out.print("$ "); // Print shell prompt
            String input = scanner.nextLine().trim();

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

            // Handle "type" command
            if (input.startsWith("type ")) {
                String command = input.substring(5).trim();

                // Check if command is a shell builtin
                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                    continue;
                }

                // Search for executable in PATH
                String pathEnv = System.getenv("PATH");
                if (pathEnv != null) {
                    String[] paths = pathEnv.split(":");
                    for (String path : paths) {
                        File file = new File(path, command);
                        if (file.exists() && file.isFile()) {
                            System.out.println(command + " is " + file.getAbsolutePath());
                            continue;
                        }
                    }
                }

                // If not found
                System.out.println(command + ": not found");
                continue;
            }

            // Default case for unknown commands
            System.out.println(input + ": command not found");
        }
    }
}