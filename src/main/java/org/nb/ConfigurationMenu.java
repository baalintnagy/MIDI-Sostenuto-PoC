package org.nb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ConfigurationMenu {
    private ConfigurationMenu() {}

    static List<Path> discover(List<Path> directories) throws IOException {
        Map<String, Path> files = new LinkedHashMap<>();
        for (Path directory : directories) {
            try (var entries = Files.list(directory)) {
                entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> files.putIfAbsent(path.getFileName().toString().toLowerCase(Locale.ROOT), path));
            }
        }
        return files.values().stream()
            .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    static AppConfig select(BufferedReader reader, PrintStream output, List<Path> files,
                            Runnable printDevices) throws IOException {
        while (true) {
            output.println("Choose a configuration:");
            output.println("0. Other...");
            for (int i = 0; i < files.size(); i++) {
                output.printf("%d. %s%n", i + 1, files.get(i).getFileName());
            }
            output.print("Selection: ");
            String input = reader.readLine();
            if (input == null) {
                throw new IOException("Input closed before a configuration was selected");
            }
            int selection;
            try {
                selection = Integer.parseInt(input.trim());
            } catch (NumberFormatException exception) {
                output.printf("Enter a number from 0 to %d.%n", files.size());
                continue;
            }
            if (selection < 0 || selection > files.size()) {
                output.printf("Enter a number from 0 to %d.%n", files.size());
                continue;
            }
            if (selection == 0) {
                printDevices.run();
                output.println(SustSosApplication.CONFIG_PROMPT);
                while (true) {
                    String manual = reader.readLine();
                    if (manual == null) {
                        throw new IOException("Input closed before a manual configuration was entered");
                    }
                    try {
                        return AppConfig.parse(manual);
                    } catch (RuntimeException exception) {
                        output.println("Invalid configuration: " + exception.getMessage());
                        output.println(SustSosApplication.CONFIG_PROMPT);
                    }
                }
            }
            Path selected = files.get(selection - 1);
            try {
                return AppConfig.load(selected);
            } catch (IOException exception) {
                output.println(exception.getMessage());
            }
        }
    }
}
