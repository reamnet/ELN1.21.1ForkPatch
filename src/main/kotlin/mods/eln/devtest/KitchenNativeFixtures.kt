package mods.eln.devtest

import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.misc.*
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.sixnode.lampsocket.*
import mods.eln.transparentnode.kitchen.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.SlabType
import kotlin.math.abs

internal object KitchenNativeFixtures {
    private val base=BlockPos(256,66,256)
    private val machines=KitchenType.entries.mapIndexed { i,type -> type to base.offset(i*4,0,12) }
    private val progress=mutableMapOf<KitchenType,Double>()
    var clickTarget: BlockPos?=null
        private set
    private val mounted=mutableListOf<Pair<BlockPos,Direction>>()
    fun machine(p: ServerPlayer,index: Int) = NativeCampaignFixtures(p.serverLevel(),p).machine(machines[index].second) as KitchenElement
    private fun totalWork(e: KitchenElement)=e.inventory.getItem(2).count*e.type.watts*e.type.seconds+e.work.joules
    fun setup(p: ServerPlayer) {
        val f=NativeCampaignFixtures(p.serverLevel(),p);val world=p.serverLevel()
        for((i,entry)in machines.withIndex()) {
            val (type,pos)=entry
            val e=f.placeMachine(Eln.findItemStack(type.title,1),pos) as KitchenElement
            e.front=Direction.ZP;e.reconnect();f.wire(pos.north());f.source(pos.north(2),type.volts)
            e.inventory.setItem(0,ItemStack(when(type) {
                KitchenType.TOASTER -> Items.BREAD
                KitchenType.COFFEE -> Items.COCOA_BEANS
                KitchenType.FRIDGE -> Items.WATER_BUCKET
                else -> Items.BEEF
            },if(type==KitchenType.FRIDGE)1 else 2))
            if(type==KitchenType.COFFEE)e.inventory.setItem(1,PotionContents.createItemStack(Items.POTION,Potions.WATER))
            if(type==KitchenType.FRIDGE)e.inventory.setItem(3,ItemStack(Items.APPLE,7))
            e.inventory.setChanged()
        }
        val ceiling=listOf("Classic Lamp Socket","Fluorescent Lamp Socket","Suspended Lamp Socket")+
            DecorativeLampStyle.entries.filter { it.ceiling }.map { it.itemName }
        var index=0
        for(slab in listOf(SlabType.BOTTOM,SlabType.TOP))for(name in ceiling) {
            val pos=base.offset((index%10)*4,6,24+(index/10)*5);index++
            world.setChunkForced(pos.x shr 4,pos.z shr 4,true)
            world.setBlockAndUpdate(pos.above(),Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,slab))
            check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name,1),p,world,pos,net.minecraft.core.Direction.DOWN,.5f,.5f,.5f)) { "Ceiling socket $name rejected $slab slab" }
            val e=(NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x,pos.y,pos.z,world)) as SixNode).getElement(Direction.YP) as LampSocketElement
            val inset=LampMounting.inset(world,pos,Direction.YP,e.descriptor)
            check(abs(inset-if(slab==SlabType.TOP).5 else 0.0)<1e-9)
            if(slab==SlabType.TOP && name==DecorativeLampStyle.POT_BAFFLE.itemName)clickTarget=pos
            mounted+=pos to Direction.YP
        }
        val supports=listOf(Blocks.STONE_SLAB.defaultBlockState(),Blocks.OAK_STAIRS.defaultBlockState(),Blocks.OAK_FENCE.defaultBlockState(),Blocks.CHEST.defaultBlockState())
        for(style in DecorativeLampStyle.entries.filter { it.tableLamp })for(state in supports) {
            val pos=base.offset((index%10)*4,0,24+(index/10)*5);index++
            world.setChunkForced(pos.x shr 4,pos.z shr 4,true);world.setBlockAndUpdate(pos.below(),state)
            check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(style.itemName,1),p,world,pos,net.minecraft.core.Direction.UP,.5f,.5f,.5f)) { "${style.itemName} rejected ${state.block}" }
            mounted+=pos to Direction.YN
        }
    }
    fun requeue(p: ServerPlayer) {
        for((i,entry)in machines.withIndex()) {
            val e=machine(p,i);val type=entry.first
            e.inventory.setItem(0,ItemStack(when(type) {
                KitchenType.TOASTER -> Items.BREAD
                KitchenType.COFFEE -> Items.COCOA_BEANS
                KitchenType.FRIDGE -> Items.WATER_BUCKET
                else -> Items.BEEF
            },if(type==KitchenType.FRIDGE)1 else 2))
            e.inventory.setItem(1,if(type==KitchenType.COFFEE)PotionContents.createItemStack(Items.POTION,Potions.WATER) else ItemStack.EMPTY)
            e.inventory.setItem(2,ItemStack.EMPTY);e.work.joules=0.0;e.inventory.setChanged()
        }
    }
    fun outage(p: ServerPlayer) {
        for(i in machines.indices) {
            val e=machine(p,i);check(e.load.voltage>e.type.volts*.95)
            progress[e.type]=totalWork(e)
            NativeCampaignFixtures(p.serverLevel(),p).voltage(machines[i].second.north(2),0.0)
        }
    }
    fun restore(p: ServerPlayer) {
        for(i in machines.indices) {
            val e=machine(p,i)
            check(totalWork(e)-progress[e.type]!!<=e.type.watts*.15) { "${e.type} processed without paid electrical power" }
            NativeCampaignFixtures(p.serverLevel(),p).voltage(machines[i].second.north(2),e.type.volts)
        }
    }
    fun gui(p: ServerPlayer,index: Int) {
        val pos=machines[index].second
        p.teleportTo(p.serverLevel(),pos.x+2.5,pos.y+1.0,pos.z+3.0,140f,24f)
        GuiHandler.open(p,GuiHandler.nodeBaseOpen+Direction.ZP.int,p.serverLevel(),pos.x,pos.y,pos.z)
    }
    fun partialView(p: ServerPlayer) {
        val pos=clickTarget!!
        val distance=kotlin.math.hypot(1.5,1.5)
        val pitch=Math.toDegrees(kotlin.math.atan2(p.eyeHeight-1.0-1.44,distance)).toFloat()
        p.teleportTo(p.serverLevel(),pos.x+2.0,pos.y-1.0,pos.z+2.0,135f,pitch)
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY)
    }
    fun verify(p: ServerPlayer) {
        for((i,entry) in machines.withIndex()) {
            val e=machine(p,i);val type=entry.first;val out=e.inventory.getItem(2)
            check(!out.isEmpty) { "$type did not produce food/ice; ${e.getWaila()}, progress=${e.work.joules}, temp=${e.temperature}" }
            when(type) {
                KitchenType.MICROWAVE,KitchenType.STOVE -> check(out.`is`(Items.COOKED_BEEF) && out.count==2)
                KitchenType.TOASTER -> check(out.`is`(KitchenFoods.toast.get()) && out.count==2)
                KitchenType.COFFEE -> { check(out.`is`(KitchenFoods.coffee.get()) && out.count==1);check(e.inventory.getItem(0).count==1);check(e.inventory.getItem(1).isEmpty) }
                KitchenType.FRIDGE -> { check(out.`is`(Items.ICE));check(e.inventory.getItem(1).`is`(Items.BUCKET));check(e.inventory.getItem(3).count==7);check(e.temperature<5.0) }
            }
            check(e.enabled)
        }
        for((pos,side)in mounted) {
            val node=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x,pos.y,pos.z,p.serverLevel())) as? SixNode
            check(node?.getElement(side) is LampSocketElement) { "Lamp on partial support disappeared at $pos/$side" }
        }
    }
}
