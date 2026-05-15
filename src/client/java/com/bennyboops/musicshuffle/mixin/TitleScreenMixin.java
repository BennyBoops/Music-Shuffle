package com.bennyboops.musicshuffle.mixin;

import com.bennyboops.musicshuffle.client.MusicControlScreen;
import com.bennyboops.musicshuffle.client.MusicShuffleClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() {
        super(Component.literal(""));
    }

    @Inject(at = @At("RETURN"), method = "init")
    private void onInit(CallbackInfo ci) {
        this.addRenderableWidget(
                Button.builder(
                                Component.literal("♫"),
                                btn -> this.minecraft.setScreen(new MusicControlScreen(this.minecraft.screen)))
                        .bounds(this.width / 2 + 128, this.height / 4 + 132, 20, 20)
                        .build()
        );
    }
}