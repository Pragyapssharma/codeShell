import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) { // Infinite loop to keep reading input
            System.out.print("$ "); // Print the shell prompt

            String input = scanner.nextLine().trim(); // Read user input & remove extra spaces

            // Exit condition
            if ("exit 0".equalsIgnoreCase(input)) {
                scanner.close(); // Close resources before exiting
                System.exit(0); // Exit with status 0
            }

            // Handle "echo" command
            if (input.startsWith("echo ")) {
                String echoOutput = input.substring(5); // Extract everything after "echo "
                System.out.println(echoOutput);
                continue; // Skip "command not found" message
            }

            // Default case for unknown commands
            System.out.println(input + ": command not found");
        }

        }
    }
}