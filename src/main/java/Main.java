import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;

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

            // Handle "type" command
            if (input.startsWith("type ")) {
                String command = input.substring(5).trim(); // Extract command
                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    System.out.println(command + ": not found");
                }
                continue;
            }

            // Default case for unknown commands
            System.out.println(input + ": command not found");
        }
    }
}