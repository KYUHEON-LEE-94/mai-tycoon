package kr.maitycoon.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Mai Tycoon menus paint their buttons across several slots, so the game's one-slot hover box cut buttons into
 * squares. In those menus the box covers the whole button instead, and painted background slots get none.
 * A wide button is the run of neighbouring slots the server filled with the same item.
 */
public final class MenuHover {
    private static final int COLUMN = 18, SIZE = 16, COLOR = 0x60FFFFFF;
    private MenuHover() {}
    /** Only screens titled in the server's own fonts are Mai Tycoon menus; ordinary chests keep the usual hover. */
    static boolean ours(Component title) {
        return title.visit((style, text) -> style.getFont() instanceof FontDescription.Resource resource && resource.id().getNamespace().equals("maitycoon")
            ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty(), net.minecraft.network.chat.Style.EMPTY).orElse(false);
    }
    public static boolean handles(Component title, Slot hovered) {
        return hovered != null && !(hovered.container instanceof Inventory) && ours(title);
    }
    public static void draw(GuiGraphics graphics, AbstractContainerMenu menu, Slot hovered) {
        ItemStack item = hovered.getItem();
        if (item.isEmpty()) return;
        List<Slot> group = group(menu, hovered);
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (Slot slot : group) {
            left = Math.min(left, slot.x);
            top = Math.min(top, slot.y);
            right = Math.max(right, slot.x + SIZE);
            bottom = Math.max(bottom, slot.y + SIZE);
        }
        // A wide button also covers the two-pixel gaps between its slots.
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, COLOR);
    }
    static List<Slot> group(AbstractContainerMenu menu, Slot start) {
        List<Slot> found = new ArrayList<>(List.of(start));
        ArrayDeque<Slot> next = new ArrayDeque<>(found);
        while (!next.isEmpty()) {
            Slot here = next.poll();
            for (Slot other : menu.slots) {
                if (found.contains(other) || other.container != start.container) continue;
                boolean beside = Math.abs(other.x - here.x) + Math.abs(other.y - here.y) == COLUMN;
                if (beside && ItemStack.isSameItemSameComponents(other.getItem(), start.getItem())) {
                    found.add(other);
                    next.add(other);
                }
            }
        }
        return found;
    }
}
