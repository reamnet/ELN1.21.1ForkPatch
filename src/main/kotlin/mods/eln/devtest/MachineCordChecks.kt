package mods.eln.devtest

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.tagCompound
import mods.eln.node.transparent.*
import mods.eln.sim.mna.component.Resistor
import mods.eln.transparentnode.cord.*
import mods.eln.transparentnode.electricalmachine.ElectricalMachineElement
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

internal object MachineCordChecks {
    private data class Case(val machine: TransparentNodeElement,val outlet: OutletElement,val rating: CordRating)
    private val cases=ArrayList<Case>()
    fun setup(player: ServerPlayer) {
        val w=player.serverLevel();val f=NativeCampaignFixtures(w,player)
        val names=listOf("24V Egg Incubator" to CordRating.V24,"48V Macerator" to CordRating.V48,"240V Macerator" to CordRating.V240,"Old 480V Arc Furnace" to CordRating.V480,"Electric Mini Fridge" to CordRating.V120)
        names.forEachIndexed { i,(name,rating) ->
            val pos=BlockPos(360,90,188+i*12);val at=pos.east(4)
            for(x in -2..8)for(z in -2..3) {
                val p=pos.offset(x,-1,z);w.setChunkForced(p.x shr 4,p.z shr 4,true);w.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState())
            }
            w.setBlockAndUpdate(at.north(),Blocks.BRICKS.defaultBlockState())
            check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(rating.outletName,1),player,w,at,net.minecraft.core.Direction.SOUTH))
            val outlet=f.machine(at) as OutletElement
            f.source(pos.west(),rating.volts)
            Eln.simulator.addElectricalComponent(Resistor(f.six(pos.west()).getElectricalLoad(LRDU.Down,0),outlet.input).apply { resistance=.002 })
            val e=f.placeMachine(Eln.findItemStack(name,1),pos)
            val c=checkNotNull(CordDevices.get(e));check(c.cord.attach(outlet.anchor(0),false))
            check(c.cordPortable==(e is mods.eln.transparentnode.kitchen.KitchenElement))
            if(e is ElectricalMachineElement && name=="240V Macerator") {
                e.inventory!!.setItem(e.inSlotId,ItemStack(Blocks.COAL_ORE,1));e.inventoryChange(e.inventory)
            }
            cases.add(Case(e,outlet,rating))
        }
    }
    fun verify(): Map<String,Any> {
        val report=linkedMapOf<String,Any>()
        cases.forEach { (e,outlet,r) ->
            val c=checkNotNull(CordDevices.get(e));check(c.cord.connected)
            check(c.cord.input.voltage>r.volts*.98) { "${r.name} machine input ${c.cord.input.voltage}" }
            for(side in Direction.values())for(lrdu in LRDU.values()) {
                if(e.machineCord?.blocks(e.getElectricalLoad(side,lrdu))==true)check(e.node!!.getElectricalLoad(side,lrdu,0)==null)
            }
            if(r==CordRating.V240) {
                val m=e as ElectricalMachineElement
                check(m.inventory!!.getItem(m.outSlotId).count==3) { "Cord-powered macerator did not finish its recipe" }
            }
            if(e is mods.eln.transparentnode.kitchen.KitchenElement) { check(e.temperature<20);check(e.inventory.containerSize==12);check(e.cordPortable) }
            report[r.name]=c.cord.input.voltage
            outlet.disconnectSocket(0);check(!c.cord.plugged)
            check(Direction.values().any { side -> LRDU.values().any { lrdu -> e.node!!.getElectricalLoad(side,lrdu,0)===c.cord.input } })
        }
        return report
    }
}
