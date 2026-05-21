package com.bennyboops.musicshuffle.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ModConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("music-shuffle.properties");

    private static final boolean DEFAULT_MOD_ENABLED             = true;
    private static final String  DEFAULT_TOAST_MODE              = "BANNER";
    private static final long    DEFAULT_TOAST_DISPLAY_MS        = 5000L;
    private static final long    DEFAULT_TRACK_DELAY_MIN_MS      = 10000L;
    private static final long    DEFAULT_TRACK_DELAY_MAX_MS      = 100000L;
    private static final boolean DEFAULT_DIMENSION_SHUFFLE       = true;
    private static final boolean DEFAULT_SKIP_ON_DIMENSION_CHANGE = true;

    private static final String KEY_MOD_ENABLED               = "modEnabled";
    private static final String KEY_TOAST_MODE                = "toastMode";
    private static final String KEY_TOAST_DISPLAY_MS          = "toastDisplayMs";
    private static final String KEY_TRACK_DELAY_MIN_MS        = "trackDelayMinMs";
    private static final String KEY_TRACK_DELAY_MAX_MS        = "trackDelayMaxMs";
    private static final String KEY_DIMENSION_SHUFFLE         = "dimensionShuffleEnabled";
    private static final String KEY_SKIP_ON_DIMENSION_CHANGE  = "skipOnDimensionChange";

    public boolean modEnabled             = DEFAULT_MOD_ENABLED;
    public MusicShuffleClient.ToastMode toastMode = MusicShuffleClient.ToastMode.BANNER;
    public long    toastDisplayMs         = DEFAULT_TOAST_DISPLAY_MS;
    public long    trackDelayMinMs        = DEFAULT_TRACK_DELAY_MIN_MS;
    public long    trackDelayMaxMs        = DEFAULT_TRACK_DELAY_MAX_MS;
    public boolean dimensionShuffleEnabled    = DEFAULT_DIMENSION_SHUFFLE;
    public boolean skipOnDimensionChange  = DEFAULT_SKIP_ON_DIMENSION_CHANGE;


    private static ModConfig instance;

    public static ModConfig get() {
        if (instance == null) instance = new ModConfig();
        return instance;
    }

    private ModConfig() {}

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            applyToRuntime();
            save();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
        } catch (IOException e) {
            MusicShuffleClient.LOGGER.error("[MusicShuffle] Failed to load config: {}", e.getMessage());
            applyToRuntime();
            return;
        }

        modEnabled = Boolean.parseBoolean(
                props.getProperty(KEY_MOD_ENABLED, String.valueOf(DEFAULT_MOD_ENABLED)));

        String toastModeStr = props.getProperty(KEY_TOAST_MODE, DEFAULT_TOAST_MODE);
        try {
            toastMode = MusicShuffleClient.ToastMode.valueOf(toastModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            toastMode = MusicShuffleClient.ToastMode.BANNER;
        }

        try {
            toastDisplayMs = Long.parseLong(
                    props.getProperty(KEY_TOAST_DISPLAY_MS, String.valueOf(DEFAULT_TOAST_DISPLAY_MS)));
            if (toastDisplayMs < 500L)   toastDisplayMs = 500L;
            if (toastDisplayMs > 30000L) toastDisplayMs = 30000L;
        } catch (NumberFormatException e) {
            toastDisplayMs = DEFAULT_TOAST_DISPLAY_MS;
        }

        try {
            trackDelayMinMs = Long.parseLong(
                    props.getProperty(KEY_TRACK_DELAY_MIN_MS, String.valueOf(DEFAULT_TRACK_DELAY_MIN_MS)));
            if (trackDelayMinMs < 0L)     trackDelayMinMs = 0L;
            if (trackDelayMinMs > 30000L) trackDelayMinMs = 30000L;
        } catch (NumberFormatException e) {
            trackDelayMinMs = DEFAULT_TRACK_DELAY_MIN_MS;
        }

        try {
            trackDelayMaxMs = Long.parseLong(
                    props.getProperty(KEY_TRACK_DELAY_MAX_MS, String.valueOf(DEFAULT_TRACK_DELAY_MAX_MS)));
            if (trackDelayMaxMs < 0L)     trackDelayMaxMs = 0L;
            if (trackDelayMaxMs > 30000L) trackDelayMaxMs = 30000L;
        } catch (NumberFormatException e) {
            trackDelayMaxMs = DEFAULT_TRACK_DELAY_MAX_MS;
        }

        dimensionShuffleEnabled = Boolean.parseBoolean(
                props.getProperty(KEY_DIMENSION_SHUFFLE,
                        String.valueOf(DEFAULT_DIMENSION_SHUFFLE)));

        skipOnDimensionChange = Boolean.parseBoolean(
                props.getProperty(KEY_SKIP_ON_DIMENSION_CHANGE,
                        String.valueOf(DEFAULT_SKIP_ON_DIMENSION_CHANGE)));

        applyToRuntime();
        MusicShuffleClient.LOGGER.info("[MusicShuffle] Config loaded.");
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty(KEY_MOD_ENABLED,              String.valueOf(modEnabled));
        props.setProperty(KEY_TOAST_MODE,               toastMode.name());
        props.setProperty(KEY_TOAST_DISPLAY_MS,         String.valueOf(toastDisplayMs));
        props.setProperty(KEY_TRACK_DELAY_MIN_MS,       String.valueOf(trackDelayMinMs));
        props.setProperty(KEY_TRACK_DELAY_MAX_MS,       String.valueOf(trackDelayMaxMs));
        props.setProperty(KEY_DIMENSION_SHUFFLE,        String.valueOf(dimensionShuffleEnabled));
        props.setProperty(KEY_SKIP_ON_DIMENSION_CHANGE, String.valueOf(skipOnDimensionChange));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "MusicShuffle Configuration");
            }
        } catch (IOException e) {
            MusicShuffleClient.LOGGER.error("[MusicShuffle] Failed to save config: {}", e.getMessage());
        }
    }

    public void applyToRuntime() {
        MusicShuffleClient.modEnabled              = modEnabled;
        MusicShuffleClient.toastMode               = toastMode;
        MusicShuffleClient.toastDisplayMs          = toastDisplayMs;
        MusicShuffleClient.trackDelayMinMs         = trackDelayMinMs;
        MusicShuffleClient.trackDelayMaxMs         = trackDelayMaxMs;
        MusicShuffleClient.dimensionShuffleEnabled = dimensionShuffleEnabled;
        MusicShuffleClient.skipOnDimensionChange   = skipOnDimensionChange;
    }

    public void pullFromRuntime() {
        modEnabled              = MusicShuffleClient.modEnabled;
        toastMode               = MusicShuffleClient.toastMode;
        toastDisplayMs          = MusicShuffleClient.toastDisplayMs;
        trackDelayMinMs         = MusicShuffleClient.trackDelayMinMs;
        trackDelayMaxMs         = MusicShuffleClient.trackDelayMaxMs;
        dimensionShuffleEnabled = MusicShuffleClient.dimensionShuffleEnabled;
        skipOnDimensionChange   = MusicShuffleClient.skipOnDimensionChange;
    }
}