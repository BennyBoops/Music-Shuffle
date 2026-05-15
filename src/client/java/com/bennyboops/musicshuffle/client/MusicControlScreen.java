package com.bennyboops.musicshuffle.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MusicControlScreen extends Screen {

    private final Screen parent;

    private static final int BTN_W   = 80;
    private static final int BTN_H   = 20;
    private static final int BTN_GAP = 6;

    private static final int QUEUE_ITEM_H  = 26;
    private static final int QUEUE_TOP_GAP = 10;

    private static final int COLOUR_TITLE    = 0xFFFFFFFF;
    private static final int COLOUR_SUBTITLE = 0xFFAAAAAA;

    private static final int NP_PAD_Y = 7;

    private static final int UD_BTN_W           = 14;
    private static final int UD_BTN_H           = 11;
    private static final int UD_BTN_GAP         =  1;
    private static final int UD_BTN_SCROLL_PAD  =  5;

    private QueueBox queueBox;
    private int btnRowX;
    private int btnRowW;
    private Button playPauseBtn;

    private boolean queueVisible = false;
    private Button queueToggleBtn;

    public MusicControlScreen(Screen parent) {
        super(Component.literal("Music Player Controls"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component loopLabel() {
        if (MusicShuffleClient.musicPlayer == null) {
            return Component.literal("Loop: Off");
        }

        return switch (MusicShuffleClient.musicPlayer.getLoopMode()) {
            case OFF -> Component.literal("Loop: Off");
            case QUEUE -> Component.literal("Loop: Queue");
            case TRACK -> Component.literal("Loop: Track");
        };
    }

    @Override
    protected void init() {
        int centerX = width / 2;

        int totalBtnRow = (BTN_W * 3) + (BTN_GAP * 2);
        int btnRowX = centerX - totalBtnRow / 2;
        this.btnRowX = btnRowX;
        this.btnRowW = totalBtnRow;
        int row1Y = height / 2 - BTN_H - BTN_GAP;
        int row2Y = height / 2;

        addRenderableWidget(Button.builder(
                        Component.literal("⏮"),
                        btn -> MusicShuffleClient.musicPlayer.rewind())
                .bounds(btnRowX, row1Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Rewind")))
                .build()
        );

        playPauseBtn = Button.builder(
                        Component.literal("▶"),
                        btn -> MusicShuffleClient.musicPlayer.togglePause())
                .bounds(btnRowX + BTN_W + BTN_GAP, row1Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Play/Pause")))
                .build();
        addRenderableWidget(playPauseBtn);

        addRenderableWidget(Button.builder(
                        Component.literal("⏭"),
                        btn -> MusicShuffleClient.musicPlayer.skip())
                .bounds(btnRowX + (BTN_W + BTN_GAP) * 2, row1Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Skip")))
                .build()
        );

        addRenderableWidget(Button.builder(
                        Component.literal("☰"),
                        btn -> {
                            if (minecraft != null)
                                minecraft.setScreen(new TrackConfigScreen(this));
                        })
                .bounds(btnRowX, row2Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Config Menu")))
                .build()
        );

        addRenderableWidget(Button.builder(
                        toggleLabel(),
                        btn -> {
                            MusicShuffleClient.modEnabled = !MusicShuffleClient.modEnabled;
                            btn.setMessage(toggleLabel());
                        })
                .bounds(btnRowX + BTN_W + BTN_GAP, row2Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Toggle Mod on/off")))
                .build()
        );

        addRenderableWidget(Button.builder(
                        Component.literal("⇄"),
                        btn -> {
                            MusicShuffleClient.musicPlayer.reshuffle();
                            if (minecraft != null)
                                MusicShuffleClient.showNowPlayingToast(minecraft, "Queue Shuffled");
                        })
                .bounds(btnRowX + (BTN_W + BTN_GAP) * 2, row2Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Shuffle")))
                .build()
        );

        int queueRowY = row2Y + BTN_H + BTN_GAP;

        addRenderableWidget(Button.builder(
                        Component.literal("Clear"),
                        btn -> MusicShuffleClient.musicPlayer.clearQueue())
                .bounds(btnRowX, queueRowY, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Clear Queue")))
                .build()
        );

        queueToggleBtn = Button.builder(
                        Component.literal("Queue"),
                        btn -> {
                            queueVisible = !queueVisible;
                            btn.setMessage(Component.literal(
                                    queueVisible ? "Queue" : "Queue"
                            ));
                        })
                .bounds(
                        btnRowX + BTN_W + BTN_GAP,
                        queueRowY,
                        BTN_W,
                        BTN_H
                )
                .tooltip(Tooltip.create(Component.literal("Show Queue")))

                .build();

        addRenderableWidget(queueToggleBtn);

        Button loopBtn = Button.builder(
                        loopLabel(),
                        btn -> {
                            MusicShuffleClient.musicPlayer.cycleLoopMode();
                            btn.setMessage(loopLabel());
                        })
                .bounds(
                        btnRowX + (BTN_W + BTN_GAP) * 2,
                        queueRowY,
                        BTN_W,
                        BTN_H
                )
                .tooltip(Tooltip.create(Component.literal("Loop")))
                .build();

        addRenderableWidget(loopBtn);

        addRenderableWidget(queueToggleBtn);

        int queueTop = queueToggleBtn.getY() + BTN_H + BTN_GAP;
        int queueH   = (height - 32) - queueTop;

        queueBox = new QueueBox(
                minecraft,
                totalBtnRow,
                queueH,
                queueTop,
                QUEUE_ITEM_H,
                btnRowX
        );

        addWidget(queueBox);

        addRenderableWidget(Button.builder(
                        Component.literal("Done"),
                        btn -> onClose())
                .bounds(centerX - 100, height - 26, 200, 20)
                .build()
        );
    }

    private Component toggleLabel() {
        return MusicShuffleClient.modEnabled
                ? Component.literal("■").withStyle(s -> s.withColor(0xFFFFFF))
                : Component.literal("■").withStyle(s -> s.withColor(0xFF5555));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (playPauseBtn != null && MusicShuffleClient.musicPlayer != null) {
            boolean paused = MusicShuffleClient.musicPlayer.isPaused();
            playPauseBtn.setMessage(paused
                    ? Component.literal("▶/⏸").withStyle(s -> s.withColor(0xFF5555))
                    : Component.literal("▶/⏸").withStyle(s -> s.withColor(0x55FF55)));
        }

        if (minecraft != null && this.children().stream().anyMatch(w -> w instanceof Button)) {
            for (GuiEventListener w : this.children()) {
                if (w instanceof Button b && b.getMessage().getString().startsWith("Loop")) {
                    b.setMessage(loopLabel());
                }
            }
        }

        String title = "Music Player";
        graphics.text(font, title, (width - font.width(title)) / 2, 40, COLOUR_TITLE, true);

        boolean isPlaying = MusicShuffleClient.musicPlayer != null
                && MusicShuffleClient.musicPlayer.isRunning()
                && !MusicShuffleClient.musicPlayer.isPaused();

        String trackLine = buildTrackLine();
        Component trackComp = Component.literal(trackLine).withStyle(s -> s.withBold(false));
        int textW = font.width(trackComp);
        int textH = font.lineHeight;
        int npY   = height / 2 - 50;

        boolean hasTrack = MusicShuffleClient.musicPlayer != null
                && MusicShuffleClient.musicPlayer.isRunning()
                && !MusicShuffleClient.musicPlayer.getCurrentTrackName().isEmpty();

        if (hasTrack) {
            String name = MusicShuffleClient.musicPlayer.getCurrentTrackName();
            int albumColour = MusicShuffleClient.trackColours.getOrDefault(name,
                    TrackConfigScreen.albumColour("Unsorted"));

            int PAD_X    = 8;
            int boxWidth = Math.max(btnRowW, textW + PAD_X * 2);
            int centerX  = btnRowX + btnRowW / 2;
            int boxX1    = centerX - boxWidth / 2;
            int boxX2    = centerX + boxWidth / 2;
            int boxY1 = npY - NP_PAD_Y - 1;
            int boxY2 = npY + textH + NP_PAD_Y - 1;
            int boxH     = boxY2 - boxY1;

            graphics.fill(boxX1, boxY1, boxX2, boxY2, tintBackground(albumColour, 0.4f));
            int border = 0xFF888888;
            graphics.fill(boxX1,     boxY1,     boxX2,     boxY1 + 1, border);
            graphics.fill(boxX1,     boxY2 - 1, boxX2,     boxY2,     border);
            graphics.fill(boxX1,     boxY1,     boxX1 + 1, boxY2,     border);
            graphics.fill(boxX2 - 1, boxY1,     boxX2,     boxY2,     border);

            int textColour = isPlaying ? COLOUR_TITLE : COLOUR_SUBTITLE;
            int centeredX  = boxX1 + (boxWidth - textW) / 2;
            int centeredY  = boxY1 + (boxH - textH) / 2;
            graphics.text(font, trackComp, centeredX, centeredY, textColour, true);
        } else {
            graphics.text(font, trackComp, (width - textW) / 2, npY, COLOUR_SUBTITLE, false);
        }

        if (queueBox != null) {
            queueBox.visible = queueVisible;

            if (queueVisible) {
                queueBox.renderQueue(graphics, mouseX, mouseY, delta);

                int border  = 0xFF888888;
                int qTop    = queueBox.getY() - 2;
                int qBottom = queueBox.getY() + queueBox.getHeight() + 2;
                int qLeft   = btnRowX;
                int qRight  = btnRowX + btnRowW;

                graphics.fill(qLeft,      qTop,        qRight,     qTop + 1, border);
                graphics.fill(qLeft,      qBottom - 1, qRight,     qBottom,  border);
                graphics.fill(qLeft,      qTop,        qLeft + 1,  qBottom,  border);
                graphics.fill(qRight - 1, qTop,        qRight,     qBottom,  border);
            }
        }

        if (hasTrack) {
            int row1Y   = height / 2 - BTN_H - BTN_GAP;
            int barH    = 4;
            int barY    = row1Y - barH - 4;
            int barX1   = btnRowX;
            int barX2   = btnRowX + btnRowW;
            int border  = 0xFF888888;

            graphics.fill(barX1, barY, barX2, barY + barH, 0x44000000);

            long cf = MusicShuffleClient.musicPlayer.getCurrentFrame();
            long tf = MusicShuffleClient.musicPlayer.getTotalFrames();

            if (tf > 0) {
                String name       = MusicShuffleClient.musicPlayer.getCurrentTrackName();
                int   albumColour = MusicShuffleClient.trackColours.getOrDefault(name,
                        TrackConfigScreen.albumColour("Unsorted"));
                float progress = Math.min(1f, (float) cf / tf);
                int   fillW    = (int)(progress * (barX2 - barX1));
                if (fillW > 0)
                    graphics.fill(barX1, barY, barX1 + fillW, barY + barH,
                            albumColour | 0xFF000000);
            } else {
                graphics.fill(barX1, barY, barX2, barY + barH, 0x33FFFFFF);
            }

            graphics.fill(barX1,     barY,          barX2,     barY + 1,      border);
            graphics.fill(barX1,     barY + barH - 1, barX2,   barY + barH,   border);
            graphics.fill(barX1,     barY,          barX1 + 1, barY + barH,   border);
            graphics.fill(barX2 - 1, barY,          barX2,     barY + barH,   border);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { onClose(); return true; }
        if (MusicShuffleClient.controlKey != null
                && MusicShuffleClient.controlKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private String buildTrackLine() {
        if (MusicShuffleClient.musicPlayer == null) return "Not initialised";
        if (!MusicShuffleClient.musicPlayer.isRunning()) return "Music Stopped - Default Music Enabled";
        if (MusicShuffleClient.musicPlayer.isPaused())
            return "Paused  ⏸  " + MusicShuffleClient.musicPlayer.getCurrentTrackName();
        String name = MusicShuffleClient.musicPlayer.getCurrentTrackName();
        return name.isEmpty() ? "Loading..." : "Playing  ♫  " + name;
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

    class QueueBox extends ObjectSelectionList<QueueBox.QueueEntry> {

        private double savedScroll = 0;

        QueueBox(net.minecraft.client.Minecraft client,
                 int width, int height, int top, int itemHeight, int offsetX) {
            super(client, width, height, top, itemHeight);
            setX(offsetX);
        }

        public void renderQueue(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            savedScroll = scrollAmount();

            List<String> snapshot = MusicShuffleClient.musicPlayer == null
                    ? new ArrayList<>()
                    : MusicShuffleClient.musicPlayer.getQueueSnapshot();

            clearEntries();
            for (int i = 0; i < snapshot.size(); i++) {
                addEntry(new QueueEntry(i, snapshot.get(i), snapshot.size()));
            }

            setScrollAmount(savedScroll);
            extractRenderState(graphics, mouseX, mouseY, delta);
        }

        private int stackBtnX(int rowRight) {
            return rowRight - 1 - UD_BTN_W - UD_BTN_SCROLL_PAD;
        }

        private int upBtnY(int rowY) {
            return rowY + 1;
        }

        private int downBtnY(int rowY) {
            return rowY + UD_BTN_H + UD_BTN_GAP;
        }


        @Override
        public Optional<GuiEventListener> getChildAt(double x, double y) {
            List<QueueEntry> entries = children();
            int total = entries.size();

            for (int i = 0; i < total; i++) {
                int rowY     = getRowTop(i);
                int rowRight = getRowLeft() + getRowWidth();
                int btnX     = stackBtnX(rowRight);
                int upY      = upBtnY(rowY);
                int dnY      = downBtnY(rowY);

                if (y < rowY || y > rowY + QUEUE_ITEM_H) continue;

                if (x >= btnX && x <= btnX + UD_BTN_W) {
                    if ((y >= upY && y <= upY + UD_BTN_H)
                            || (y >= dnY && y <= dnY + UD_BTN_H)) {
                        return Optional.of(this);
                    }
                }
            }

            return super.getChildAt(x, y);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mx = event.x();
            double my = event.y();

            List<QueueEntry> entries = children();
            int total = entries.size();

            for (int i = 0; i < total; i++) {
                int rowY     = getRowTop(i);
                int rowRight = getRowLeft() + getRowWidth();
                int btnX     = stackBtnX(rowRight);
                int upY      = upBtnY(rowY);
                int dnY      = downBtnY(rowY);

                if (my < rowY || my > rowY + QUEUE_ITEM_H) continue;

                if (mx >= btnX && mx <= btnX + UD_BTN_W) {
                    if (my >= upY && my <= upY + UD_BTN_H) {
                        if (i > 0) MusicShuffleClient.musicPlayer.reorderQueue(i, i - 1);
                        playQueueClick();
                        return true;
                    }
                    if (my >= dnY && my <= dnY + UD_BTN_H) {
                        if (i < total - 1) MusicShuffleClient.musicPlayer.reorderQueue(i, i + 1);
                        playQueueClick();
                        return true;
                    }
                }
            }

            return super.mouseClicked(event, doubleClick);
        }

        private void playQueueClick() {
            if (minecraft != null) minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }

        @Override
        public void setSelected(@Nullable QueueEntry entry) {
            super.setSelected(null);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, QueueEntry entry, int outlineColor) {
        }

        @Override
        public int getRowWidth()    { return width; }

        @Override
        protected int scrollBarX() { return getX() + width - 6; }

        @Override
        public int getRowLeft()    { return getX(); }

        class QueueEntry extends ObjectSelectionList.Entry<QueueEntry> {

            private final int    index;
            private final int    total;
            private final String trackName;

            private final Button upBtn;
            private final Button downBtn;

            QueueEntry(int index, String trackName, int total) {
                this.index     = index;
                this.total     = total;
                this.trackName = trackName;

                upBtn   = Button.builder(Component.literal("▲"), btn -> {})
                        .bounds(0, 0, UD_BTN_W, UD_BTN_H).build();
                downBtn = Button.builder(Component.literal("▼"), btn -> {})
                        .bounds(0, 0, UD_BTN_W, UD_BTN_H).build();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics,
                                       int mouseX, int mouseY,
                                       boolean hovered, float delta) {

                int y        = QueueBox.this.getRowTop(QueueBox.this.children().indexOf(this));
                int x        = QueueBox.this.getRowLeft();
                int rowWidth = QueueBox.this.getRowWidth();
                int x2       = x + rowWidth;

                int colour = MusicShuffleClient.trackColours.getOrDefault(trackName,
                        TrackConfigScreen.albumColour("Unsorted"));

                int boxTop    = y;
                int boxBottom = y + 24;
                graphics.fill(x + 1, boxTop, x2 - 1, boxBottom, tintBackground(colour, 0.4f));

                int border = 0xFF888888;
                graphics.fill(x + 1,  boxTop,        x2 - 1, boxTop + 1,    border);
                graphics.fill(x + 1,  boxBottom - 1, x2 - 1, boxBottom,     border);
                graphics.fill(x,      boxTop,         x + 1,  boxBottom,     border);
                graphics.fill(x2 - 1, boxTop,         x2,     boxBottom,     border);

                int btnX = stackBtnX(x2);
                int upY  = upBtnY(y);
                int dnY  = downBtnY(y);

                upBtn.active = (index > 0);
                upBtn.setX(btnX);
                upBtn.setY(upY);
                upBtn.extractRenderState(graphics, mouseX, mouseY, delta);

                downBtn.active = (index < total - 1);
                downBtn.setX(btnX);
                downBtn.setY(dnY);
                downBtn.extractRenderState(graphics, mouseX, mouseY, delta);

                int textY   = y + 8;
                int textX   = x + 4;
                String posStr = (index + 1) + ". ";
                graphics.text(MusicControlScreen.this.font, posStr,
                        textX, textY, 0xFFFFFFFF, true);
                graphics.text(MusicControlScreen.this.font, trackName,
                        textX + font.width(posStr), textY, 0xFFFFFFFF, true);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal((index + 1) + ". " + trackName);
            }
        }
    }
}