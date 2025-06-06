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
            
            if (input.contains(">")) {
                executeCommandWithRedirection(input);
                continue;
            }

            if (input.startsWith("echo ")) {
            	System.out.println(handleEcho(input.substring(5).trim()));
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
            	executeLsCommand(input);
                continue;
            }

            if ("help".equalsIgnoreCase(input)) {
                System.out.println("Available commands: " + builtins);
                continue;
            }

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
    
    private static void executeCommandWithRedirection(String input) {
        String[] parts = input.split(">", 2);
        String command = parts[0].trim();
        String outputFilePart = parts[1].trim();

        // Check if file descriptor is specified
        int fileDescriptor = 1; // Default to stdout
        Pattern pattern = Pattern.compile("^(\\d+)>");
        Matcher matcher = pattern.matcher(command);
        if (matcher.find()) {
            fileDescriptor = Integer.parseInt(matcher.group(1));
            command = command.replaceFirst("^\\d+>", "").trim();
        }

        // Check if output file is specified with quotes
        String outputFile = outputFilePart.replaceAll("^['\"]|['\"]$", "");

        try (FileWriter writer = new FileWriter(outputFile)) {
            Process process;
            if (command.startsWith("echo ")) {
                String echoOutput = command.substring(5).trim();
                echoOutput = echoOutput.replaceAll("^['\"]|['\"]$", "");
                writer.write(echoOutput);
            } else if (command.startsWith("cat ")) {
                String[] fileNames = command.substring(4).trim().split("\\s+");
                for (String fileName : fileNames) {
                    File file = new File(fileName);
                    if (!file.exists()) {
                        if (fileDescriptor == 2) {
                            writer.write("cat: " + fileName + ": No such file or directory\n");
                        } else {
                            System.out.println("cat: " + fileName + ": No such file or directory");
                        }
                        continue;
                    }

                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.write(line + "\n");
                        }
                    } catch (IOException e) {
                        if (fileDescriptor == 2) {
                            writer.write("Error reading file: " + fileName + "\n");
                        } else {
                            System.out.println("Error reading file: " + fileName);
                        }
                    }
                }
            } else {
                process = new ProcessBuilder("sh", "-c", command).start();
                if (fileDescriptor == 1) {
                    // Redirect stdout
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line + "\n");
                    }
                } else if (fileDescriptor == 2) {
                    // Redirect stderr
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line + "\n");
                    }
                } else {
                    // Redirect both stdout and stderr
                    BufferedReader readerOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader readerErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line;
                    while ((line = readerOut.readLine()) != null) {
                        writer.write(line + "\n");
                    }
                    while ((line = readerErr.readLine()) != null) {
                        writer.write(line + "\n");
                    }
                }
                process.waitFor();
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }
    private static void changeDirectory(String newPath) {
        Path newDirPath;

        if ("~".equals(newPath)) {
            String homeDir = System.getenv("HOME");
            newDirPath = homeDir != null ? Paths.get(homeDir) : Paths.get(currentDirectory);
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
        return content.replaceAll("^['\"]|['\"]$", "");
    }


    private static void handleCat(String content) {
        List<String> fileNames = Arrays.asList(content.split("\\s+"));

        for (String fileName : fileNames) {
            File file = new File(fileName);
            if (!file.exists()) {
                System.out.println("cat: " + fileName + ": No such file or directory");
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.out.println("Error reading file: " + fileName);
            }
        }
    }

    
    private static void executeLsCommand(String command) {
        File directory = new File(currentDirectory);
        String[] files = directory.list();
        if (files != null) {
            Arrays.sort(files);
            for (String file : files) {
                System.out.println(file);
            }
        } else {
            System.out.println("Error: Directory not found.");
        }
    }

}