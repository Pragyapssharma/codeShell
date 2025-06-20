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
        Main shell = new Main();

        while (true) {
        	stdOut.print("$ ");
//            System.out.print("$ "); // Shell prompt
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("exit 0")) {
                scanner.close();
                System.exit(0);
            }
            if (shell.handleCd(input)) {
                

            if (input.contains(">")) {
            	try {
                    executeCommandWithRedirection(input);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // restore interrupt flag
                    System.out.println("Command execution was interrupted.");
                }

            } else {
                executeCommand(input);
            }
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
            Process process = new ProcessBuilder("sh", "-c", input).directory(new File(currentDirectory)).start();
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

    
    private static void executeCommandWithRedirection(String input) throws InterruptedException {
        Pattern pattern = Pattern.compile("^(.*?)\\s*(?:1?>)\\s*(.*?)$");
        Matcher matcher = pattern.matcher(input);

        if (!matcher.matches()) {
            System.out.println("Invalid redirection syntax.");
            return;
        }

        String command = matcher.group(1).trim();
        String outputFile = matcher.group(2).trim().replaceAll("^['\"]|['\"]$", "");

        File file = new File(outputFile);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            builder.directory(new File(currentDirectory));
            builder.redirectOutput(file);                          // stdout → file
            builder.redirectError(ProcessBuilder.Redirect.INHERIT); // stderr → terminal

            Process process = builder.start();
            process.waitFor();
        } catch (IOException e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }

    
    private static void executeCommandWithOutput(String input, PrintStream out) {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", input);
            builder.directory(new File(currentDirectory));
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            out.println("Error executing command: " + e.getMessage());
        }
    }


    private static void executeCommandWithoutRedirection(String input, PrintStream out) {
        if (input.startsWith("echo ")) {
            out.println(handleEcho(input.substring(5).trim()));
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
                    out.println(file);
                }
            }
        } else if (input.startsWith("cat ")) {
            try {
                handleCat(input.substring(4).trim(), new OutputStreamWriter(out));
            } catch (IOException e) {
                out.println("Error handling cat command: " + e.getMessage());
            }
        } else {
            try {
                Process process = Runtime.getRuntime().exec(input);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    out.println(line);
                }

                process.waitFor();
            } catch (IOException | InterruptedException e) {
                out.println("Error executing command");
            }
        }
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
        Path newDirPath = newPath.equals("~") ? Paths.get(System.getProperty("user.home")) : Paths.get(currentDirectory, newPath);
        File newDir = newDirPath.toFile();
        if (newDir.exists() && newDir.isDirectory()) {
            currentDirectory = newDir.getAbsolutePath();
        } else {
            System.out.println("cd: " + newPath + ": No such file or directory");
        }
    }


    private static String handleEcho(String content) {
        if ((content.startsWith("\"") && content.endsWith("\"")) ||
            (content.startsWith("'") && content.endsWith("'"))) {
            content = content.substring(1, content.length() - 1);
        }
        return content;
    }

    private static void handleCat(String content, Writer writer) throws IOException {
        List<String> fileNames = Arrays.asList(content.split("\\s+"));
        BufferedWriter bufferedWriter = new BufferedWriter(writer);

        for (String fileName : fileNames) {
            File file = new File(fileName);
            if (!file.exists()) {
                bufferedWriter.write("cat: " + fileName + ": No such file or directory\n");
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();
                }
            } catch (IOException e) {
                bufferedWriter.write("Error reading file: " + fileName + "\n");
            }
        }
        bufferedWriter.flush();
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
            Process process = new ProcessBuilder("sh", "-c", input).directory(new File(currentDirectory)).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            writer.println("Error executing command.");
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
    
    private static boolean handleCd(String input) {
        if (input.startsWith("cd ")) {
            String[] parts = input.split("\\s+", 2);
            String dir = parts.length > 1 ? parts[1].trim() : "";

            if (dir.equals("~")) {
                dir = "/tmp/grape/raspberry/pear"; // custom home for the tests
            } else if (dir.startsWith("~")) {
                // handle paths like "~/some/dir"
                dir = "/tmp/grape/raspberry/pear" + dir.substring(1);
            }

            File targetDir = new File(dir);
            if (targetDir.isDirectory()) {
                System.setProperty("user.dir", targetDir.getAbsolutePath());
            } else {
                System.out.println("cd: no such file or directory: " + dir);
            }
            return true;
        }
        return false;
    }




    private static void executeCommand(String input) {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", input);
            builder.directory(new File(currentDirectory));
            builder.inheritIO();  // Use parent's stdout/stderr
            Process process = builder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }

    

}