import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
	public static void main(String[] args) throws Exception {

		System.err.flush();
		System.out.flush();
		System.out.print("$ ");
		System.out.flush();

		Scanner scanner = new Scanner(System.in);
		final PrintStream stdout = System.out;
		final PrintStream stderr = System.err;

		while (scanner.hasNextLine()) {
			System.setOut(stdout);
			System.setErr(stderr);
//  		System.out.print("$ ");

			if (!scanner.hasNextLine())
				break;

			String rawInput = scanner.nextLine();

			if (rawInput.trim().isEmpty()) {
//            System.out.print("$ ");
				continue;
			}

			List<String> tokens = tokenize(rawInput);
            if (tokens.isEmpty())
                continue;

            // Parse redirection
            RedirectionResult redir = parseCommandWithRedirection(tokens);

            // Set redirections (stderr, stdout)
            if (redir.stderrFile != null) {
                Files.createDirectories(redir.stderrFile.getParentFile().toPath());
                System.setErr(new PrintStream(new FileOutputStream(redir.stderrFile, redir.appendStderr)));
            }
            if (redir.stdoutFile != null) {
            	Files.createDirectories(redir.stdoutFile.getParentFile().toPath());
                FileOutputStream fos = new FileOutputStream(redir.stdoutFile, redir.appendStdout);
                System.setOut(new PrintStream(fos));
            }

            List<String> commandArgs = redir.commandArgs;
            if (commandArgs.isEmpty()) {
                System.setOut(stdout);
                System.setErr(stderr);
                System.out.print("$ ");
                System.out.flush();
                continue;
            }

            String command = commandArgs.get(0);
            String argsCleaned = String.join(" ", commandArgs);

			switch (command) {
			case "exit" -> System.exit(0);
//			case "echo" -> {
//				String[] echoParts = argsCleaned.split(" ", 2);
//				System.out.println(echoParts.length > 1 ? echoParts[1] : "");
//			}
			 case "echo" -> {
                 if (commandArgs.size() > 1)
                     System.out.println(String.join(" ", commandArgs.subList(1, commandArgs.size())));
                 else
                     System.out.println();
             }
			case "type" -> type(argsCleaned);
			case "pwd" -> System.out.println(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
//			case "cd" -> {
//				String[] cdParts = argsCleaned.split(" ", 2);
//				if (cdParts.length > 1) {
//					changeDirectory(cdParts[1]);
//				} else {
//					System.err.println("cd: missing operand");
//				}
//			}
			case "cd" -> {
                if (commandArgs.size() > 1)
                    changeDirectory(commandArgs.get(1));
                else
                    System.err.println("cd: missing operand");
            }
//			case "ls" -> lsCommand(argsCleaned);
			case "ls" -> commandExec(redir);
			case "help" -> helpCommand();
			default -> commandExec(redir);
			}
 
			// Always print prompt after command finishes
			System.setOut(stdout);
            System.setErr(stderr);
			System.out.print("$ ");
			System.out.flush();
		}
	}
 
	static void type(String input) {
		String[] builtins = { "exit", "echo", "type", "pwd", "cd", "help", "ls", "help" };

		// Check if command provided
		String[] parts = input.trim().split(" ", 2);
		if (parts.length < 2) {
			System.out.println("type: missing operand");
			return;
		}
		String command = parts[1];

		// Check if command is builtin
		for (String builtin : builtins) {
			if (builtin.equals(command)) {
				System.out.printf("%s is a shell builtin\n", command);
				return;
			}
		}

		// Get PATH and split by system separator
		String pathEnv = System.getenv("PATH");
		String pathSeparator = System.getProperty("path.separator"); // ":" on Unix, ";" on Windows
		String[] PATH = pathEnv.split(pathSeparator);

		// Get PATHEXT extensions for Windows executables
		String pathextEnv = System.getenv("PATHEXT");
		String[] extensions;
		if (pathextEnv != null && !pathextEnv.isEmpty()) {
			extensions = pathextEnv.toLowerCase().split(";");
		} else {
			extensions = new String[] { "" }; // fallback for non-Windows
		}

		// Search for executable in each PATH directory with all extensions
		for (String path : PATH) {
			File directory = new File(path);
			if (directory.isDirectory()) {
				for (String ext : extensions) {
					File file = new File(directory, command + ext);
					if (file.exists() && file.canExecute()) {
						System.out.printf("%s is %s\n", command, file.getAbsolutePath());
						return;
					}
				}
			}
		}

		// Not found
		System.out.printf("%s: not found\n", command);
	}

	private static void commandExec(RedirectionResult result) {
        try {
            ProcessBuilder builder = new ProcessBuilder(result.commandArgs);
            builder.directory(new File(System.getProperty("user.dir")));

            if (result.stdoutFile != null) {
            	if (result.appendStdout) {
            		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(result.stdoutFile));
            	} else {
            		builder.redirectOutput(result.stdoutFile);
            	}
            } else {
                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (result.stderrFile != null) {
            	if (result.appendStderr) {
                    builder.redirectError(ProcessBuilder.Redirect.appendTo(result.stderrFile));
                } else {
                    builder.redirectError(result.stderrFile);
                }
            } else {
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = builder.start();
            process.waitFor();
        } catch (IOException e) {
            // Typically means command not found
            if (!result.commandArgs.isEmpty()) {
                System.err.printf("%s: command not found\n", result.commandArgs.get(0));
            } else {
                System.err.println("Command not found");
            }
        } catch (Exception e) {
            // Handle other types of errors
            System.err.println("Error executing command: " + e.getMessage());
        }

    }

    private static RedirectionResult parseCommandWithRedirection(List<String> tokens) {
        List<String> cmd = new ArrayList<>();
        File stdoutFile = null;
        File stderrFile = null;
        boolean expectRedirectFile = false;
        String redirectType = null;
        boolean appendMode = false;
        boolean appendStderr = false;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if ("2>".equals(token)) {
                redirectType = "stderr";
                expectRedirectFile = true;
                appendMode = false;
            } else if ("2>>".equals(token)) {
            	redirectType = "stderr";
                expectRedirectFile = true;
                appendStderr = true;
            } else if ("1>".equals(token) || ">".equals(token)) {
                redirectType = "stdout";
                expectRedirectFile = true;
                appendMode = false;
            } else if ("1>>".equals(token) || ">>".equals(token)) {
                redirectType = "stdout";
                expectRedirectFile = true;
                appendMode = true;
            } else if (expectRedirectFile) {
                File f = new File(token);
                if ("stdout".equals(redirectType)) {
                        stdoutFile = f;
                } else if ("stderr".equals(redirectType)) {
                    stderrFile = f;
                }
                expectRedirectFile = false;
//                appendMode = false;
            } else {
                cmd.add(token);
            }
        }
        return new RedirectionResult(cmd, stdoutFile, stderrFile, appendMode, appendStderr);
    }

    static void changeDirectory(String path) {
    	if (path.charAt(0) == '~') {
    		String home = System.getenv("HOME");
    		if (home == null) {
    			home = System.getenv("USERPROFILE");
    		}
    		Path resolvedPath = Paths.get(home).resolve(path.substring(1).trim()).normalize();
    		if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
    			System.setProperty("user.dir", resolvedPath.toString());
    		} else {
    			System.out.printf("cd: %s: No such file or directory\n", path);
    		}
    	} else {
    		Path workingDir = Paths.get(System.getProperty("user.dir"));
    		Path resolvedPath = workingDir.resolve(path).normalize();
    		if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
    			System.setProperty("user.dir", resolvedPath.toString());
    		} else {
    			System.out.printf("cd: %s: No such file or directory\n", path);
    		}
    	}
    }


	static Path getPath(String path) {
		return Paths.get(path);
	}

	public static List<String> tokenize(String input) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		boolean escaping = false;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);

			if (escaping) {
				current.append(c);
				escaping = false;
				continue;
			}
			if (c == '\\') {
				if (inSingleQuote) {
					current.append(c);
				} else if (inDoubleQuote) {
					if (i + 1 < input.length()) {
						char next = input.charAt(i + 1);
						if (next == '\\' || next == '"' || next == '$' || next == '\n') {
							i++;
							current.append(input.charAt(i));
						} else {
							current.append(c);
						}
					} else {
						current.append(c);
					}
				} else {
					escaping = true;
				}
			} else if (c == '\'') {
				if (inDoubleQuote) {
					current.append(c);
				} else {
					inSingleQuote = !inSingleQuote;
				}
			} else if (c == '"') {
				if (inSingleQuote) {
					current.append(c);
				} else {
					inDoubleQuote = !inDoubleQuote;
				}
			} else if (Character.isWhitespace(c)) {
				if (inSingleQuote || inDoubleQuote) {
					current.append(c);
				} else {
					if (!current.isEmpty()) {
						tokens.add(current.toString());
						current.setLength(0);
					}
				}
			} else {
				current.append(c);
			}
		}
		if (!current.isEmpty()) {
			tokens.add(current.toString());
		}
		return tokens;
	}

	public static String handleRedirection(String input, PrintStream stdout) throws IOException {
		// Redirect stderr (2>)
		if (input.contains(" 2> ")) {
			String[] parts = input.split(" 2> ", 2);
			String commandPart = parts[0].trim();
			String errorPathStr = parts[1].trim();
			Path errorPath = Paths.get(errorPathStr);
			Path parentDir = errorPath.getParent();
			if (parentDir != null && !Files.exists(parentDir)) {
				Files.createDirectories(parentDir);
			}
			if (Files.exists(errorPath)) {
				Files.delete(errorPath);
			}
			Files.createFile(errorPath);
			System.setErr(new PrintStream(Files.newOutputStream(errorPath)));
			return commandPart;
		}

		// Redirect stdout (1> or >)
		if (input.contains(" 1> ") || input.contains(" > ")) {
			String[] parts = input.split("( 1> )|( > )", 2);
			String commandPart = parts[0].trim();
			String outputPathStr = parts[1].trim();
			Path logPath = Paths.get(outputPathStr);
			Path parentDir = logPath.getParent();
			if (parentDir != null && !Files.exists(parentDir)) {
				Files.createDirectories(parentDir);
			}
			if (Files.exists(logPath)) {
				Files.delete(logPath);
			}
			Files.createFile(logPath);
			System.setOut(new PrintStream(Files.newOutputStream(logPath)));
			return commandPart;
		}

		return input;
	}

	static void lsCommand(String input) {
		List<String> args = tokenize(input);
		List<String> params = args.size() > 1 ? args.subList(1, args.size()) : List.of();

		List<String> options = new ArrayList<>();
		List<String> paths = new ArrayList<>();

		// Separate options (start with '-') and paths
		for (String param : params) {
			if (param.startsWith("-")) {
				options.add(param);
			} else {
				paths.add(param);
			}
		}

		// For now, ignore options (no error)
		// If multiple paths given, only handle the first for simplicity
		Path dir;
		if (paths.isEmpty()) {
			dir = getPath(System.getProperty("user.dir"));
		} else {
			dir = getPath(paths.get(0));
			if (!dir.isAbsolute()) {
				dir = getPath(System.getProperty("user.dir")).resolve(dir);
			}
		}

		dir = dir.toAbsolutePath().normalize();

		if (!Files.exists(dir)) {
			System.out.printf("ls: %s: No such file or directory\n", paths.isEmpty() ? "" : paths.get(0));
			return;
		}
		if (!Files.isDirectory(dir)) {
			System.out.printf("ls: %s: Not a directory\n", paths.get(0));
			return;
		}

		try {
			Files.list(dir).forEach(path -> System.out.println(path.getFileName()));
		} catch (IOException e) {
			System.out.println("ls: error reading directory");
		}
	}

	static void helpCommand() {
		System.out.println("Available commands:");
		System.out.println("  exit       Exit the shell");
		System.out.println("  echo       Print arguments");
		System.out.println("  type       Display command type");
		System.out.println("  pwd        Show current directory");
		System.out.println("  cd         Change directory");
		System.out.println("  ls         List directory contents");
		System.out.println("  help       Display this help message");
	}
	
	
	
	static class RedirectionResult {
	    List<String> commandArgs;
	    File stdoutFile;
	    File stderrFile;
	    boolean appendStdout;
	    boolean appendStderr;

	    RedirectionResult(List<String> commandArgs, File stdoutFile, File stderrFile, boolean appendStdout, boolean appendStderr) {
	        this.commandArgs = commandArgs;
	        this.stdoutFile = stdoutFile;
	        this.stderrFile = stderrFile;
	        this.appendStdout = appendStdout;
	        this.appendStderr = appendStderr;
	    }
	}



}