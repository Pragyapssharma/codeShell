import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
	
	private static String currentDirectory = System.getProperty("user.dir"); // Track manually
	final static PrintStream stdOut = System.out;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        Set<String> builtins = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "ls", "help"));
        

        while (true) {
        	stdOut.print("$ ");
//            System.out.print("$ "); // Shell prompt
            String input = scanner.nextLine().trim();
            
            if ("exit 0".equalsIgnoreCase(input)) {
                scanner.close();
                System.exit(0);
            }
            
            executeCommand(input);
     /*       
            if (input.contains(" > ")) {
                // redirect output stream
            	 String[] parts = input.split(">", 2);
            	 String command = parts[0].trim();
            	 if (command.matches(".*\\d+$")) {
            	        command = command.replaceAll("\\d+$", "").trim();
            	    }
            	    String outputFile = parts[1].trim().replaceAll("^['\"]|['\"]$", "");

                try {
                    File logFile = new File(outputFile);
                    File path = logFile.getParentFile();
                    if (!(path.exists())) {
                        path.mkdirs();
                    } else if (logFile.exists()) {
                        logFile.delete();
                    }
                    logFile.createNewFile();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos);
                    PrintStream oldOut = System.out;
                    System.setOut(ps);

                    executeCommand(command);

                    System.out.flush();
                    System.setOut(oldOut);
                    FileWriter writer = new FileWriter(logFile);
                    writer.write(baos.toString());
                    writer.close();
                } catch (Exception e) {
                    System.out.println("Error executing command: " + e.getMessage());
                }
            }
            else {
                executeCommand(input);
            }
            
            input = handleRedirection(input);

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
//            if ("ls".equalsIgnoreCase(input)) {
//            	executeLsCommand(input);
//                continue;
//            }
//
//            if ("help".equalsIgnoreCase(input)) {
//                System.out.println("Available commands: " + builtins);
//                continue;
//            }
//
//            executeExternalProgram(input);
            
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
        String[] parts;
        if (input.contains(" 1> ")) {
            parts = input.split(" 1> ");
        } else {
            parts = input.split(" > ");
        }
        String command = parts[0].trim();
        String outputFile = parts[1].trim().replaceAll("^['\"]|['\"]$", "");

        try (FileWriter writer = new FileWriter(outputFile)) {
            if (command.startsWith("echo ")) {
                writer.write(handleEcho(command.substring(5).trim()) + "\n");
            } else if (command.startsWith("cat ")) {
                handleCatForRedirection(command.substring(4).trim(), writer);
            } else {
                executeExternalProgramForRedirection(command, writer);
            }
        } catch (IOException e) {
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
    
    private static void handleCatForRedirection(String content, FileWriter writer) {
        List<String> fileNames = Arrays.asList(content.split("\\s+"));

        for (String fileName : fileNames) {
            File file = new File(fileName);
            if (!file.exists()) {
                try {
                    writer.write("cat: " + fileName + ": No such file or directory\n");
                } catch (IOException e) {
                    System.out.println("Error writing to file: " + e.getMessage());
                }
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + "\n");
                }
            } catch (IOException e) {
                System.out.println("Error reading file: " + fileName);
            }
        }
    }
    
    private static void executeExternalProgramForRedirection(String input, FileWriter writer) {
        try {
            Process process = Runtime.getRuntime().exec(input);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }

            while ((line = errorReader.readLine()) != null) {
                writer.write(line + "\n");
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            try {
                writer.write("Error executing command: " + e.getMessage() + "\n");
            } catch (IOException ex) {
                System.out.println("Error writing to file: " + ex.getMessage());
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
    
    private static String handleRedirection(String input) {
        if (input.contains(" 1> ") || input.contains(" > ")) {
            // Redirect output stream
            String[] arr = input.split("( 1> )|( > )");
            File logFile = new File(arr[arr.length - 1]);
            File path = logFile.getParentFile();

            if (!(path.exists())) {
                path.mkdirs();
            } else if (logFile.exists()) {
                logFile.delete();
            }
            
            try {
                logFile.createNewFile();
                System.setOut(new PrintStream(logFile));
            } catch (IOException e) {
                System.out.println("Error: Could not create file for redirection.");
            }

            return arr[0];
        } else {
            System.setOut(stdOut);
            return input;
        }
    }

    private static void executeCommand(String input) {
        if (input.contains(">") || input.contains("1>")) {
            executeCommandWithRedirection(input);
        } else if (input.startsWith("echo ")) {
            System.out.println(handleEcho(input.substring(5).trim()));
        } else if (input.startsWith("cat ")) {
            handleCat(input.substring(4).trim());
        } else if (input.startsWith("cd ")) {
            changeDirectory(input.substring(3).trim());
        } else if (input.equals("pwd")) {
            System.out.println(currentDirectory);
        } else if (input.equals("ls")) {
            executeLsCommand(input);
        } else {
            executeExternalProgram(input);
        }
    }

}