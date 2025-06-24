import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
  public static void main(String[] args) throws Exception {
    Scanner scanner = new Scanner(System.in);
    Path wd = Paths.get("").toAbsolutePath();
    while (true) {
        System.out.print("$ ");
        String input = scanner.nextLine();
        var result = extractStreams(parse(input));
        var parts = result.commands();

        try (var printer = result.streams().toPrinter(System.out, System.err)) {
            String cmd = parts.getFirst();
            switch (cmd) {
                case "exit":
                    // exit with code from args or 0 if missing
                    int code = 0;
                    if (parts.size() > 1) {
                        try {
                            code = Integer.parseInt(parts.get(1));
                        } catch (NumberFormatException e) {
                            printer.err.println("exit: numeric argument required");
                            code = 1;
                        }
                    }
                    System.exit(code);
                    break;

                case "echo":
                    printer.out.println(String.join(" ", parts.subList(1, parts.size())));
                    break;

                case "type":
                    if (parts.size() < 2) {
                        printer.err.println("type: missing argument");
                        break;
                    }
                    String what = parts.get(1);
                    switch (what) {
                        case "exit":
                        case "echo":
                        case "type":
                        case "pwd":
                        case "cd":
                            printer.out.println(what + " is a shell builtin");
                            break;
                        default:
                            Optional<Path> found = findOnPath(what);
                            if (found.isPresent()) {
                                printer.out.println(what + " is " + found.get());
                            } else {
                                printer.out.println(what + ": not found");
                            }
                            break;
                    }
                    break;

                case "pwd":
                    printer.out.println(wd);
                    break;

                case "cd":
                    if (parts.size() < 2) {
                        printer.err.println("cd: missing argument");
                        break;
                    }
                    Path to = Path.of(parts.get(1));
                    Path newWd;
                    if (to.isAbsolute()) {
                        newWd = to;
                    } else if ("~".equals(to.toString())) {
                        newWd = Paths.get(System.getenv("HOME"));
                    } else {
                        newWd = wd.resolve(to).normalize();
                    }
                    if (Files.isDirectory(newWd)) {
                        wd = newWd;
                    } else {
                        printer.err.printf("cd: %s: No such file or directory%n", newWd);
                    }
                    break;

                default:
                    Optional<Path> executable = findOnPath(cmd);
                    if (executable.isPresent()) {
                        runProgram(parts, result.streams(), wd);
                    } else {
                        printer.err.println(cmd + ": command not found");
                    }
                    break;
            }
        }

    }

    }

  private static void runProgram(List<String> commands, Streams streams, Path wd) {
	    try {
	        var builder = new ProcessBuilder();
	        // Remove inheritIO
	        if (streams.output() != null) {
	            builder.redirectOutput(streams.output());
	        }
	        if (streams.err() != null) {
	            builder.redirectError(streams.err());
	        }
	        builder.command(commands);
	        builder.directory(wd.toFile());  // Important for correct cwd
	        Process process = builder.start();
	        process.waitFor();
	    } catch (IOException | InterruptedException e) {
	        throw new RuntimeException(e);
	    }
	}


    private static Optional<Path> findOnPath(String file) {
        var pathVar = System.getenv("PATH");
        String[] paths = pathVar.contains("C:\\") ? pathVar.split(";") :
                  pathVar.split(":");
                  for (String path : paths) {
                    Path p = Path.of(path).resolve(file);
                    if (Files.isExecutable(p)) {
                      return Optional.of(p);
                    }
                    p = Path.of(path).resolve(file + ".exe");
                    if (Files.isExecutable(p)) {
                      return Optional.of(p);
                    }
                  }
                  return Optional.empty();
                }

    private static List<String> parse(String input) {
        var result = new ArrayList<String>();
        var currentToken = new StringBuilder();
        Character quote = null;
        boolean backslash = false;
        char[] charArray = input.toCharArray();
        for (int i = 0; i < charArray.length; i++) {
            char c = charArray[i];
            switch (c) {
                case ' ':
                    if (backslash) {
                        currentToken.append(c);
                    } else if (quote == null) {
                        if (currentToken.length() > 0) {
                            result.add(currentToken.toString());
                            currentToken = new StringBuilder();
                        }
                    } else {
                        currentToken.append(c);
                    }
                    break;

                case '\'':
                case '"':
                    if (backslash) {
                        currentToken.append(c);
                    } else {
                        if (quote == null) {
                            quote = c;
                        } else if (quote.equals(c)) {
                            quote = null;
                        } else {
                            currentToken.append(c);
                        }
                    }
                    break;

                case '\\':
                    if (backslash) {
                        currentToken.append(c);
                        backslash = false;
                    } else {
                        if (quote != null) {
                            if (quote == '\'') {
                                // backslash ignored inside single quotes
                                currentToken.append(c);
                            } else if (quote == '"') {
                                Character nextChar = next(charArray, i);
                                if (nextChar != null && (nextChar == '$' || nextChar == '~' || nextChar == '"' || nextChar == '\\' || nextChar == '\n')) {
                                    backslash = true;
                                } else {
                                    currentToken.append(c);
                                }
                            } else {
                                currentToken.append(c);
                            }
                        } else {
                            backslash = true;
                        }
                    }
                    break;

                default:
                    currentToken.append(c);
                    if (backslash) {
                        backslash = false;
                    }
                    break;
            }
        }

        if (currentToken.length() > 0) {
            result.add(currentToken.toString());
        }

        return result;
    }


    private static Character next(char[] charArray, int current) {
        if (current + 1 < charArray.length) {
            return charArray[current + 1];
        } else {
            return null;
        }
    }


    private static ExtractResult extractStreams(List<String> parts) {
        var newCommands = new ArrayList<String>();
        File output = null;
        File err = null;
        String lastRedirection = null;
        for (String command : parts) {
            if ("1>".equals(lastRedirection) || ">".equals(lastRedirection)) {
                output = new File(command);
                lastRedirection = null;
            } else if ("2>".equals(lastRedirection)) {
                err = new File(command);
                lastRedirection = null;
            } else {
                if (">".equals(command) || "1>".equals(command) || "2>".equals(command)) {
                    lastRedirection = command;
                } else {
                    newCommands.add(command);
                }
            }
        }
        return new ExtractResult(newCommands, new Streams(null, output, err));
    }



    private record ExtractResult(List<String> commands, Streams streams) {
    }

    private record Streams(File input, File output, File err) {
        public Printer toPrinter(PrintStream defaultOut, PrintStream defaultErr) throws IOException {
            PrintStream outStream;
            if (output != null) {
                output.createNewFile();
                outStream = new PrintStream(output);
            } else {
                outStream = defaultOut;
            }

            PrintStream errStream;
            if (err != null) {
                err.createNewFile();
                errStream = new PrintStream(err);
            } else {
                errStream = defaultErr;
            }
            return new Printer(outStream, errStream);
        }
    }

    private record Printer(PrintStream out, PrintStream err) implements AutoCloseable {
        @Override
        public void close() {
            out.flush();
            err.flush();
            if (out != System.out) out.close();
            if (err != System.err) err.close();
        }
    }


}