package com.xt9y.metal.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import net.minecraft.launchwrapper.Launch;

/**
 * Deliberately depends only on Java and LaunchWrapper: runs before Forge mod initialization.
 * Angelica reads its backend flags during the coremod/tweaker phase.
 */
public final class MetalBootstrap {

    private static final String SDL_ENABLE = "angelica.sdlgpu.enable";
    private static final String SDL_DRIVER = "angelica.sdlgpu.driver";
    private static final String PENDING = "xt9ymetal-metal-starting";
    private static final String DISABLED = "xt9ymetal-metal-disabled";
    private static volatile boolean armed;
    private static File pendingFile;

    private MetalBootstrap() {}

    public static synchronized void apply() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return;
        }

        // A launcher-supplied override always wins. Do not fight another user's configuration.
        if (System.getProperty(SDL_ENABLE) != null || System.getProperty(SDL_DRIVER) != null) {
            System.out.println("[GTNH Metal] Launcher controls Angelica backend; not overriding.");
            return;
        }

        File instance = Launch.minecraftHome != null
            ? Launch.minecraftHome
            : new File(System.getProperty("user.dir", "."));
        File configDir = new File(instance, "config");
        Properties boot = new Properties();
        File config = new File(configDir, "xt9ymetal-boot.properties");
        if (config.isFile()) {
            try (FileInputStream in = new FileInputStream(config)) {
                boot.load(in);
            } catch (IOException e) {
                System.err.println("[GTNH Metal] Cannot read startup settings: " + e);
                return;
            }
        }
        if (!Boolean.parseBoolean(boot.getProperty("autoEnableMetal", "true"))) {
            return;
        }

        File disabled = new File(configDir, DISABLED);
        pendingFile = new File(configDir, PENDING);
        if (pendingFile.isFile()) {
            // The previous launch never reached a rendered frame: avoid an automatic crash loop.
            try {
                if (!configDir.isDirectory() && !configDir.mkdirs()) {
                    throw new IOException("Cannot create " + configDir);
                }
                disabled.createNewFile();
                if (!pendingFile.delete()) {
                    System.err.println("[GTNH Metal] Could not remove old startup marker.");
                }
            } catch (IOException e) {
                System.err.println("[GTNH Metal] Recovery marker could not be written: " + e);
            }
        }
        if (disabled.isFile()) {
            System.setProperty(SDL_ENABLE, "false");
            System.out.println(
                "[GTNH Metal] Previous Metal startup failed; requesting OpenGL. "
                + "Delete config/" + DISABLED + " to retry.");
            return;
        }

        // Write marker before enabling the GPU driver; a failed boot disables the next attempt.
        try {
            if (!configDir.isDirectory() && !configDir.mkdirs()) {
                throw new IOException("Cannot create " + configDir);
            }
            if (!pendingFile.createNewFile()) {
                throw new IOException("Cannot create startup marker");
            }
        } catch (IOException e) {
            System.err.println("[GTNH Metal] Startup marker unavailable; not forcing Metal: " + e);
            return;
        }
        System.setProperty(SDL_ENABLE, "true");
        System.setProperty(SDL_DRIVER, "metal");
        armed = true;
        System.out.println("[GTNH Metal] Requested Angelica SDL GPU Metal backend on macOS.");
    }

    public static void markRenderedFrame() {
        if (!armed) {
            return;
        }
        synchronized (MetalBootstrap.class) {
            if (!armed) {
                return;
            }
            armed = false;
            if (pendingFile != null && pendingFile.isFile() && !pendingFile.delete()) {
                System.err.println("[GTNH Metal] Failed to clear startup marker.");
            }
        }
    }
}
