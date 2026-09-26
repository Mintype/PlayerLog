package org.mintype.playerlog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    public boolean enabled = true;

    public Notifications notifications = new Notifications();
    public Damage damage = new Damage();
    public CreeperThreat creeperThreat = new CreeperThreat();
    public Drowning drowning = new Drowning();
    public Recipients recipients = new Recipients();
    public Chat chat = new Chat();
    public Cooldowns cooldowns = new Cooldowns();

    public static class Notifications {
        public boolean damage = true;
        public boolean drowning = true;
        public boolean creeperThreat = true;
        public boolean fire = true;
        public boolean fallDamage = true;
        public boolean projectileDamage = true;
        public boolean voidDamage = true;
    }

    public static class Damage {
        public double minimumDamage = 1.0;
        public double lowHealthThreshold = 4.0; // hearts
        public boolean includeSource = true;
        public boolean includeHealth = true;
    }

    public static class CreeperThreat {
        public double detectionRange = 8.0;
        public boolean requireLineOfSight = false;
    }

    public static class Drowning {
        public int secondsRemaining = 5;
    }

    public static class Recipients {
        public boolean operators = true;
        public boolean spectators = true;
    }

    public static class Chat {
        public String prefix = "[PlayerLog]";
        public boolean includeCoordinates = false;
    }

    public static class Cooldowns {
        public double damage = 0.5;
        public double creeperThreat = 5.0;
        public double drowning = 5.0;
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("PlayerLog.json");

    public static ModConfig INSTANCE = load();

    public static ModConfig load() {
        if (!Files.exists(PATH)) {
            ModConfig cfg = new ModConfig();
            save(cfg);
            return cfg;
        }

        try (Reader reader = Files.newBufferedReader(PATH)) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);

            // Protect against a partially missing/invalid config.
            if (config == null) {
                config = new ModConfig();
            }

            return config;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return new ModConfig();
        }
    }

    public static void save(ModConfig cfg) {
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(cfg, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}