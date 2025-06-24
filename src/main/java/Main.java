import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        final PrintStream stdout = System.out;
        System.out.print("$ ");

        while (scanner.hasNextLine()) {
            String rawLine = scanner.nextLine();

            // Handle stdout redirection
            PrintStream redirected = handleRedirection(rawLine);
            if (redirected != null) {
                System.setOut(redirected);
            }

            // After redirection, get the real command to parse
            String line = System.getProperty("redirected.input", rawLine);
            List<String> tokens = tokenize(line);

            if (!tokens.isEmpty()) {
                switch (tokens.get(0)) {
                    case "exit":
                        System.exit(0);
                    case "echo":
                        echo(tokens);
                        break;
                    case "type":
                        type(tokens);
                        break;
                    case "pwd":
                        System.out.println(
                            Paths.get(System.getProperty("user.dir"))
                                 .toAbsolutePath()
                                 .normalize()
                        );
                        break;
                    case "cd":
                        changeDirectory(tokens);
                        break;
                    default:
                        commandExec(tokens);
                }
            }

            // Restore stdout, prompt next line
            System.setOut(stdout);
            System.out.print("$ ");
        }
    }

    static void echo(List<String> tokens) {
        System.out.println(
            tokens.size() > 1 ? String.join(" ", tokens.subList(1, tokens.size())) : ""
        );
    }

    static void type(List<String> tokens) {
        if (tokens.size() < 2) return;
        String cmd = tokens.get(1);
        List<String> builtins = List.of("exit", "echo", "type", "pwd", "cd");
        if (builtins.contains(cmd)) {
            System.out.printf("%s is a shell builtin%n", cmd);
            return;
        }
        for (String path : System.getenv("PATH").split(":")) {
            File f = new File(path, cmd);
            if (f.exists() && f.canExecute()) {
                System.out.printf("%s is %s%n", cmd, f.getAbsolutePath());
                return;
            }
        }
        System.out.printf("%s: not found%n", cmd);
    }

    static void commandExec(List<String> tokens) {
        try {
            ProcessBuilder pb = new ProcessBuilder(tokens)
                .directory(new File(System.getProperty("user.dir")));
            Process p = pb.start();

            try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                out.lines().forEach(System.out::println);
                err.lines().forEach(System.err::println);
            }
            p.waitFor();
        } catch (Exception e) {
            System.err.printf("%s: command not found%n", tokens.get(0));
        }
    }

    static void changeDirectory(List<String> tokens) {
        if (tokens.size() < 2) return;
        String path = tokens.get(1);
        Path target = path.startsWith("~")
            ? Paths.get(System.getenv("HOME") + path.substring(1))
            : Paths.get(System.getProperty("user.dir")).resolve(path).normalize();

        if (Files.exists(target) && Files.isDirectory(target)) {
            System.setProperty("user.dir", target.toString());
        } else {
            System.out.printf("cd: %s: No such file or directory%n", path);
        }
    }

    static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean sq = false, dq = false, esc = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (esc) {
                cur.append(c);
                esc = false;
                continue;
            }
            if (c == '\\' && !sq) {
                esc = true;
                continue;
            }
            if (c == '\'' && !dq) {
                sq = !sq;
                continue;
            }
            if (c == '"' && !sq) {
                dq = !dq;
                continue;
            }
            if (Character.isWhitespace(c) && !sq && !dq) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens;
    }

    static PrintStream handleRedirection(String input) throws IOException {
        if (input.contains(" 1> ") || input.contains(" > ")) {
            String[] parts = input.split(" (?:1>|>) ");
            String cmd = parts[0].trim();
            String outPath = parts[1].trim();
            Path p = Paths.get(outPath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.write(p, new byte[0]); // truncate/create

            System.setProperty("redirected.input", cmd);
            return new PrintStream(Files.newOutputStream(p));
        }
        System.setProperty("redirected.input", input);
        return null;
    }
}
