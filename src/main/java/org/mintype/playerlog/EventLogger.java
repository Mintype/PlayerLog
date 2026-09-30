package org.mintype.playerlog;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class EventLogger {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static Path getLogFile() throws IOException {
        Path logDir = FabricLoader.getInstance().getConfigDir().resolve("playerlog").resolve("logs");
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }

        String fileName = LocalDate.now().format(DATE_FORMATTER) + ".log";
        return logDir.resolve(fileName);
    }

    public static synchronized void log(String eventType, String victim, String attackerOrSource, double health, String location, String details) {
        try {
            Path file = getLogFile();
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);

            // Detailed structured line
            String logLine = String.format("[%s] [%s] Victim: %s | Attacker/Source: %s | HP: %.1f | Pos: %s | Details: %s%n",
                    timestamp,
                    eventType.toUpperCase(),
                    victim != null ? victim : "N/A",
                    attackerOrSource != null ? attackerOrSource : "N/A",
                    health,
                    location,
                    details
            );

            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(logLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}