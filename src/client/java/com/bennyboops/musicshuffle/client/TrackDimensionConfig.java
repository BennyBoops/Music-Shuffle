package com.bennyboops.musicshuffle.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TrackDimensionConfig {

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER    = "minecraft:the_nether";
    public static final String END       = "minecraft:the_end";

    public static final List<String> ALL_DIMS = List.of(OVERWORLD, NETHER, END);

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("music-shuffle-track-dimensions.properties");

    private final Map<String, Set<String>> trackDimensions = new LinkedHashMap<>();


    private static TrackDimensionConfig instance;

    public static TrackDimensionConfig get() {
        if (instance == null) instance = new TrackDimensionConfig();
        return instance;
    }

    private TrackDimensionConfig() {}


    public void load() {
        trackDimensions.clear();
        if (!Files.exists(CONFIG_PATH)) {
            writeDefaults();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                String fileName = trimmed.substring(0, eq).trim();
                String dims     = trimmed.substring(eq + 1).trim();
                Set<String> dimSet = new LinkedHashSet<>();
                for (String d : dims.split(",")) {
                    String dd = d.trim().toLowerCase(Locale.ROOT);
                    if (!dd.isEmpty()) dimSet.add(dd);
                }
                if (!fileName.isEmpty()) trackDimensions.put(fileName, dimSet);
            }
            MusicShuffleClient.LOGGER.info(
                    "[MusicShuffle] Loaded per-track dimension config ({} track(s)).",
                    trackDimensions.size());
        } catch (IOException e) {
            MusicShuffleClient.LOGGER.error(
                    "[MusicShuffle] Failed to load track dimension config: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
                writer.write("# MusicShuffle Per-Track Dimension Config");
                writer.newLine();
                writer.write("# filename = dimension1, dimension2, ...");
                writer.newLine();
                writer.write("# If a track has no entry it plays in ALL dimensions.");
                writer.newLine();
                writer.newLine();
                for (Map.Entry<String, Set<String>> e : trackDimensions.entrySet()) {
                    writer.write(e.getKey() + " = " + String.join(", ", e.getValue()));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            MusicShuffleClient.LOGGER.error(
                    "[MusicShuffle] Failed to save track dimension config: {}", e.getMessage());
        }
    }

    private void writeDefaults() {
        // Overworld tracks
        String[] overworld = {
                "C418 - Axolotl.ogg", "C418 - Dragon Fish.ogg", "C418 - Shuniji.ogg",
                "Kumi Tanioka - An Ordinary Day.ogg", "Kumi Tanioka - Comforting Memories.ogg",
                "Kumi Tanioka - Floating Dream.ogg", "Lena Raine - Ancestry.ogg",
                "Lena Raine - Infinite Amethyst.ogg", "Lena Raine - Left to Bloom.ogg",
                "Lena Raine - One More Day.ogg", "Lena Raine - otherside.ogg",
                "Lena Raine - Stand Tall.ogg", "Lena Raine - Wending.ogg",
                "Amos Roddy - Below and Above.ogg", "Amos Roddy - Fireflies.ogg",
                "Amos Roddy - Broken Clocks.ogg", "Amos Roddy - Lilypad.ogg",
                "Amos Roddy - O's Piano.ogg", "Amos Roddy - Tears.ogg",
                "Lena Raine - Aerie.ogg", "Lena Raine - Firebugs.ogg",
                "Lena Raine - Labyrinthine.ogg", "Samuel Åberg - Five.ogg",
                "Aaron Cherof - A Familiar Room.ogg", "Aaron Cherof - Bromeliad.ogg",
                "Aaron Cherof - Crescent Dunes.ogg", "Aaron Cherof - Echo in the Wind.ogg",
                "Aaron Cherof - Relic.ogg", "Aaron Cherof - Featherfall.ogg",
                "Aaron Cherof - Precipice.ogg", "Aaron Cherof - Puzzlebox.ogg",
                "Aaron Cherof - Watcher.ogg", "Lena Raine - Creator (Music Box Version).ogg",
                "Lena Raine - Creator.ogg", "Lena Raine - Deeper.ogg",
                "Lena Raine - Eld Unknown.ogg", "Lena Raine - Endless.ogg",
                "Tanioka Kumi - komorebi.ogg", "Tanioka Kumi - pokopoko.ogg",
                "Tanioka Kumi - yakusoku.ogg", "C418 - Beginning.ogg", "C418 - Cat.ogg",
                "C418 - Chris.ogg", "C418 - Clark.ogg", "C418 - Danny.ogg",
                "C418 - Death.ogg", "C418 - Dog.ogg", "C418 - Door.ogg",
                "C418 - Droopy likes ricochet.ogg", "C418 - Droopy likes your face.ogg",
                "C418 - Dry Hands.ogg", "C418 - Excuse.ogg", "C418 - Haggstrom.ogg",
                "C418 - Key.ogg", "C418 - Living Mice.ogg", "C418 - Mice on Venus.ogg",
                "C418 - Minecraft.ogg", "C418 - Moog City.ogg", "C418 - Oxygène.ogg",
                "C418 - Subwoofer Lullaby.ogg", "C418 - Sweden.ogg", "C418 - Thirteen.ogg",
                "C418 - Wet Hands.ogg", "C418 - Équinoxe.ogg", "C418 - Alpha.ogg",
                "C418 - Aria Math.ogg", "C418 - Ballad of the Cats.ogg",
                "C418 - Beginning 2.ogg", "C418 - Biome Fest.ogg", "C418 - Blind Spots.ogg",
                "C418 - Blocks.ogg", "C418 - Chirp.ogg", "C418 - Dreiton.ogg",
                "C418 - Eleven.ogg", "C418 - Far.ogg", "C418 - Flake.ogg",
                "C418 - Floating Trees.ogg", "C418 - Haunt Muskie.ogg", "C418 - Intro.ogg",
                "C418 - Ki.ogg", "C418 - Kyoto.ogg", "C418 - Mall.ogg",
                "C418 - Mellohi.ogg", "C418 - Moog City 2.ogg", "C418 - Mutation.ogg",
                "C418 - Stal.ogg", "C418 - Strad.ogg", "C418 - Taswell.ogg",
                "C418 - Wait.ogg", "C418 - Ward.ogg", "C418 - Warmth.ogg",
                "Peter Hont - Finally.ogg"
        };

        // Nether tracks
        String[] nether = {
                "Lena Raine - Chrysopoeia.ogg", "Lena Raine - Pigstep (Mono Mix).ogg",
                "Lena Raine - Pigstep (Stereo Mix).ogg", "Lena Raine - So Below.ogg",
                "Lena Raine - Rubedo.ogg", "C418 - Concrete Halls.ogg",
                "C418 - Dead Voxel.ogg"
        };

        // End tracks
        String[] end = {
                "C418 - The End.ogg", "Peter Hont - Astray Archipelago.ogg",
                "Peter Hont - End..ogg", "Peter Hont - Obsidian Cavern.ogg",
                "Peter Hont - Obsidian Gate Tower.ogg", "Rostislav Trifonov - Ethereal Blocks.ogg"
        };

        for (String f : overworld) {
            trackDimensions.put(f, new LinkedHashSet<>(List.of(OVERWORLD)));
        }
        for (String f : nether) {
            trackDimensions.put(f, new LinkedHashSet<>(List.of(NETHER)));
        }
        for (String f : end) {
            trackDimensions.put(f, new LinkedHashSet<>(List.of(END)));
        }

        save();
        MusicShuffleClient.LOGGER.info("[MusicShuffle] Written default track dimension config.");
    }


    public void seedIfAbsent(String fileName) {
        if (!trackDimensions.containsKey(fileName)) {
            trackDimensions.put(fileName, new LinkedHashSet<>(ALL_DIMS));
        }
    }

    public boolean isAllowedIn(String fileName, String dimensionId) {
        Set<String> dims = trackDimensions.get(fileName);
        if (dims == null) return true;
        return dims.contains(dimensionId.toLowerCase(Locale.ROOT));
    }

    public void toggle(String fileName, String dimensionId) {
        String dim = dimensionId.toLowerCase(Locale.ROOT);
        Set<String> dims = trackDimensions.computeIfAbsent(
                fileName, k -> new LinkedHashSet<>(ALL_DIMS));
        if (!dims.remove(dim)) dims.add(dim);
        save();
    }

    public List<File> getTracksForDimension(String dimensionId, File musicFolder,
                                            Set<String> blacklist) {
        String dim = dimensionId.toLowerCase(Locale.ROOT);
        List<File> result = new ArrayList<>();
        scanFolder(musicFolder, dim, blacklist, result);
        return result;
    }

    private void scanFolder(File folder, String dim, Set<String> blacklist, List<File> result) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanFolder(f, dim, blacklist, result);
            } else if (f.isFile() && isSupportedExtension(f.getName())
                    && !blacklist.contains(f.getName())
                    && isAllowedIn(f.getName(), dim)) {
                result.add(f);
            }
        }
    }

    private static boolean isSupportedExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".wav") || lower.endsWith(".ogg");
    }
}