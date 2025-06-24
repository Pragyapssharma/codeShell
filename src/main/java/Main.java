import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
  public static void main(String[] args) throws Exception {
    System.out.print("$ ");
    Scanner scanner = new Scanner(System.in);
    final PrintStream stdout = System.out;
    while (scanner.hasNextLine()) {
      String input = handleRedirection(scanner.nextLine(), stdout);
      List<String> tokens = tokenize(input);
      String argsCleaned = String.join(" ", tokens);
      String command = argsCleaned.split(" ")[0];
      switch (command) {
                case "exit" -> System.exit(0);
                case "echo" -> System.out.println(argsCleaned.split(" ", 2)[1]);
                case "type" -> type(argsCleaned);
                case "pwd" -> System.out.println(getPath(System.getProperty("user.dir")).toAbsolutePath().normalize());
                case "cd" -> changeDirectory(argsCleaned.split(" ", 2)[1]);
                default -> commandExec(tokens, input);
            }
            System.setOut(stdout);
            System.out.print("$ ");
        }
    }

    static void type(String input) {
        String[] validCommands = {"exit", "echo", "type", "pwd", "cd"};
        String command = input.split(" ", 2)[1];
        String[] PATH = System.getenv("PATH").split(":");
        for (String validCommand : validCommands) {
                    if (validCommand.equals(command)) {
                      System.out.printf("%s is a shell builtin\n", command);
                      return;
                    }
                  }
                  for (String path : PATH) {
                    File[] directory = new File(path).listFiles();
                    if (directory != null) {
                      for (File file : directory) {
                        if (file.getName().equals(command)) {
                          System.out.printf("%s is %s\n", command,
                                            file.getAbsolutePath());
                          return;
                        }
                      }
                    }
                  }
                  System.out.printf("%s: not found\n", command);
                }

                static void commandExec(List<String> args, String input) {
                  try {
                    ProcessBuilder builder = new ProcessBuilder(args);
                    Process process = builder.start();
                    BufferedReader stdoutReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                      System.out.println(line);
                    }
                    BufferedReader stderrReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()));
                    while ((line = stderrReader.readLine()) != null) {
                      System.err.println(line);
                    }
                    process.waitFor();
                  } catch (Exception e) {
                    System.err.printf("%s: command not found\n", input);
                  }
                }

                static void changeDirectory(String path) {
                  if (path.charAt(0) == '~') {
                    String part1 = System.getenv("HOME");
                    String part2 = path.substring(1).trim();
                    Path path1 = getPath(part1);
                    Path path2 = getPath(part2);
                    Path resolvedPath = path1.resolve(path2);
                    if (Files.exists(resolvedPath) &&
                        Files.isDirectory(resolvedPath)) {
                      System.setProperty("user.dir", resolvedPath.toString());
                    } else {
                      System.out.printf("cd: %s: No such file or directory\n",
                                        path);
                    }
                  } else {
                    Path workingDir = getPath(System.getProperty("user.dir"));
                    Path normalizedPath = getPath(path);
                    Path resolvedPath = workingDir.resolve(normalizedPath);
                    if (Files.exists(resolvedPath) &&
                        Files.isDirectory(resolvedPath)) {
                      System.setProperty("user.dir", resolvedPath.toString());
                    } else {
                      System.out.printf("cd: %s: No such file or directory\n",
                                        path);
                    }
                  }
                }

                static Path getPath(String path) { return Paths.get(path); }

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
                          if (next == '\\' || next == '"' || next == '$' ||
                              next == '\n') {
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
                public static String handleRedirection(
                    String input, PrintStream stdout) throws IOException {
                  if (input.contains(" 1> ") || input.contains(" > ")) {
                    String[] parts = input.split("( 1> )|( > )");
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
                    System.setOut(
                        new PrintStream(Files.newOutputStream(logPath)));
                    return commandPart;
                  } else {
                    return input;
                  }
                }
    }