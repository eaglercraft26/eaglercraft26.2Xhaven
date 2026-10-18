package net.minecraft.world.inventory.tooltip;

import net.minecraft.world.item.component.ItemContainerContents;

public record ShulkerBoxTooltip(ItemContainerContents contents) implements TooltipComponent {
}
