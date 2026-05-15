package com.bennyboops.musicshuffle.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackConfigScreen extends Screen {

    private final Screen parent;
    private TrackList trackList;

    private static final int COLOUR_TITLE = 0xFFFFFFFF;

    private static final Map<String, Boolean> collapsedState = new HashMap<>();

    // ALBUM COLOURS
    private static final java.util.Map<String, Integer> ALBUM_COLOURS;
    static {
        ALBUM_COLOURS = new java.util.LinkedHashMap<>();
        ALBUM_COLOURS.put("Unsorted",      0xFF888888);   // Grey
        ALBUM_COLOURS.put("Volume Alpha",  0xFF55FF55);   // Green
        ALBUM_COLOURS.put("Volume Beta",   0xFFFFA500);   // Orange
        ALBUM_COLOURS.put("Aquatic Update", 0xFF5555FF);  // Blue
        ALBUM_COLOURS.put("Nether Update", 0xFFAA0000);   // Dark Red
        ALBUM_COLOURS.put("Caves & Cliffs", 0xFFAA00AA);  // Purple
        ALBUM_COLOURS.put("The Wild Update", 0xFF00AAAA); // Dark Aqua
        ALBUM_COLOURS.put("Trails & Tales", 0xFFFF55FF);  // Light Purple
        ALBUM_COLOURS.put("Tricky Trials", 0xFFFFFF55);   // Yellow
        ALBUM_COLOURS.put("Chase the Skies", 0xFF55FFFF); // Aqua
        ALBUM_COLOURS.put("Chaos Cubed", 0xFFFF5555);     // Red
    }

    private static final java.util.Map<String, Integer> generatedColourCache = new java.util.HashMap<>();

    public static int albumColour(String albumName) {
        Integer hardcoded = ALBUM_COLOURS.get(albumName);
        if (hardcoded != null) return hardcoded;
        return generatedColourCache.computeIfAbsent(albumName, TrackConfigScreen::generateColour);
    }

    private static int generateColour(String name) {
        int hash = name.hashCode();
        float hue = Math.abs(hash % 360) / 360f;
        float sat = 0.50f + (Math.abs(hash >> 8)  % 25) / 100f;
        float bri = 0.78f + (Math.abs(hash >> 16) % 17) / 100f;
        java.awt.Color c = java.awt.Color.getHSBColor(hue, sat, bri);
        return 0xFF000000 | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private static int tintBackground(int argb, float strength) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b =  argb        & 0xFF;
        int base = 0x22;
        r = Math.min(255, (int)(base + (r - base) * strength));
        g = Math.min(255, (int)(base + (g - base) * strength));
        b = Math.min(255, (int)(base + (b - base) * strength));
        return 0xB0000000 | (r << 16) | (g << 8) | b;
    }

    public static void populateTrackColours() {
        MusicShuffleClient.trackColours.clear();

        File[] topLevel = MusicShuffleClient.musicFolder.listFiles();
        if (topLevel == null) return;

        int unsortedColour = albumColour("Unsorted");
        for (File f : topLevel) {
            if (f.isFile()) {
                String lower = f.getName().toLowerCase();
                if (lower.endsWith(".wav") || lower.endsWith(".ogg")) {
                    MusicShuffleClient.trackColours.put(stripExtStatic(f.getName()), unsortedColour);
                }
            }
        }

        for (File folder : topLevel) {
            if (!folder.isDirectory()) continue;
            int colour = albumColour(folder.getName());
            File[] contents = folder.listFiles();
            if (contents == null) continue;
            for (File f : contents) {
                if (f.isFile()) {
                    String lower = f.getName().toLowerCase();
                    if (lower.endsWith(".wav") || lower.endsWith(".ogg")) {
                        MusicShuffleClient.trackColours.put(stripExtStatic(f.getName()), colour);
                    }
                }
            }
        }
    }

    public TrackConfigScreen(Screen parent) {
        super(Component.literal("Configure Tracks"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        searchBox = new net.minecraft.client.gui.components.EditBox(
                font, width / 2 - 100, 8, 200, 16,
                Component.literal("Search...")
        );
        searchBox.setHint(Component.literal("Search..."));
        addRenderableWidget(searchBox);

        trackList = new TrackList(minecraft, width, height - 64, 32, 26);
        addWidget(trackList);

        addRenderableWidget(Button.builder(
                        Component.literal("Done"),
                        btn -> onClose())
                .bounds(width / 2 - 100, height - 26, 200, 20)
                .build()
        );

        addRenderableWidget(Button.builder(
                        Component.literal("\uD83D\uDCBE Folder"),
                        btn -> net.minecraft.util.Util.getPlatform()
                                .openUri(MusicShuffleClient.musicFolder.toURI()))
                .bounds(width / 2 - 164, height - 26, 60, 20)
                .tooltip(Tooltip.create(Component.literal("Open the music-shuffle folder")))
                .build()
        );

        addRenderableWidget(Button.builder(
                        toastToggleLabel(),
                        btn -> {
                            MusicShuffleClient.toastsEnabled = !MusicShuffleClient.toastsEnabled;
                            btn.setMessage(toastToggleLabel());
                        })
                .bounds(width / 2 + 104, height - 26, 60, 20)
                .tooltip(Tooltip.create(Component.literal("Toggles the now-playing banner")))
                .build()
        );
    }

    private Component toastToggleLabel() {
        return MusicShuffleClient.toastsEnabled
                ? Component.literal("Toast: On").withStyle(s -> s.withColor(0x55FF55))
                : Component.literal("Toast: Off").withStyle(s -> s.withColor(0xFF5555));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        trackList.extractRenderState(graphics, mouseX, mouseY, delta);

        String title = "";
        graphics.text(font, title, (width - font.width(title)) / 2, 12, COLOUR_TITLE, true);

        trackList.filter(searchBox.getValue());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private net.minecraft.client.gui.components.EditBox searchBox;

    static final int SMALL_BTN_W = 28;
    static final int BTN_GAP     =  4;
    static final int BTN_H       = 20;


    class TrackList extends ObjectSelectionList<TrackList.AbstractEntry> {

        private List<AbstractEntry> allEntries = new ArrayList<>();
        private String lastFilter = null;

        public TrackList(net.minecraft.client.Minecraft client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            refresh();
        }

        public void refresh() {
            allEntries.clear();

            File[] topLevel = MusicShuffleClient.musicFolder.listFiles();
            if (topLevel == null) return;

            List<File> rootTracks = new ArrayList<>();
            List<File> subFolders = new ArrayList<>();

            for (File f : topLevel) {
                if (f.isDirectory()) subFolders.add(f);
                else if (f.isFile()) {
                    String lower = f.getName().toLowerCase();
                    if (lower.endsWith(".wav") || lower.endsWith(".ogg")) rootTracks.add(f);
                }
            }

            rootTracks.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            subFolders.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            if (!rootTracks.isEmpty()) {
                int colour = albumColour("Unsorted");
                List<TrackEntry> entries = new ArrayList<>();
                for (File f : rootTracks) {
                    TrackEntry te = new TrackEntry(f.getName(), f, colour);
                    entries.add(te);
                    allEntries.add(te);
                }
                allEntries.add(0, new CategoryEntry("Unsorted", entries, colour));
            }

            for (File folder : subFolders) {
                List<File> tracks = new ArrayList<>();
                File[] contents = folder.listFiles();
                if (contents != null) {
                    for (File f : contents) {
                        String lower = f.getName().toLowerCase();
                        if (f.isFile() && (lower.endsWith(".wav") || lower.endsWith(".ogg"))) tracks.add(f);
                    }
                }
                if (tracks.isEmpty()) continue;
                tracks.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                int colour = albumColour(folder.getName());
                List<TrackEntry> entries = new ArrayList<>();
                for (File f : tracks) {
                    entries.add(new TrackEntry(f.getName(), f, colour));
                }
                allEntries.add(new CategoryEntry(folder.getName(), entries, colour));
                allEntries.addAll(entries);
            }

            populateTrackColours();
            filter(lastFilter == null ? "" : lastFilter);
        }

        @Override
        public void setSelected(@Nullable AbstractEntry entry) {
            super.setSelected(null);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, AbstractEntry entry, int outlineColor) {
        }

        public void filter(String query) {
            if (query.equals(lastFilter)) return;
            lastFilter = query;
            rebuildVisible();
        }

        @Override
        public int getRowWidth() { return width - 20; }

        @Override
        protected int scrollBarX() { return width - 6; }

        abstract class AbstractEntry extends ObjectSelectionList.Entry<AbstractEntry> {
            @Override
            public Component getNarration() { return Component.empty(); }
        }


        class CategoryEntry extends AbstractEntry {

            private final String name;
            private final List<TrackEntry> tracks;
            final int colour;

            private final Button shuffleBtn;
            private final Button toggleBtn;

            CategoryEntry(String name, List<TrackEntry> tracks, int colour) {
                this.name   = name;
                this.tracks = tracks;
                this.colour = colour;

                shuffleBtn = Button.builder(Component.literal("⇄"), btn -> {})
                        .bounds(0, 0, SMALL_BTN_W, BTN_H)
                        .tooltip(Tooltip.create(Component.literal("Shuffle Album")))
                        .build();

                toggleBtn = Button.builder(Component.literal(""), btn -> {})
                        .bounds(0, 0, SMALL_BTN_W, BTN_H)
                        .tooltip(Tooltip.create(Component.literal("Include In Shuffle")))
                        .build();
            }


            private boolean isCollapsed() {
                return collapsedState.getOrDefault(name, false);
            }

            private void setCollapsed(boolean value) {
                collapsedState.put(name, value);
            }

            private boolean allEnabled() {
                return tracks.stream().noneMatch(t ->
                        MusicShuffleClient.musicPlayer.getBlacklist().contains(t.fileName));
            }

            private boolean noneEnabled() {
                return tracks.stream().allMatch(t ->
                        MusicShuffleClient.musicPlayer.getBlacklist().contains(t.fileName));
            }

            private int toggleBtnX(int rowLeft, int rowWidth) {
                return rowLeft + rowWidth - 2 - SMALL_BTN_W;
            }

            private int shuffleBtnX(int rowLeft, int rowWidth) {
                return toggleBtnX(rowLeft, rowWidth) - BTN_GAP - SMALL_BTN_W;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
                int y        = TrackList.this.getRowTop(children().indexOf(this));
                int x        = TrackList.this.getRowLeft();
                int rowWidth = TrackList.this.getRowWidth();
                int btnY     = y + (26 - BTN_H) / 2 - 1;

                String arrow = isCollapsed() ? "▶  " : "▼  ";
                int labelColour = colour | 0xFF000000;
                Component label = Component.literal(arrow + name)
                        .withStyle(s -> s.withBold(true).withColor(labelColour));
                graphics.text(TrackConfigScreen.this.font, label, x + 4, y + 8, labelColour, false);

                // Shuffle button
                int shuffleX = shuffleBtnX(x, rowWidth);
                shuffleBtn.setX(shuffleX);
                shuffleBtn.setY(btnY);
                shuffleBtn.extractRenderState(graphics, mouseX, mouseY, delta);

                // Toggle button
                Component toggleMsg;
                if (allEnabled()) {
                    toggleMsg = Component.literal("☒").withStyle(s -> s.withColor(0x55FF55)); // green
                } else if (noneEnabled()) {
                    toggleMsg = Component.literal("☐").withStyle(s -> s.withColor(0xFF5555)); // red
                } else {
                    toggleMsg = Component.literal("☒").withStyle(s -> s.withColor(0xFFFFFF)); // white
                }
                toggleBtn.setMessage(toggleMsg);
                int toggleX = toggleBtnX(x, rowWidth);
                toggleBtn.setX(toggleX);
                toggleBtn.setY(btnY);
                toggleBtn.extractRenderState(graphics, mouseX, mouseY, delta);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                int y        = TrackList.this.getRowTop(children().indexOf(this));
                int x        = TrackList.this.getRowLeft();
                int rowWidth = TrackList.this.getRowWidth();
                int btnY     = y + (26 - BTN_H) / 2 - 1;

                double mx = event.x();
                double my = event.y();

                // Toggle button
                int toggleX = toggleBtnX(x, rowWidth);
                if (mx >= toggleX && mx <= toggleX + SMALL_BTN_W && my >= btnY && my <= btnY + BTN_H) {
                    boolean shouldEnable = !allEnabled();
                    for (TrackEntry t : tracks) {
                        boolean currently = !MusicShuffleClient.musicPlayer.getBlacklist().contains(t.fileName);
                        if (currently != shouldEnable) MusicShuffleClient.toggleTrack(t.fileName);
                    }
                    playClick();
                    return true;
                }

                // Shuffle button
                int shuffleX = shuffleBtnX(x, rowWidth);
                if (mx >= shuffleX && mx <= shuffleX + SMALL_BTN_W && my >= btnY && my <= btnY + BTN_H) {
                    MusicShuffleClient.musicPlayer.reshuffleAlbum(name);
                    if (minecraft != null)
                        MusicShuffleClient.showNowPlayingToast(minecraft, "Shuffling: " + name);
                    playClick();
                    return true;
                }

                // Collapse / expand
                setCollapsed(!isCollapsed());
                rebuildVisible();
                playClick();
                return true;
            }

            private void playClick() {
                if (minecraft != null) minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }

            @Override
            public Component getNarration() { return Component.literal(name); }
        }

        public void rebuildVisible() {
            clearEntries();
            String lower = lastFilter == null ? "" : lastFilter.toLowerCase();
            boolean currentCollapsed = false;

            for (AbstractEntry e : allEntries) {
                if (e instanceof CategoryEntry ce) {
                    currentCollapsed = ce.isCollapsed();
                    if (lower.isEmpty()) addEntry(ce);
                } else if (e instanceof TrackEntry te) {
                    if (!lower.isEmpty()) {
                        if (te.fileName.toLowerCase().contains(lower)) addEntry(te);
                    } else if (!currentCollapsed) {
                        addEntry(te);
                    }
                }
            }
        }

        class TrackEntry extends AbstractEntry {

            private final String fileName;
            private final File   file;
            final int colour;

            private final Button playBtn;
            private final Button queueNextBtn;
            private final Button toggleBtn;

            TrackEntry(String fileName, File file, int colour) {
                this.fileName = fileName;
                this.file     = file;
                this.colour   = colour;

                playBtn = Button.builder(Component.literal("▶"), btn -> {})
                        .bounds(0, 0, SMALL_BTN_W, BTN_H)
                        .tooltip(Tooltip.create(Component.literal("Play Now")))
                        .build();

                queueNextBtn = Button.builder(Component.literal("+"), btn -> {})
                        .bounds(0, 0, SMALL_BTN_W, BTN_H)
                        .tooltip(Tooltip.create(Component.literal("Queue Next")))
                        .build();

                toggleBtn = Button.builder(Component.literal(""), btn -> {})
                        .bounds(0, 0, SMALL_BTN_W, BTN_H)
                        .tooltip(Tooltip.create(Component.literal("Include In Shuffle")))
                        .build();
            }

            private boolean isEnabled() {
                return !MusicShuffleClient.musicPlayer.getBlacklist().contains(fileName);
            }

            private int toggleBtnX(int rowLeft, int rowWidth) {
                return rowLeft + rowWidth - 2 - SMALL_BTN_W;
            }

            private int queueNextBtnX(int rowLeft, int rowWidth) {
                return toggleBtnX(rowLeft, rowWidth) - BTN_GAP - SMALL_BTN_W;
            }

            private int playBtnX(int rowLeft, int rowWidth) {
                return queueNextBtnX(rowLeft, rowWidth) - BTN_GAP - SMALL_BTN_W;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
                int absoluteY = TrackList.this.getRowTop(children().indexOf(this));
                int absoluteX = TrackList.this.getRowLeft();
                int rowWidth  = TrackList.this.getRowWidth();
                boolean enabled = isEnabled();
                int x2   = absoluteX + rowWidth;
                int btnY = absoluteY + (26 - BTN_H) / 2 - 1;

                graphics.fill(absoluteX - 2, absoluteY, x2 + 2, absoluteY + 24, tintBackground(colour, 0.4f));
                graphics.fill(absoluteX - 2, absoluteY,      x2 + 2, absoluteY + 1,  0xFF888888);
                graphics.fill(absoluteX - 2, absoluteY + 23, x2 + 2, absoluteY + 24, 0xFF888888);
                graphics.fill(absoluteX - 2, absoluteY,      absoluteX - 1, absoluteY + 24, 0xFF888888);
                graphics.fill(x2 + 1,        absoluteY,      x2 + 2,        absoluteY + 24, 0xFF888888);

                int nameColour = enabled ? 0xFFFFFFFF : 0xFF888888;
                graphics.text(TrackConfigScreen.this.font, stripExtension(fileName),
                        absoluteX + 4, absoluteY + 8, nameColour, true);

                // Play button. Icon turns green when this track is currently playing
                String current = MusicShuffleClient.musicPlayer.getCurrentTrackName();
                boolean isPlaying = fileName.equals(current + ".wav") || fileName.equals(current + ".ogg");
                playBtn.setMessage(isPlaying
                        ? Component.literal("▶").withStyle(s -> s.withColor(0x55FF55))
                        : Component.literal("▶"));
                int playX = playBtnX(absoluteX, rowWidth);
                playBtn.setX(playX);
                playBtn.setY(btnY);
                playBtn.extractRenderState(graphics, mouseX, mouseY, delta);

                // Queue-next button. Turns yellow when this track is already queued next
                List<String> snapshot = MusicShuffleClient.musicPlayer.getQueueSnapshot();
                boolean isNextUp = !snapshot.isEmpty()
                        && snapshot.get(0).equals(stripExtension(fileName));
                queueNextBtn.setMessage(isNextUp
                        ? Component.literal("+").withStyle(s -> s.withColor(0xFFFF55))
                        : Component.literal("+"));
                int queueNextX = queueNextBtnX(absoluteX, rowWidth);
                queueNextBtn.setX(queueNextX);
                queueNextBtn.setY(btnY);
                queueNextBtn.extractRenderState(graphics, mouseX, mouseY, delta);

                // Toggle button
                toggleBtn.setMessage(enabled
                        ? Component.literal("☒").withStyle(s -> s.withColor(0x55FF55))
                        : Component.literal("☐").withStyle(s -> s.withColor(0xFF5555)));
                int toggleX = toggleBtnX(absoluteX, rowWidth);
                toggleBtn.setX(toggleX);
                toggleBtn.setY(btnY);
                toggleBtn.extractRenderState(graphics, mouseX, mouseY, delta);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                int absoluteY = TrackList.this.getRowTop(children().indexOf(this));
                int absoluteX = TrackList.this.getRowLeft();
                int rowWidth  = TrackList.this.getRowWidth();
                int btnY      = absoluteY + (26 - BTN_H) / 2 - 1;

                double mx = event.x();
                double my = event.y();

                // Toggle button
                int toggleX = toggleBtnX(absoluteX, rowWidth);
                if (mx >= toggleX && mx <= toggleX + SMALL_BTN_W && my >= btnY && my <= btnY + BTN_H) {
                    MusicShuffleClient.toggleTrack(fileName);
                    playClick();
                    return true;
                }

                // Queue-next button
                int queueNextX = queueNextBtnX(absoluteX, rowWidth);
                if (mx >= queueNextX && mx <= queueNextX + SMALL_BTN_W && my >= btnY && my <= btnY + BTN_H) {
                    MusicShuffleClient.musicPlayer.queueNext(file);
                    playClick();
                    return true;
                }

                // Play button
                int playX = playBtnX(absoluteX, rowWidth);
                if (mx >= playX && mx <= playX + SMALL_BTN_W && my >= btnY && my <= btnY + BTN_H) {
                    MusicShuffleClient.musicPlayer.playTrackNow(file);
                    if (minecraft != null)
                        MusicShuffleClient.showNowPlayingToast(minecraft, stripExtension(fileName));
                    playClick();
                    return true;
                }

                return false;
            }

            private void playClick() {
                if (minecraft != null) minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }

            @Override
            public Component getNarration() { return Component.literal(fileName); }

            private String stripExtension(String name) {
                int dot = name.lastIndexOf('.');
                return dot > 0 ? name.substring(0, dot) : name;
            }
        }
    }

    private static String stripExtStatic(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}