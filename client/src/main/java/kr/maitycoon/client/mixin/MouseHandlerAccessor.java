package kr.maitycoon.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the development capture put the pointer on a button. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos") void maitycoon$setX(double x);
    @Accessor("ypos") void maitycoon$setY(double y);
}
