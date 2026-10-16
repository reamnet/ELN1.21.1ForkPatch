package mods.eln.sixnode.lampsupply;

import mods.eln.cable.CableItemSlot;
import mods.eln.i18n.I18N;
import mods.eln.misc.BasicContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

public class LampSupplyContainer extends BasicContainer {

    public static final int cableSlotId = 0;
    public static final double requiredCableLength = 1.0; // This applies only for utility cables

    public LampSupplyContainer(Player player, Container inventory) {
        super(player, inventory, new Slot[]{new CableItemSlot(inventory, cableSlotId, 184, 170, 64, false,
                requiredCableLength, I18N.tr("Cable slot\nSelect the wireless power range in the GUI.\nCable quantity does not change the range.").split("\n"))});
    }

}
