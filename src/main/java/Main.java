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
            
            if (input.contains(">") || input.contains("1>")) {
                executeCommandWithRedirection(input);
            } else {
                executeCommand(input);
            }
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
            System.out.println("Error executing command.");
        }
    }

    
    private static void executeCommandWithRedirection(String input) {
        String[] parts;
        String outputFile;
        String command;

        if (input.contains(" 1> ")) {
            parts = input.split(" 1> ");
            command = parts[0].trim();
            outputFile = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
        } else {
            parts = input.split(" > ", 2);
            command = parts[0].trim();
            outputFile = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
        }

        File logFile = new File(outputFile);
        File path = logFile.getParentFile();
        if (!(path.exists())) {
            path.mkdirs();
        } else if (logFile.exists()) {
            logFile.delete();
        }

        try {
            logFile.createNewFile();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            PrintStream oldOut = System.out;
            System.setOut(ps);

            if (command.startsWith("echo ")) {
                System.out.println(handleEcho(command.substring(5).trim()));
            } else if (command.startsWith("cat ")) {
                try {
                    handleCat(command.substring(4).trim(), new OutputStreamWriter(System.out));
                } catch (IOException e) {
                    System.out.println("Error handling cat command: " + e.getMessage());
                }
            } else if (command.startsWith("ls")) {
                String pathStr;
                if (command.trim().equals("ls")) {
                    pathStr = currentDirectory;
                } else {
                    pathStr = command.replace("ls", "").trim();
                }
                File directory = new File(pathStr);
                if (!directory.isAbsolute()) {
                    directory = new File(currentDirectory, pathStr);
                }
                String[] files = directory.list();
                if (files != null) {
                    Arrays.sort(files);
                    if (command.contains("-1")) {
                        for (String file : files) {
                            System.out.println(file);
                        }
                    } else {
                        for (String file : files) {
                            System.out.print(file + " ");
                        }
                        System.out.println();
                    }
                }
            } else {
                try {
                    Process process = Runtime.getRuntime().exec(command);
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

            System.out.flush();
            System.setOut(oldOut);
            Files.write(logFile.toPath(), baos.toString().getBytes());
        } catch (IOException e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }
    
    private static void executeCommandWithoutRedirection(String input, ByteArrayOutputStream baos) {
        PrintStream oldOut = System.out;
        PrintStream ps = new PrintStream(baos);
        System.setOut(ps);

        if (input.startsWith("echo ")) {
            System.out.println(handleEcho(input.substring(5).trim()));
        } else if (input.startsWith("cat ")) {
            try {
                handleCat(input.substring(4).trim(), new OutputStreamWriter(System.out));
            } catch (IOException e) {
                System.out.println("Error handling cat command: " + e.getMessage());
            }
        } else if (input.startsWith("cd ")) {
            changeDirectory(input.substring(3).trim());
        } else if (input.equals("pwd")) {
            System.out.println(currentDirectory);
        } else if (input.startsWith("ls")) {
            String path;
            if (input.trim().equals("ls")) {
                path = currentDirectory;
            } else {
                path = input.replace("ls", "").trim();
            }
            File directory = new File(path);
            if (!directory.isAbsolute()) {
                directory = new File(currentDirectory, path);
            }
            String[] files = directory.list();
            if (files != null) {
                Arrays.sort(files);
                for (String file : files) {
                    System.out.println(file);
                }
            }
        } else {
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

        System.out.flush();
        System.setOut(oldOut);
    }
    
    private static String getLsOutput(String command) {
        StringBuilder output = new StringBuilder();
        String dirPath = currentDirectory;
        if (command.contains("/")) {
            dirPath = command.substring(command.indexOf("/") + 1).trim();
        } else if (!command.equals("ls") && !command.equals("ls -1")) {
            dirPath = command.substring(3).trim();
        }
        File directory = new File(dirPath);
        String[] files = directory.list();
        if (files != null) {
            Arrays.sort(files);
            if (command.contains("-1")) {
                for (String file : files) {
                    output.append(file).append("\n");
                }
            } else {
                for (String file : files) {
                    output.append(file).append(" ");
                }
                output.append("\n");
            }
        }
        return output.toString();
    }
    
    private static void changeDirectory(String newPath) {
        Path newDirPath = newPath.equals("~") ? Paths.get(System.getenv("HOME")) : Paths.get(currentDirectory, newPath);
        File newDir = newDirPath.toFile();
        if (newDir.exists() && newDir.isDirectory()) {
            currentDirectory = newDirPath.toAbsolutePath().toString();
        } else {
            System.out.println("cd: " + newPath + ": No such file or directory");
        }
    }


    private static String handleEcho(String content) {
        // Remove surrounding quotes if both are the same type (single or double)
        if ((content.startsWith("'") && content.endsWith("'")) || (content.startsWith("\"") && content.endsWith("\""))) {
            content = content.substring(1, content.length() - 1);
        }
        return content;
    }

    private static void handleCat(String content, Writer writer) throws IOException {
        List<String> fileNames = Arrays.asList(content.split("\\s+"));

        for (String fileName : fileNames) {
            File file = new File(fileName);
            if (!file.exists()) {
                writer.write("cat: " + fileName + ": No such file or directory\n");
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + "\n");
                }
            } catch (IOException e) {
                writer.write("Error reading file: " + fileName + "\n");
            }
        }
    }
    
    private static void handleCatForRedirection(String content, PrintWriter writer) {
        List<String> fileNames = Arrays.asList(content.split("\\s+"));

        for (String fileName : fileNames) {
            File file = new File(fileName);
            if (!file.exists()) {
                writer.println("cat: " + fileName + ": No such file or directory");
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);
                }
            } catch (IOException e) {
                writer.println("cat: " + fileName + ": Error reading file");
            }
        }
    }
    
    private static void executeExternalProgramForRedirection(String input, PrintWriter writer) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(input.split("\\s+"));
            
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);
                }

                process.waitFor();
                writer.flush();
            }
        } catch (IOException | InterruptedException e) {
            writer.println("Error executing command: " + e.getMessage());
        }
    }
    
    
    private static void executeLsCommand(String command) {
        String[] parts = command.split(" ");
        if (parts.length < 2) {
            System.out.println("Error: Invalid ls command syntax.");
            return;
        }

        File directory = new File(parts[1].trim()); // Extract directory from `ls` command

        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("Error: Directory not found.");
            return;
        }

        String[] files = directory.list((dir, name) -> new File(dir, name).isFile() && !name.startsWith("."));
        if (files != null) {
            Arrays.sort(files);
            for (String file : files) {
                System.out.println(file);
            }
        } else {
            System.out.println("Error: No files found.");
        }
    }

    
    private static void handleRedirection(String input) {
        String[] parts = input.split("( 1> )|( > )");
        if (parts.length < 2) {
            System.out.println("Error: Invalid redirection syntax.");
            return;
        }

        String command = parts[0].trim();
        String outputFile = parts[1].trim().replaceAll("^['\"]|['\"]$", "");

        File file = new File(outputFile);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try (PrintStream fileOut = new PrintStream(new FileOutputStream(file))) {
            System.setOut(fileOut);
            processCommand(command);
        } catch (IOException e) {
            System.out.println("Error: Unable to redirect output.");
        } finally {
            System.setOut(stdOut);
        }
    }

    private static void processCommand(String input) {
        if (input.startsWith("echo ")) {
            System.out.println(input.substring(5).trim());
        } else if (input.startsWith("cat ")) {
            handleCat(input.substring(4).trim());
        } else if (input.startsWith("cd ")) {
            changeDirectory(input.substring(3).trim());
        } else if ("pwd".equalsIgnoreCase(input)) {
            System.out.println(currentDirectory);
        } else if (input.startsWith("ls")) {
            executeLsCommand(input);
        } else {
            executeExternalProgram(input);
        }
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


    private static void executeCommand(String input) {
        if (input.startsWith("echo ")) {
            System.out.println(handleEcho(input.substring(5).trim()));
        } else if (input.startsWith("cat ")) {
            try {
                handleCat(input.substring(4).trim(), new OutputStreamWriter(System.out));
            } catch (IOException e) {
                System.out.println("Error handling cat command: " + e.getMessage());
            }
        } else if (input.startsWith("cd ")) {
            changeDirectory(input.substring(3).trim());
        } else if (input.equals("pwd")) {
            System.out.println(currentDirectory);
        } else if (input.startsWith("ls")) {
            String path;
            if (input.trim().equals("ls")) {
                path = currentDirectory;
            } else {
                path = input.replace("ls", "").trim();
            }
            File directory = new File(path);
            if (!directory.isAbsolute()) {
                directory = new File(currentDirectory, path);
            }
            String[] files = directory.list();
            if (files != null) {
                Arrays.sort(files);
                for (String file : files) {
                    System.out.println(file);
                }
            }
        } else {
            executeExternalProgram(input);
        }
    }


}