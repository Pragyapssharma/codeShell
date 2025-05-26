import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) { // Infinite loop to keep reading input
            System.out.print("$ "); // Print the shell prompt

            String input = scanner.nextLine().trim(); // Read user input & remove extra spaces

            // Exit condition
            if ("exit".equalsIgnoreCase(input)) {
                System.out.println("Exiting shell...");
                break; // Stop the loop if user types 'exit'
            }

            // Simulate command execution (currently, all commands are treated as invalid)
            System.out.println(input + ": command not found");
        }

        scanner.close(); // Close the scanner when exiting
    }
}