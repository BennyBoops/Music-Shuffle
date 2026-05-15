package com.bennyboops.musicshuffle.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;


import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.FileSystemAlreadyExistsException;

public class MusicShuffleClient implements ClientModInitializer {

	public static final String MOD_ID = "music-shuffle";
	public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

	public static File musicFolder;

	private static final String BUNDLED_SONGS_PATH = "/assets/musicshuffle/songs/";

	static MusicPlayer musicPlayer;

	public static final ConcurrentHashMap<String, Integer> trackColours = new ConcurrentHashMap<>();

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"));

	static KeyMapping controlKey;
	private static KeyMapping pausePlayKey;
	private static KeyMapping rewindKey;
	private static KeyMapping skipKey;
	private static KeyMapping shuffleKey;

	private static String lastToastTrack = "";

	public static boolean modEnabled = true;
	public static boolean toastsEnabled = true;

	public static volatile boolean startupComplete = false;

	private static volatile String nowPlayingHudText = "";
	private static volatile long nowPlayingHudUntilMs = 0L;
	private static long nowPlayingHudStartMs = 0L;
	private static final long HOLD_MS = 5000L;
	private static final long FADE_MS = 1500L;
	private static final int SLIDE_OFFSET = 60;

	@Override
	public void onInitializeClient() {
		musicFolder = new File(FabricLoader.getInstance().getGameDir().toFile(), MOD_ID);
		ensureMusicFolder();
		extractBundledSongs();
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "now_playing_hud"),
				(graphics, tickCounter) -> renderNowPlayingHud(graphics)
		);

		musicPlayer = new MusicPlayer(musicFolder);

		Thread startupThread = new Thread(() -> {
			try {
				Thread.sleep(8000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			loadBlacklist();
			musicPlayer.start();
			TrackConfigScreen.populateTrackColours();
			startupComplete = true;
		}, "MusicShuffle-Startup");
		startupThread.setDaemon(true);
		startupThread.start();

		controlKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.musicshuffle.controls",
				GLFW.GLFW_KEY_EQUAL,
				CATEGORY
		));
		pausePlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.musicshuffle.pauseplay",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));
		rewindKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.musicshuffle.rewind",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));
		skipKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.musicshuffle.skip",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));
		shuffleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.musicshuffle.shuffle",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		LOGGER.info("[MusicShuffle] Initialised. Music folder: {}", musicFolder.getAbsolutePath());
	}

	private void onClientTick(Minecraft client) {
		if (musicPlayer == null || client.options == null) return;

		while (controlKey.consumeClick()) {
			client.setScreen(new MusicControlScreen(client.screen));
		}
		while (pausePlayKey.consumeClick()) {
			musicPlayer.togglePause();
		}
		while (rewindKey.consumeClick()) {
			musicPlayer.rewind();
		}
		while (skipKey.consumeClick()) {
			musicPlayer.skip();
		}
		while (shuffleKey.consumeClick()) {
			musicPlayer.reshuffle();
			showNowPlayingToast(client, "Queue Shuffled");
		}

		if (!modEnabled) {
			if (musicPlayer.isRunning()) musicPlayer.stop();
			return;
		}

		float musicVol  = client.options.getSoundSourceVolume(SoundSource.MUSIC);
		float masterVol = client.options.getSoundSourceVolume(SoundSource.MASTER);
		musicPlayer.setVolume(musicVol * masterVol);

		client.getMusicManager().stopPlaying();

		if (!musicPlayer.isRunning() && startupComplete) musicPlayer.resume();

		String track = musicPlayer.getCurrentTrackName();
		if (!track.isEmpty() && !track.equals(lastToastTrack) && !musicPlayer.isPaused()) {
			lastToastTrack = track;
			showNowPlayingToast(client, track);
		}
	}

	private static int nowPlayingHudColor = 0x555555;

	static void showNowPlayingToast(Minecraft client, String trackName) {
		if (!toastsEnabled) return;
		nowPlayingHudText = "♫ " + trackName;
		nowPlayingHudStartMs = Util.getMillis();
	}

	private static void renderNowPlayingHud(GuiGraphicsExtractor graphics) {
		if (nowPlayingHudText.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.font == null || mc.getWindow() == null) return;

		final long HOLD_MS = 5000L;
		final long FADE_MS = 1500L;
		final int SLIDE_OFFSET = 60;
		final int X_BASE_OFFSET = 8;

		long now = Util.getMillis();
		float elapsed = now - nowPlayingHudStartMs;

		float alphaFactor;
		float slideT;

		//SLIDE IN
		if (elapsed <= FADE_MS) {
			float t = elapsed / FADE_MS;

			if (t < 0f) t = 0f;
			if (t > 1f) t = 1f;

			t = t * t * (3f - 2f * t);

			slideT = 1f - t;
			alphaFactor = t;
		}
		//HOLD
		else if (elapsed <= HOLD_MS + FADE_MS) {
			slideT = 0f;
			alphaFactor = 1f;
		}
		//SLIDE OUT
		else {
			float t = (elapsed - HOLD_MS - FADE_MS) / FADE_MS;

			if (t < 0f) t = 0f;
			if (t > 1f) t = 1f;

			t = t * t * (3f - 2f * t);

			slideT = t;
			alphaFactor = 1f - t;
		}

		int alpha = (int)(alphaFactor * 255f);

		if (elapsed >= HOLD_MS + (FADE_MS * 2) + 50f) {
			nowPlayingHudText = "";
			return;
		}

		String name = MusicShuffleClient.musicPlayer != null
				? MusicShuffleClient.musicPlayer.getCurrentTrackName()
				: "";

		int songColorRGB = MusicShuffleClient.trackColours.getOrDefault(
				name,
				TrackConfigScreen.albumColour("Unsorted")
		);

		float desat = 0.65f;

		int r = (songColorRGB >> 16) & 0xFF;
		int g = (songColorRGB >> 8) & 0xFF;
		int b = songColorRGB & 0xFF;

		int gray = (r + g + b) / 3;

		r = (int)(gray + (r - gray) * desat);
		g = (int)(gray + (g - gray) * desat);
		b = (int)(gray + (b - gray) * desat);

		int desatColor = (r << 16) | (g << 8) | b;

		int bg = ((int)(alpha * 0.65f) << 24) | (desatColor & 0x00FFFFFF);

		int border = (alpha << 24) | 0x888888;
		int textColor = (alpha << 24) | 0xFFFFFF;

		int textW = mc.font.width(Component.literal(nowPlayingHudText).withStyle(s -> s.withBold(true)));
		int textH = mc.font.lineHeight;

		int pad = 8;
		int y = 8;

		int baseX = X_BASE_OFFSET;
		int x = baseX + (int)(-SLIDE_OFFSET * slideT);

		int x2 = x + textW + pad * 2;
		int y2 = y + textH + pad * 2;

		int textY = y + pad + 1;

		graphics.fill(x, y, x2, y2, bg);

		graphics.fill(x,  y,    x2, y + 1, border);
		graphics.fill(x,  y2-1, x2, y2, border);
		graphics.fill(x,  y,    x + 1, y2, border);
		graphics.fill(x2-1, y,  x2, y2, border);

		graphics.text(
				mc.font,
				Component.literal(nowPlayingHudText).withStyle(s -> s.withBold(true)),
				x + pad,
				textY,
				textColor,
				true
		);
	}


	private void ensureMusicFolder() {
		if (!musicFolder.exists()) {
			if (musicFolder.mkdirs()) {
				LOGGER.info("[MusicShuffle] Created music folder at: {}", musicFolder.getAbsolutePath());
			} else {
				LOGGER.error("[MusicShuffle] Failed to create music folder at: {}", musicFolder.getAbsolutePath());
			}
		}
	}

	private void extractBundledSongs() {
		URL resourceUrl = MusicShuffleClient.class.getResource(BUNDLED_SONGS_PATH);
		if (resourceUrl == null) {
			LOGGER.info("[MusicShuffle] No bundled songs in JAR ({}) - skipping extraction.", BUNDLED_SONGS_PATH);
			return;
		}

		try {
			URI uri = resourceUrl.toURI();

			if (uri.getScheme().equals("jar")) {
				FileSystem fs;
				boolean weOpenedIt;

				try {
					fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
					weOpenedIt = true;
				} catch (FileSystemAlreadyExistsException e) {
					fs = FileSystems.getFileSystem(uri);
					weOpenedIt = false;
				}

				try {
					copyFromPath(fs.getPath(BUNDLED_SONGS_PATH));
				} finally {
					if (weOpenedIt) fs.close();
				}

			} else {
				copyFromPath(Paths.get(uri));
			}

		} catch (URISyntaxException | IOException e) {
			LOGGER.error("[MusicShuffle] Failed to extract bundled songs: {}", e.getMessage());
		}
	}

	private void copyFromPath(Path songsDir) throws IOException {
		copyFromPathRecursive(songsDir, musicFolder);
	}

	private void copyFromPathRecursive(Path sourceDir, File destDir) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
			for (Path entry : stream) {
				String name = entry.getFileName().toString();

				if (Files.isDirectory(entry)) {
					File subFolder = new File(destDir, name);
					if (!subFolder.exists()) subFolder.mkdirs();
					copyFromPathRecursive(entry, subFolder);
				} else {
					String lower = name.toLowerCase();
					if (!lower.endsWith(".wav") && !lower.endsWith(".ogg")) continue;

					File destination = new File(destDir, name);
					if (destination.exists()) {
						LOGGER.debug("[MusicShuffle] Skipping '{}' - already on disk.", name);
						continue;
					}
					try (InputStream in = Files.newInputStream(entry)) {
						Files.copy(in, destination.toPath());
						LOGGER.info("[MusicShuffle] Extracted bundled song: {}", name);
					}
				}
			}
		}
	}

	private static File blacklistFile;

	private void loadBlacklist() {
		blacklistFile = new File(musicFolder, ".blacklist");

		if (!blacklistFile.exists()) {
			try {
				java.util.List<String> template = java.util.Arrays.asList(
						"# MusicShuffle Blacklist",
						"# ----------------------",
						"# Add one filename per line (including extension) to exclude a track from the shuffle queue.",
						"#",
						"# You can edit this file any time.",
						"# Can also edit the blacklist on the config screen in game.",

						"Aaron Cherof - Precipice.ogg",
						"Aaron Cherof - Relic.ogg",
						"Amos Roddy - Tears.ogg",
						"C418 - Blocks.ogg",
						"C418 - Cat.ogg",
						"C418 - Chirp.ogg",
						"C418 - Far.ogg",
						"C418 - Mall.ogg",
						"C418 - Mellohi.ogg",
						"C418 - Stal.ogg",
						"C418 - Strad.ogg",
						"C418 - Thirteen.ogg",
						"C418 - Wait.ogg",
						"C418 - Ward.ogg",
						"Lena Raine - Creator (Music Box Version).ogg",
						"Lena Raine - Creator.ogg",
						"Lena Raine - Pigstep (Mono Mix).ogg",
						"Lena Raine - Pigstep (Stereo Mix).ogg",
						"Lena Raine - otherside.ogg",
						"Samuel Åberg - Five.ogg",
						""
				);
				Files.write(blacklistFile.toPath(), template);
				LOGGER.info("[MusicShuffle] Created blacklist template at: {}", blacklistFile.getAbsolutePath());
			} catch (IOException e) {
				LOGGER.error("[MusicShuffle] Failed to write blacklist template: {}", e.getMessage());
			}
			return;
		}

		try {
			java.util.Set<String> blacklist = new java.util.HashSet<>();
			for (String line : Files.readAllLines(blacklistFile.toPath())) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
				blacklist.add(trimmed);
			}
			musicPlayer.setBlacklist(blacklist);
			LOGGER.info("[MusicShuffle] Loaded blacklist with {} entr(ies).", blacklist.size());
		} catch (IOException e) {
			LOGGER.error("[MusicShuffle] Failed to load blacklist: {}", e.getMessage());
		}
	}

	public static void saveBlacklist() {
		if (blacklistFile == null) return;
		try {
			java.util.List<String> output = new java.util.ArrayList<>();

			if (blacklistFile.exists()) {
				for (String line : Files.readAllLines(blacklistFile.toPath())) {
					String trimmed = line.trim();
					if (trimmed.startsWith("#") || trimmed.isEmpty()) {
						output.add(line);
					}
				}
			}

			java.util.List<String> entries = new java.util.ArrayList<>(musicPlayer.getBlacklist());
			java.util.Collections.sort(entries);
			output.addAll(entries);

			Files.write(blacklistFile.toPath(), output);
		} catch (IOException e) {
			LOGGER.error("[MusicShuffle] Failed to save blacklist: {}", e.getMessage());
		}
	}

	public static void toggleTrack(String fileName) {
		java.util.Set<String> blacklist = new java.util.HashSet<>(musicPlayer.getBlacklist());
		if (blacklist.contains(fileName)) {
			blacklist.remove(fileName);
		} else {
			blacklist.add(fileName);
			if (fileName.equals(musicPlayer.getCurrentTrackName() + ".wav")
					|| fileName.equals(musicPlayer.getCurrentTrackName() + ".ogg")) {
				musicPlayer.skip();
			}
		}
		musicPlayer.setBlacklist(blacklist);
		saveBlacklist();
	}
}