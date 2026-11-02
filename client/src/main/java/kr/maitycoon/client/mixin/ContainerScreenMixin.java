package kr.maitycoon.client.mixin;

import kr.maitycoon.client.MenuHover;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {
    @Shadow protected Slot hoveredSlot;
    @Shadow @Final protected AbstractContainerMenu menu;
    protected ContainerScreenMixin(Component title) { super(title); }
    @Inject(method = "renderSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void maitycoon$back(GuiGraphics graphics, CallbackInfo info) {
        if (MenuHover.handles(title, hoveredSlot)) info.cancel();
    }
    @Inject(method = "renderSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void maitycoon$front(GuiGraphics graphics, CallbackInfo info) {
        if (!MenuHover.handles(title, hoveredSlot)) return;
        MenuHover.draw(graphics, menu, hoveredSlot);
        info.cancel();
    }
}
