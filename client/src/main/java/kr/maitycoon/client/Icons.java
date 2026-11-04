package kr.maitycoon.client;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

/** Builds the stack a shop row draws: the server names the item and, for its own crops, the model from the pack. */
final class Icons {
    private Icons() {}
    static ItemStack stack(String material, String model, String name) {
        ItemStack stack;
        try { stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(material))); }
        catch (RuntimeException e) { stack = new ItemStack(Items.BARRIER); }
        if (!model.isEmpty()) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(model), List.of()));
        if (!name.isEmpty()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
    static String money(long value) { return String.format("%,d", value); }
}
