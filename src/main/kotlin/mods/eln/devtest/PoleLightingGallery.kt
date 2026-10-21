package mods.eln.devtest

import mods.eln.Eln
import mods.eln.client.NodeFrustum
import mods.eln.gridnode.*
import mods.eln.misc.*
import mods.eln.node.six.*
import mods.eln.node.transparent.*
import mods.eln.sixnode.lampsocket.*
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.sim.mna.component.Resistor
import mods.eln.transparentnode.utilitylight.*
import mods.eln.utility.*
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import java.io.*
import net.minecraft.world.item.ItemStack

/** Additional real-world checks run by UtilityDistributionChecks, never during ordinary play. */
internal object PoleLightingGallery {
    private val origin=BlockPos(264,90,200)
    private val fixtures=linkedMapOf<DecorativeLampStyle,BlockPos>()
    private val source=origin.west(7)
    private val portable=origin.west(2).south(8)
    private val socket=origin.west(5).south(8)
    private val flush=origin.west(5).south(12)
    private val candle=origin.west(2).south(12)
    private val corner=origin.south(42)
    val breakerPos: BlockPos=origin.east(58).south(8)
    private val strings=ArrayList<BlockPos>()
    private var lampStateBeforeRemoval: BlockPos?=null
    private fun f(p: ServerPlayer)=NativeCampaignFixtures(p.serverLevel(),p)
    private fun lamp(p: ServerPlayer,pos: BlockPos)=(f(p).node(pos) as SixNode).sideElementList.filterIsInstance<LampSocketElement>().single()
    private fun packet(id: Int,write: DataOutputStream.()->Unit={}): DataInputStream {
        val bytes=ByteArrayOutputStream();DataOutputStream(bytes).use { it.writeByte(id);it.write() };return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }
    fun setup(p: ServerPlayer) {
        MachineCordChecks.setup(p)
        val w=p.serverLevel();w.dayTime=6000
        val helper=f(p)
        for(x in -10..60)for(z in -4..52)w.setBlockAndUpdate(origin.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState())
        val breaker=helper.placeSix(Eln.findItemStack("125A Electrical Breaker",1),breakerPos)
        val thick=mods.eln.sixnode.electricalcable.UtilityCableDescriptor.allDescriptors().single { !it.melted && it.material==mods.eln.sixnode.electricalcable.UtilityCableMaterial.COPPER && it.sizeLabel=="1/0 AWG" && it.insulated && it.insulationVoltageRating==1000.0 }
        for(port in listOf(breaker.front,breaker.front.inverse()))helper.placeSix(thick.newItemStack(),breakerPos.relative(mods.eln.misc.Direction.YN.applyLRDU(port).toFacing()))
        helper.source(source,120.0)
        val supplyPos=source.east(2);helper.wire(source.east())
        val supply=helper.placeSix(Eln.findItemStack("240V Lamp Supply",1),supplyPos) as LampSupplyElement
        supply.front=mods.eln.misc.Direction.YN.getLRDUGoingTo(mods.eln.misc.Direction.XN)!!
        supply.inventory!!.setItem(0,helper.spool());supply.inventoryChange(supply.inventory)
        supply.networkUnserialize(packet(1) { writeByte(0);writeUTF("Pool gallery") })
        supply.networkUnserialize(packet(LampSupplyElement.setTransmissionRange.toInt()) { writeInt(128) })
        for((index,style) in DecorativeLampStyle.entries.drop(70).withIndex()) {
            val base=origin.offset((index%7)*8,0,(index/7)*16)
            val side=when { style.ceiling->mods.eln.misc.Direction.YP;style.wallLamp->mods.eln.misc.Direction.XN;else->mods.eln.misc.Direction.YN }
            val pos=if(style.ceiling)base.above(3) else if(style.wallLamp)base.above() else base
            if(style.pool) {
                for(x in -1..3)for(y in -1..2)for(z in -2..2) {
                    val at=base.offset(x,y,z)
                    w.setBlockAndUpdate(at,if(x==-1 || x==3 || y==-1 || z==-2 || z==2)Blocks.QUARTZ_BLOCK.defaultBlockState() else Blocks.WATER.defaultBlockState())
                }
            } else {
                w.setBlockAndUpdate(pos.relative(side.toFacing()),Blocks.SMOOTH_STONE.defaultBlockState())
            }
            check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(style.itemName,1),p,w,pos,side.toFacing().opposite,.5f,.5f,.5f)) { "Gallery placement failed: ${style.itemName}" }
            val e=lamp(p,pos)
            e.inventory.setItem(0,Eln.findItemStack("120V LED Light Bulb",style.bulbs));e.inventory.setItem(1,helper.spool())
            e.lampSupplyChannel="Pool gallery";e.poweredByLampSupply=index%2==0;e.inventoryChange(e.inventory)
            if(!e.poweredByLampSupply)Eln.simulator.addElectricalComponent(Resistor(helper.six(source).getElectricalLoad(LRDU.Down,0),e.electricalLoad).apply { resistance=.002 })
            if(style.aimHeadCount>0)e.networkUnserialize(packet(32+(style.aimHeadCount-1)*2) { writeDouble(135.0) },p)
            fixtures[style]=pos
        }
        // Exercise moving a connected fixture through the production pickup and placement paths.
        val support=socket.north();w.setBlockAndUpdate(support,Blocks.BRICKS.defaultBlockState())
        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack("240 V Wall Outlet",1),p,w,socket,net.minecraft.core.Direction.SOUTH))
        val outlet=helper.machine(socket) as mods.eln.transparentnode.cord.OutletElement
        val feed240=source.south(8);helper.source(feed240,240.0)
        Eln.simulator.addElectricalComponent(Resistor(helper.six(feed240).getElectricalLoad(LRDU.Down,0),outlet.input).apply { resistance=.002 })
        check(Eln.sixNodeItem.placeBlockAt(Eln.findItemStack("Window Candle Light",1),p,w,portable,net.minecraft.core.Direction.UP,.5f,.5f,.5f))
        val moving=lamp(p,portable);moving.inventory.setItem(0,Eln.findItemStack("240V LED Light Bulb",1));moving.inventory.setItem(1,helper.spool());moving.inventoryChange(moving.inventory)
        check(moving.cord.attach(outlet.anchor(0),false));p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        check(moving.cord.carry(p));check(helper.node(portable)==null)
        val carried=p.mainHandItem.copy();check(carried.tagCompound?.contains("carriedAppliance")==true)
        val replaced=portable.east()
        check(Eln.sixNodeItem.placeBlockAt(carried,p,w,replaced,net.minecraft.core.Direction.UP,.5f,.5f,.5f))
        val restored=lamp(p,replaced);restored.cord.poll();check(restored.cord.connected && restored.installedBulbs==1)
        outlet.disconnectSocket(0,p);check(!restored.cord.plugged && restored.installedBulbs==1)
        check(restored.cord.attach(outlet.anchor(0),false))
        // Recess into a solid wall and preserve its original block state.
        val wallStack=Eln.findItemStack("Experimental Wall Outlet",1).apply { tagCompound=CompoundTag().apply { put("outletWall",net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.BRICKS.defaultBlockState())) } }
        w.setBlockAndUpdate(flush,Blocks.BRICKS.defaultBlockState())
        check(Eln.transparentNodeItem.placeBlockAt(wallStack,p,w,flush,net.minecraft.core.Direction.SOUTH,true))
        check((helper.machine(flush) as mods.eln.transparentnode.cord.OutletElement).recessed)
        check(w.getBlockState(flush.south()).isAir)
        // A bottom slab is a valid window sill for the old holiday candle.
        w.setBlockAndUpdate(candle.below(),Blocks.STONE_SLAB.defaultBlockState())
        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack("Holiday Candle",1),p,w,candle,net.minecraft.core.Direction.UP))
        check((helper.machine(candle) as mods.eln.transparentnode.festive.FestiveElement).mountOffset==-.5)
        // A real right-angle HV run and a straight, independently rotated LV run.
        fun pole(pos: BlockPos,kind: DistributionKind=DistributionKind.THREE): DistributionPoleElement {
            w.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            val e=helper.placeMachine(Eln.findItemStack(kind.title,1),pos) as DistributionPoleElement
            return e
        }
        val centre=pole(corner);val east=pole(corner.east(16));val south=pole(corner.south(16));val west=pole(corner.west(16),DistributionKind.SECONDARY)
        val cable=Eln.sixNodeItem.getDescriptor(helper.spool(300.0)) as mods.eln.sixnode.electricalcable.UtilityCableDescriptor
        GridLink.addLink(centre,east,52,52,cable,48);GridLink.addLink(centre,south,52,52,cable,48)
        GridLink.addLink(centre,east,50,50,cable,48);GridLink.addLink(centre,west,50,50,cable,48)
        check(centre.highVoltageRenderAngle==-45f && centre.lowVoltageRenderAngle==0f)
        val branchBase=corner.east(40);val three=pole(branchBase)
        repeat(3) { phase ->
            val branch=pole(when(phase) { 0->branchBase.east(8);1->branchBase.south(8);else->branchBase.west(8) },DistributionKind.SINGLE)
            val spool=helper.spool(300.0)
            if(phase==1) {
                branch.onTryGridConnect(p,spool,cable,Direction.ZP,branch.renderedGridCablePoint(32,0))
                three.onTryGridConnect(p,spool,cable,Direction.ZP,three.renderedGridCablePoint(33,0))
            } else {
                three.onTryGridConnect(p,spool,cable,Direction.ZP,three.renderedGridCablePoint(32+phase,0))
                branch.onTryGridConnect(p,spool,cable,Direction.ZP,branch.renderedGridCablePoint(32,0))
            }
            check(branch.gridLinkList.single().getTerminal(three)==32+phase) { "Wrong single-phase branch mapping: $phase" }
        }
        val meter=three.multiMeterString(Direction.ZP)
        check(listOf("HV A","HV B","HV C","LV L1","LV L2").all { it in meter })
        for((i,name)in listOf("White Fairy Lights on Green Wire","Multicolor Fairy Lights on Green Wire","Warm White Deck Globe String Lights","Multicolor Deck Globe String Lights").withIndex()) {
            val a=origin.east(12).south(36+i*3).above(3);val b=a.east(6)
            for(at in listOf(a,b))w.setBlockAndUpdate(at,Blocks.OAK_PLANKS.defaultBlockState())
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,Eln.findItemStack(name,1))
            for(at in listOf(a,b)) {
                val hit=net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at).add(0.0,0.0,-.5),net.minecraft.core.Direction.NORTH,at,false)
                val context=net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit)
                check(mods.eln.transparentnode.festive.FairyLightPlacement.use(context).consumesAction())
            }
            val placed=b.north();val e=helper.machine(placed) as mods.eln.transparentnode.festive.FairyLightsElement
            e.powerChannel="Pool gallery";check(e.start.distanceTo(e.end)>5.9);strings.add(placed)
        }
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        p.teleportTo(w,origin.x+24.0,origin.y+6.0,origin.z+35.0,180f,20f)
    }
    fun verify(p: ServerPlayer) {
        val w=p.serverLevel()
        for(pos in strings) {
            val e=f(p).machine(pos) as mods.eln.transparentnode.festive.FairyLightsElement
            check(e.node!!.lightValue>=14) { "Suspended string unlit at $pos: ${e.electricalLoad.voltage}" }
        }
        val moved=lamp(p,portable.east());check(moved.cord.connected && moved.electricalLoad.voltage>238)
        w.destroyBlock(socket,false,p);check(!moved.cord.plugged)
        w.destroyBlock(flush,false,p);check(w.getBlockState(flush).block===Blocks.BRICKS)
        for((style,pos) in fixtures) {
            val e=lamp(p,pos)
            check(e.installedBulbs==style.bulbs)
            check(e.electricalLoad.voltage>119.5) { "${style.name} voltage ${e.electricalLoad.voltage}" }
            check((e.sixNode!!.lightValue)>=14) { "${style.name} light ${e.sixNode!!.lightValue}" }
            if(style.pool)check(w.getBlockState(pos).getValue(WATERLOGGED) && w.getFluidState(pos).`is`(Fluids.WATER))
            if(style.aimHeadCount>0)check(e.aim.pan(style.aimHeadCount-1)==135.0.coerceIn(-if(e.aim.axialPan)180.0 else 90.0,if(e.aim.axialPan)180.0 else 90.0))
        }
        check(w.getBrightness(LightLayer.SKY,corner)>=14) { "Pole base self-shadow: ${w.getBrightness(LightLayer.SKY,corner)}" }
        val pool=fixtures.getValue(DecorativeLampStyle.POOL_ROUND)
        val lightCell=pool.east()
        mods.eln.lightblock.LightBlockEntity.addLight(w,lightCell.x,lightCell.y,lightCell.z,15,3)
        check(w.getBlockState(lightCell).block===Eln.lightBlock && w.getBlockState(lightCell).getValue(WATERLOGGED))
        w.destroyBlock(pool,false,p)
        check(w.getFluidState(pool).`is`(Fluids.WATER) && w.getFluidState(pool).isSource)
        // A separate, otherwise unlit pool cell tests timeout restoration without a refresh.
        val isolated=origin.north(3)
        w.setBlockAndUpdate(isolated,Blocks.WATER.defaultBlockState())
        mods.eln.lightblock.LightBlockEntity.addLight(w,isolated.x,isolated.y,isolated.z,15,2)
        lampStateBeforeRemoval=isolated
    }
    fun verifyExpiredWater(p: ServerPlayer) {
        MachineCordChecks.verify()
        val at=lampStateBeforeRemoval!!
        check(p.serverLevel().getBlockState(at).block===Blocks.WATER && p.serverLevel().getFluidState(at).isSource)
    }
    fun viewStrings(p: ServerPlayer) {
        p.serverLevel().dayTime=18000
        p.teleportTo(p.serverLevel(),origin.x+15.5,origin.y+2.0,origin.z+31.0,0f,8f)
    }
    fun darkStrings(p: ServerPlayer) { f(p).voltage(source,0.0) }
    fun verifyStringRenders(on: Boolean) {
        val world=Minecraft.getInstance().level!!
        for(pos in strings) {
            val render=(world.getBlockEntity(pos) as TransparentNodeEntity).elementRender as mods.eln.transparentnode.festive.FairyLightsRender
            check(render.powered==on) { "Suspended light visual state differs from power at $pos" }
        }
    }
    fun verifyClientSpan(): Map<String,Any> {
        val mc=Minecraft.getInstance()
        val entity=mc.level!!.getBlockEntity(corner) as TransparentNodeEntity
        val render=entity.elementRender as GridRender
        check(render.highVoltageRenderAngle==-45f && render.lowVoltageRenderAngle==0f)
        val bounds=entity.getRenderBoundingBox()
        check(bounds.maxX>corner.x+16 && bounds.maxZ>corner.z+16)
        val ber=TransparentNodeRender()
        check(ber.shouldRenderOffScreen(entity))
        val camera=Vec3(corner.x+8.0,corner.y+7.0,corner.z+2.0)
        check(NodeFrustum.inRange(bounds,camera,256))
        return mapOf("hvYaw" to render.highVoltageRenderAngle,"lvYaw" to render.lowVoltageRenderAngle,"bounds" to bounds.toString())
    }
}
