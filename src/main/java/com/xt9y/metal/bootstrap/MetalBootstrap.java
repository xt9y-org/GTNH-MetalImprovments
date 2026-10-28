package com.xt9y.metal.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import net.minecraft.launchwrapper.Launch;

/**
 * Requests Angelica's existing SDL GPU Metal backend before its coremod reads
 * the backend properties. Uses JVM properties to coordinate across classloaders.
 */
public final class MetalBootstrap {

    private static final String SDL_ENABLE = "angelica.sdlgpu.enable";
    private static final String SDL_DRIVER = "angelica.sdlgpu.driver";
    private static final String AUTO_REQUESTED = "xt9ymetal.bootstrap.requested";
    private static final String AUTO_CHECKED = "xt9ymetal.bootstrap.checked";
    private static final String STATE = "xt9ymetal.bootstrap.status";
    private static final String CONFIG_LOCATION = "xt9ymetal.bootstrap.configDir";
    private static final String PENDING = "xt9ymetal-metal-starting";
    private static final String DISABLED = "xt9ymetal-metal-disabled";

    private MetalBootstrap() {}

    private static File configDirectory() {
        String configured = System.getProperty(CONFIG_LOCATION);
        if (configured != null) return new File(configured);
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(System.getProperty("user.dir", "."));
        return new File(home, "config");
    }

    private static void status(String message) {
        System.setProperty(STATE, message);
        System.out.println("[GTNH Metal] " + message);
    }

    public static String status() {
        return System.getProperty(STATE, "No backend request made");
    }

    public static synchronized void apply() {
        if (!System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("mac")) {
            return;
        }

        // The RFB and Forge coremod may live in separate classloaders.
        if (Boolean.getBoolean(AUTO_REQUESTED)) {
            return;
        }

        // Always honor launcher JVM properties, including explicit OpenGL selection.
        if (System.getProperty(SDL_ENABLE) != null || System.getProperty(SDL_DRIVER) != null) {
            status("Launcher controls Angelica backend");
            return;
        }

        File configDir = configDirectory();
        System.setProperty(CONFIG_LOCATION, configDir.getAbsolutePath());
        File config = new File(configDir, "xt9ymetal-boot.properties");
        Properties boot = new Properties();
        if (config.isFile()) {
            try (FileInputStream in = new FileInputStream(config)) {
                boot.load(in);
            } catch (IOException e) {
                status("Cannot read startup settings: " + e);
                return;
            }
        }
        if (!Boolean.parseBoolean(boot.getProperty("autoEnableMetal", "true"))) {
            status("Automatic Metal selection disabled in config");
            return;
        }

        File pending = new File(configDir, PENDING);
        File disabled = new File(configDir, DISABLED);
        if (pending.isFile()) {
            // If the previous launch died before its first frame, stop automatic attempts.
            try {
                if (!disabled.exists() && !disabled.createNewFile()) {
                    throw new IOException("Unable to write disabled marker");
                }
                if (!pending.delete()) {
                    status("Previous startup marker could not be removed");
                }
            } catch (IOException e) {
                status("Cannot write recovery state: " + e);
                return;
            }
        }

        if (disabled.isFile()) {
            // Don't forcibly disable Angelica's own UI config; just stop auto-selection.
            status("Automatic Metal disabled after failed startup; OpenGL/Angelica settings retained");
            return;
        }

        try {
            if (!configDir.isDirectory() && !configDir.mkdirs()) {
                throw new IOException("Cannot create " + configDir);
            }
            if (!pending.createNewFile()) {
                throw new IOException("Cannot create startup marker");
            }
        } catch (IOException e) {
            status("Startup marker unavailable; Metal not forced: " + e);
            return;
        }

        // These are opt-in experiments, not automatic Apple Silicon heuristics.
        // Both values are consumed by Angelica on startup, before its GPU device exists.
        String frames = boot.getProperty("sdlFramesInFlight", "0")
            .trim();
        if (!"0".equals(frames) && System.getProperty("angelica.sdlgpu.framesInFlight") == null) {
            try {
                int requested = Integer.parseInt(frames);
                if (requested >= 1 && requested <= 3) {
                    System.setProperty("angelica.sdlgpu.framesInFlight", Integer.toString(requested));
                    System.out.println("[GTNH Metal] Requested SDL frames in flight: " + requested);
                }
            } catch (NumberFormatException e) {
                System.err.println("[GTNH Metal] Invalid sdlFramesInFlight setting: " + frames);
            }
        }
        if (Boolean.parseBoolean(boot.getProperty("disablePresenterThread", "false"))
            && System.getProperty("angelica.sdlgpu.disablePresenterThread") == null) {
            System.setProperty("angelica.sdlgpu.disablePresenterThread", "true");
            System.out.println("[GTNH Metal] SDL presenter thread disabled by user config");
        }

        System.setProperty(AUTO_REQUESTED, "true");
        System.setProperty(SDL_ENABLE, "true");
        System.setProperty(SDL_DRIVER, "metal");
        status("Requested SDL GPU Metal before Angelica initialization");
    }

    public static void markRenderedFrame() {
        if (!Boolean.getBoolean(AUTO_REQUESTED) || Boolean.getBoolean(AUTO_CHECKED)) {
            return;
        }
        synchronized (MetalBootstrap.class) {
            if (Boolean.getBoolean(AUTO_CHECKED)) {
                return;
            }
            System.setProperty(AUTO_CHECKED, "true");
            String backend = "unknown";
            boolean nativeMetal = false;
            try {
                Class<?> manager = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                backend = String.valueOf(
                    manager.getMethod("getRenderBackendName")
                        .invoke(null));
                nativeMetal = backend.toLowerCase(Locale.ROOT)
                    .contains("sdl gpu")
                    && backend.toLowerCase(Locale.ROOT)
                        .contains("metal");
            } catch (ReflectiveOperationException | LinkageError e) {
                backend = "unavailable (" + e.getClass()
                    .getSimpleName() + ")";
            }

            File configDir = configDirectory();
            File pending = new File(configDir, PENDING);
            if (pending.isFile() && !pending.delete()) {
                System.err.println("[GTNH Metal] Could not remove startup marker " + pending);
            }

            if (nativeMetal) {
                status("Metal active: " + backend);
            } else {
                // A graceful OpenGL fallback must NOT be treated as successful Metal startup.
                File disabled = new File(configDir, DISABLED);
                try {
                    if (!disabled.exists() && !disabled.createNewFile()) {
                        throw new IOException("Could not write disabled marker");
                    }
                } catch (IOException e) {
                    System.err.println("[GTNH Metal] Cannot save fallback state: " + e);
                }
                status("Metal unavailable; active backend: " + backend + " (auto-retry disabled until marker removed)");
            }
        }
    }
}
