package com.bennyboops.musicshuffle.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModConfigScreen extends Screen {

    private final Screen parent;

    private static final int BTN_W    = 100;
    private static final int BTN_H    = 20;
    private static final int ITEM_H   = 26;
    private static final int ROW_W    = 310;
    private static final int PAD_X    = 8;
    private static final int TITLE_COLOUR = 0xFFFFFFFF;

    private Button modEnabledBtn;
    private Button toastModeBtn;
    private EditBox toastDelayBox;
    private EditBox trackDelayMinBox;
    private EditBox trackDelayMaxBox;
    private Button dimensionShuffleBtn;
    private Button skipOnDimensionChangeBtn;

    private int row0Y, row1Y, row2Y, row3Y, row4Y, row5Y, row6Y, rowsX;

    public ModConfigScreen(Screen parent) {
        super(Component.literal("Music Shuffle Config"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        rowsX = width / 2 - ROW_W / 2;
        int totalH = 7 * ITEM_H + 6 * 2;
        row0Y = height / 2 - totalH / 2;
        row1Y = row0Y + ITEM_H + 2;
        row2Y = row1Y + ITEM_H + 2;
        row3Y = row2Y + ITEM_H + 2;
        row4Y = row3Y + ITEM_H + 2;
        row5Y = row4Y + ITEM_H + 2;
        row6Y = row5Y + ITEM_H + 2;

        int btnX   = rowsX + ROW_W - PAD_X - BTN_W;
        int btnOffY = (ITEM_H - BTN_H) / 2;

        modEnabledBtn = Button.builder(
                        modEnabledLabel(),
                        btn -> {
                            MusicShuffleClient.modEnabled = !MusicShuffleClient.modEnabled;
                            btn.setMessage(modEnabledLabel());
                        })
                .bounds(btnX, row0Y + btnOffY, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal("Enable or disable the music shuffle mod")))
                .build();
        addRenderableWidget(modEnabledBtn);

        toastModeBtn = Button.builder(
                        toastModeLabel(),
                        btn -> {
                            MusicShuffleClient.toastMode = switch (MusicShuffleClient.toastMode) {
                                case OFF    -> MusicShuffleClient.ToastMode.BANNER;
                                case BANNER -> MusicShuffleClient.ToastMode.SMALL;
                                case SMALL  -> MusicShuffleClient.ToastMode.OFF;
                            };
                            btn.setMessage(toastModeLabel());
                        })
                .bounds(btnX, row1Y + btnOffY, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal(
                        "Off: no notification  |  Banner: top-left box  |  Small: text above hotbar")))
                .build();
        addRenderableWidget(toastModeBtn);

        toastDelayBox = new EditBox(
                font,
                btnX, row2Y + btnOffY,
                BTN_W, BTN_H,
                Component.literal("Toast duration (ms)")
        );
        toastDelayBox.setMaxLength(6);
        toastDelayBox.setValue(String.valueOf(MusicShuffleClient.toastDisplayMs));
        toastDelayBox.setResponder(value -> {
            try {
                long ms = Long.parseLong(value.trim());
                if (ms >= 500L && ms <= 10000L) {
                    MusicShuffleClient.toastDisplayMs = ms;
                }
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(toastDelayBox);

        trackDelayMinBox = new EditBox(
                font,
                btnX, row3Y + btnOffY,
                BTN_W, BTN_H,
                Component.literal("Min delay (ms)")
        );
        trackDelayMinBox.setMaxLength(7);
        trackDelayMinBox.setValue(String.valueOf(MusicShuffleClient.trackDelayMinMs));
        trackDelayMinBox.setResponder(value -> {
            try {
                long ms = Long.parseLong(value.trim());
                if (ms >= 0L && ms <= 300000L) {
                    MusicShuffleClient.trackDelayMinMs = ms;
                }
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(trackDelayMinBox);

        trackDelayMaxBox = new EditBox(
                font,
                btnX, row4Y + btnOffY,
                BTN_W, BTN_H,
                Component.literal("Max delay (ms)")
        );
        trackDelayMaxBox.setMaxLength(7);
        trackDelayMaxBox.setValue(String.valueOf(MusicShuffleClient.trackDelayMaxMs));
        trackDelayMaxBox.setResponder(value -> {
            try {
                long ms = Long.parseLong(value.trim());
                if (ms >= 0L && ms <= 300000L) {
                    MusicShuffleClient.trackDelayMaxMs = ms;
                }
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(trackDelayMaxBox);

        dimensionShuffleBtn = Button.builder(
                        dimensionShuffleLabel(),
                        btn -> {
                            MusicShuffleClient.dimensionShuffleEnabled =
                                    !MusicShuffleClient.dimensionShuffleEnabled;
                            btn.setMessage(dimensionShuffleLabel());
                            skipOnDimensionChangeBtn.active = MusicShuffleClient.dimensionShuffleEnabled;
                        })
                .bounds(btnX, row5Y + btnOffY, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal(
                        "When enabled, entering a dimension only plays albums mapped to that dimension.\n"
                                + "Configure mappings in config/music-shuffle-dimensions.properties.")))
                .build();
        addRenderableWidget(dimensionShuffleBtn);

        skipOnDimensionChangeBtn = Button.builder(
                        skipOnDimensionChangeLabel(),
                        btn -> {
                            MusicShuffleClient.skipOnDimensionChange =
                                    !MusicShuffleClient.skipOnDimensionChange;
                            btn.setMessage(skipOnDimensionChangeLabel());
                        })
                .bounds(btnX, row6Y + btnOffY, BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.literal(
                        "Skip: the current song stops immediately when you enter a portal.\n"
                                + "Finish: the current song plays to the end before the new dimension's queue starts.")))
                .build();
        skipOnDimensionChangeBtn.active = MusicShuffleClient.dimensionShuffleEnabled;
        addRenderableWidget(skipOnDimensionChangeBtn);

        addRenderableWidget(Button.builder(
                        Component.literal("Done"),
                        btn -> onClose())
                .bounds(width / 2 - 100, height - 26, 200, BTN_H)
                .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String title = "Music Shuffle Config";
        graphics.text(font, title, (width - font.width(title)) / 2, 20, TITLE_COLOUR, true);

        drawRow(graphics, row0Y, "Mod Enabled");
        drawRow(graphics, row1Y, "Toast Style");
        drawRow(graphics, row2Y, "Toast Duration (ms)");
        drawRow(graphics, row3Y, "Delay Between Songs - Min (ms)");
        drawRow(graphics, row4Y, "Delay Between Songs - Max (ms)");
        drawRow(graphics, row5Y, "Dimension Shuffle");
        drawRow(graphics, row6Y, "Skip Song on Dimension Change");
    }

    private void drawRow(GuiGraphicsExtractor graphics, int y, String label) {
        int x  = rowsX;
        int x2 = x + ROW_W;
        int y2 = y + ITEM_H;

        graphics.fill(x + 1, y,      x2 - 1, y2,      0x00000000);
        graphics.fill(x + 1, y,      x2 - 1, y + 1,   0xFF888888);
        graphics.fill(x + 1, y2 - 1, x2 - 1, y2,      0xFF888888);
        graphics.fill(x,     y,      x + 1,  y2,       0xFF888888);
        graphics.fill(x2 - 1, y,     x2,     y2,       0xFF888888);

        int textY = y + (ITEM_H - font.lineHeight) / 2;
        graphics.text(font, label, x + PAD_X, textY, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        ModConfig cfg = ModConfig.get();
        cfg.pullFromRuntime();
        cfg.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }


    private Component modEnabledLabel() {
        return MusicShuffleClient.modEnabled
                ? Component.literal("Enabled").withStyle(s -> s.withColor(0x55FF55))
                : Component.literal("Disabled").withStyle(s -> s.withColor(0xFF5555));
    }

    private Component toastModeLabel() {
        return switch (MusicShuffleClient.toastMode) {
            case OFF    -> Component.literal("Off").withStyle(s -> s.withColor(0xFF5555));
            case BANNER -> Component.literal("Banner").withStyle(s -> s.withColor(0x55FF55));
            case SMALL  -> Component.literal("Small").withStyle(s -> s.withColor(0x55FF55));
        };
    }

    private Component dimensionShuffleLabel() {
        return MusicShuffleClient.dimensionShuffleEnabled
                ? Component.literal("Enabled").withStyle(s -> s.withColor(0x55FF55))
                : Component.literal("Disabled").withStyle(s -> s.withColor(0xFF5555));
    }

    private Component skipOnDimensionChangeLabel() {
        return MusicShuffleClient.skipOnDimensionChange
                ? Component.literal("Skip").withStyle(s -> s.withColor(0xFFAA00))
                : Component.literal("Finish").withStyle(s -> s.withColor(0x55FFFF));
    }
}