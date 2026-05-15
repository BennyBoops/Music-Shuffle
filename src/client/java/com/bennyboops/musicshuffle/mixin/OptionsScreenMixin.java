package com.bennyboops.musicshuffle.mixin;

import com.bennyboops.musicshuffle.client.MusicControlScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin() {
        super(Component.literal(""));
    }

    @Inject(at = @At("RETURN"), method = "init")
    private void onInit(CallbackInfo ci) {
        for (var listener : this.children()) {
            if (!(listener instanceof AbstractWidget widget)) continue;
            if (!widget.getMessage().getString().equals(
                    Component.translatable("options.sounds").getString())) continue;

            this.addRenderableWidget(
                    Button.builder(
                                    Component.literal("♫"),
                                    btn -> this.minecraft.setScreen(new MusicControlScreen(this)))
                            .bounds(widget.getX() + widget.getWidth() + 2, widget.getY(), 20, 20)
                            .build()
            );
            break;
        }
    }
}