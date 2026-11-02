package mods.eln.vehicle

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalitem.BatteryItem
import mods.eln.misc.*
import mods.eln.registration.ElnRegistry
import mods.eln.transparentnode.industrial.IndustrialItems
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.*

enum class RoadVehicleKind(val title: String,val seats: Int,val packs: Int,val topSpeed: Double,val mass: Double,val motorWatts: Double) {
    CART(I18N.TR_NAME(I18N.Type.NONE,"Electric Golf Cart"),4,1,10.0,550.0,18000.0),
    COUPE(I18N.TR_NAME(I18N.Type.NONE,"Arc Runner Coupe"),2,6,45.0,1750.0,220000.0)
}

/** SI-unit driving model. Only the server changes motion, energy and cornering damage. */
object RoadPhysics {
    fun chargerResistance(volts: Double,demand: Double): Double {
        if(!volts.isFinite() || !demand.isFinite() || volts !in 200.0..264.0 || demand<=.01)return 1e19
        return maxOf(2.4,240.0*240.0/demand,volts/100.0,volts*volts/24000.0)
    }
    fun lateralAcceleration(speed: Double,yawRadiansPerSecond: Double)=abs(speed*yawRadiansPerSecond)
    fun dangerousCorner(speed: Double,yawRadiansPerSecond: Double)=abs(speed)>=7.5 && lateralAcceleration(speed,yawRadiansPerSecond)>8.5
    fun storedCharge(energy: Double,capacity: Double,chargeWatts: Double,inputJoules: Double,seconds: Double): Double {
        if(listOf(energy,capacity,chargeWatts,inputJoules,seconds).any { !it.isFinite() } || seconds<=0)return 0.0
        return minOf(max(0.0,capacity-energy),max(0.0,chargeWatts)*seconds,max(0.0,inputJoules)*.94)
    }
}

object RoadVehicles {
    lateinit var cart: Supplier<EntityType<RoadVehicleEntity>>
    lateinit var coupe: Supplier<EntityType<RoadVehicleEntity>>
    lateinit var menu: Supplier<MenuType<VehicleMenu>>
    fun register() {
        fun type(name: String,kind: RoadVehicleKind)=ElnRegistry.registerEntityType(name) {
            EntityType.Builder.of({ t,l->RoadVehicleEntity(t,l,kind) },MobCategory.MISC).sized(if(kind==RoadVehicleKind.CART)1.65f else 1.9f,if(kind==RoadVehicleKind.CART)2.15f else 1.85f).clientTrackingRange(12).updateInterval(1)
        }
        cart=type("electric_golf_cart",RoadVehicleKind.CART);coupe=type("arc_runner_coupe",RoadVehicleKind.COUPE)
        menu=ElnRegistry.registerMenu("road_vehicle") { id,inv,buf->VehicleMenu(id,inv,inv.player.level().getEntity(buf.readVarInt()) as? RoadVehicleEntity) }
    }
    fun battery(stack: ItemStack): BatteryItem? {
        if(stack.isEmpty || stack.count!=1)return null
        val d=GenericItemUsingDamageDescriptor.getDescriptor(stack)
        return (d as? BatteryItem)?.takeIf { d===GenericItemUsingDamageDescriptor.getDescriptor(Eln.findItemStack(IndustrialItems.PACK,1)) }
    }
}

class RoadVehicleItem(val kind: RoadVehicleKind): GenericItemUsingDamageDescriptor(kind.title) {
    init { creativeTab=Eln.creativeTabMachines }
    override fun onItemUse(stack: ItemStack?,player: Player?,world: Level?,x: Int,y: Int,z: Int,side: Int,vx: Float,vy: Float,vz: Float): Boolean {
        if(stack==null || player==null || world==null)return false
        val pos=BlockPos(x,y,z).relative(net.minecraft.core.Direction.from3DDataValue(side))
        val e=(if(kind==RoadVehicleKind.CART)RoadVehicles.cart else RoadVehicles.coupe).get().create(world) ?: return false
        e.moveTo(pos.x+.5,pos.y.toDouble(),pos.z+.5,player.yRot,0f)
        if(!world.noCollision(e,e.boundingBox))return false
        if(!world.isClientSide) { if(!world.addFreshEntity(e))return false;if(!player.abilities.instabuild)stack.shrink(1) }
        return true
    }
    override fun handleRenderType(item: ItemStack?,type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType?)=true
    override fun shouldUseRenderHelper(type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType?,item: ItemStack?,helper: mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper?)=true
    override fun renderItem(type: mods.eln.client.itemrender.IItemRenderer.ItemRenderType?,item: ItemStack?,vararg data: Any?)=preserveMatrix {
        mods.eln.client.gl.GL11.glScalef(.38f,.38f,.38f);mods.eln.client.gl.GL11.glTranslatef(0f,-.6f,0f);VehicleModels.draw(kind)
    }
    override fun addInformation(stack: ItemStack?,player: Player?,list: MutableList<String>,advanced: Boolean) {
        list.add(tr("Seats: %1$; traction packs: %2$; maximum speed: %3$ km/h",kind.seats,kind.packs,(kind.topSpeed*3.6).toInt()))
        list.add(tr("Sneak-right-click while parked: batteries and nine storage slots"))
        list.add(tr("Movement keys drive; sneak dismounts; plug in while parked to charge"))
        if(kind==RoadVehicleKind.CART)list.add(tr("Climbs one block. Fast sharp corners cause an explosion and fire!"))
        else list.add(tr("Install six 20 kWh traction packs; 240 V 100 A vehicle charging"))
    }
}

class RoadVehicleEntity(type: EntityType<out RoadVehicleEntity>,world: Level,val kind: RoadVehicleKind): Entity(type,world) {
    companion object {
        private val CHARGE=SynchedEntityData.defineId(RoadVehicleEntity::class.java,EntityDataSerializers.FLOAT)
        private val SPEED=SynchedEntityData.defineId(RoadVehicleEntity::class.java,EntityDataSerializers.FLOAT)
        private val PLUG=SynchedEntityData.defineId(RoadVehicleEntity::class.java,EntityDataSerializers.BOOLEAN)
        private val PACKS=SynchedEntityData.defineId(RoadVehicleEntity::class.java,EntityDataSerializers.INT)
        private val TURN=SynchedEntityData.defineId(RoadVehicleEntity::class.java,EntityDataSerializers.FLOAT)
    }
    var speed=0.0;private set
    private var throttle=0.0;private var steering=0.0;private var age=100
    private var dischargeLeft=java.util.IdentityHashMap<ItemStack,Double>()
    private var climbReserve=0.0
    private var cornerTicks=0;private var health=40f
    private var viewers=0
    private var lerpSteps=0;private var target=Vec3.ZERO;private var targetYaw=0f
    internal var charger: UUID?=null
    val plugged get()=entityData.get(PLUG)
    val charge get()=entityData.get(CHARGE)
    val displayedSpeed get()=entityData.get(SPEED)
    val installedPacks get()=entityData.get(PACKS)
    val displayedSteering get()=entityData.get(TURN)
    val parked get()=abs(speed)<.1
    val items=object: SimpleContainer(15) {
        override fun canPlaceItem(slot: Int,stack: ItemStack)=if(slot<6)slot<kind.packs && RoadVehicles.battery(stack)!=null else GenericItemUsingDamageDescriptor.getDescriptor(stack) !is RoadVehicleItem
        override fun stillValid(player: Player)=isAlive && player.distanceToSqr(this@RoadVehicleEntity)<64 && parked && !isVehicle
        override fun startOpen(player: Player) { viewers++ }
        override fun stopOpen(player: Player) { viewers=max(0,viewers-1) }
        override fun setChanged() { super.setChanged();if(!level().isClientSide)publish() }
    }
    override fun defineSynchedData(builder: SynchedEntityData.Builder) { builder.define(CHARGE,0f);builder.define(SPEED,0f);builder.define(PLUG,false);builder.define(PACKS,0);builder.define(TURN,0f) }
    override fun getControllingPassenger(): LivingEntity?=firstPassenger as? Player
    override fun isControlledByLocalInstance()=false
    override fun canAddPassenger(passenger: Entity)=passenger is Player && passengers.size<kind.seats && viewers==0 && !plugged
    override fun getPassengerAttachmentPoint(passenger: Entity,dimensions: EntityDimensions,partialTick: Float): Vec3 {
        val seat=passengers.indexOf(passenger).coerceAtLeast(0)
        return Vec3(if(seat%2==0).39 else -.39,.43,if(kind==RoadVehicleKind.COUPE)-.15 else if(seat<2).42 else -.68).yRot(-yRot*PI.toFloat()/180)
    }
    override fun getDismountLocationForPassenger(passenger: LivingEntity): Vec3 {
        for(side in listOf(1.4,-1.4))for(up in listOf(0.0,1.0)) {
            val p=position().add(Vec3(side,up,0.0).yRot(-yRot*PI.toFloat()/180))
            if(level().noCollision(passenger,passenger.boundingBox.move(p.subtract(passenger.position()))))return p
        }
        return position().add(0.0,2.2,0.0)
    }
    override fun makeBoundingBox(): net.minecraft.world.phys.AABB {
        val r=yRot*PI/180;val width=if(kind==RoadVehicleKind.CART).83 else .98;val length=if(kind==RoadVehicleKind.CART)1.35 else 2.03
        val xRadius=abs(cos(r))*width+abs(sin(r))*length;val zRadius=abs(sin(r))*width+abs(cos(r))*length
        return net.minecraft.world.phys.AABB(x-xRadius,y,z-zRadius,x+xRadius,y+bbHeight,z+zRadius)
    }
    override fun maxUpStep()=if(kind==RoadVehicleKind.CART)1.001f else .6f
    override fun isPickable()=true
    override fun canBeCollidedWith()=true
    fun controls(player: Player,forward: Double,turn: Double) {
        if(controllingPassenger!==player || level().isClientSide)return
        throttle=if(forward.isFinite())forward.coerceIn(-1.0,1.0) else 0.0
        steering=if(turn.isFinite())turn.coerceIn(-1.0,1.0) else 0.0;age=0
    }
    override fun interact(player: Player,hand: InteractionHand): InteractionResult {
        if(level().isClientSide)return InteractionResult.SUCCESS
        val lead=GenericItemUsingDamageDescriptor.getDescriptor(player.getItemInHand(hand)) as? VehicleLeadItem
        if(lead!=null) { lead.connect(player.getItemInHand(hand),player,this);return InteractionResult.CONSUME }
        if(player.isShiftKeyDown && parked && !isVehicle && player is ServerPlayer) {
            player.openMenu(SimpleMenuProvider({ id,inv,_->VehicleMenu(id,inv,this) },Component.literal(kind.title))) { it.writeVarInt(id) }
        } else if(!plugged && viewers==0)player.startRiding(this)
        else player.displayClientMessage(Component.literal(tr("Disconnect the charging lead before driving")),true)
        return InteractionResult.CONSUME
    }
    private fun packs()=(0 until kind.packs).mapNotNull { i->val s=items.getItem(i);RoadVehicles.battery(s)?.let { s to it } }
    fun chargeDemand(dt: Double=.05): Double=if(!dt.isFinite() || dt<=0)0.0 else packs().sumOf { (s,b)->min(b.getChargePower(s),max(0.0,b.getEnergyMax(s)-safeEnergy(s,b))/(.94*dt)) }.coerceAtMost(24000.0)
    private fun safeEnergy(s: ItemStack,b: BatteryItem)=b.getEnergy(s).takeIf { it.isFinite() }?.coerceIn(0.0,b.getEnergyMax(s)) ?: 0.0
    /** Fairly share measured charger energy, then redistribute the remainder from full bays. */
    fun acceptCharge(inputJoules: Double,dt: Double): Double {
        if(!parked || !inputJoules.isFinite() || inputJoules<=0 || !dt.isFinite() || dt<=0)return 0.0
        var available=inputJoules*.94;var stored=0.0
        var pending=packs().map { (s,b)->Triple(s,b,RoadPhysics.storedCharge(safeEnergy(s,b),b.getEnergyMax(s),b.getChargePower(s),inputJoules,dt)) }.filter { it.third>0 }
        while(available>1e-9 && pending.isNotEmpty()) {
            val share=available/pending.size
            pending=pending.mapNotNull { (s,b,room)->val add=min(share,room);b.setEnergy(s,safeEnergy(s,b)+add);available-=add;stored+=add;if(room>add+1e-9)Triple(s,b,room-add) else null }
        }
        publish();return stored
    }
    private fun spend(joules: Double): Double {
        var left=max(0.0,joules)
        var pending=packs().filter { (s,b)->min(safeEnergy(s,b),dischargeLeft[s] ?: 0.0)>1e-9 }
        while(left>1e-9 && pending.isNotEmpty()) {
            val share=left/pending.size
            pending=pending.filter { (s,b)->
                val limit=dischargeLeft[s] ?: 0.0;val e=safeEnergy(s,b);val paid=minOf(share,e,limit)
                b.setEnergy(s,e-paid);dischargeLeft[s]=limit-paid;left-=paid
                min(e-paid,limit-paid)>1e-9
            }
        }
        return joules-left
    }
    private fun energy()=packs().sumOf { (s,b)->safeEnergy(s,b) }
    private fun publish() {
        val p=packs();entityData.set(CHARGE,(p.sumOf { (s,b)->safeEnergy(s,b) }/(kind.packs*72000000.0)).coerceIn(0.0,1.0).toFloat())
        entityData.set(PACKS,p.size);entityData.set(PLUG,charger!=null);entityData.set(SPEED,speed.toFloat());entityData.set(TURN,steering.toFloat())
    }
    override fun tick() {
        super.tick()
        if(level().isClientSide) {
            if(lerpSteps>0) { setPos(position().add(target.subtract(position()).scale(1.0/lerpSteps)));yRot+=net.minecraft.util.Mth.wrapDegrees(targetYaw-yRot)/lerpSteps;lerpSteps-- };return
        }
        dischargeLeft=java.util.IdentityHashMap<ItemStack,Double>().apply { packs().forEach { (s,b)->put(s,min(safeEnergy(s,b),b.getDischagePower(s)*.05)) } }
        if(charger!=null && !VehicleChargers.connected(this))charger=null
        if(++age>5 || controllingPassenger==null || viewers>0) { throttle=0.0;steering=0.0 }
        val mass=kind.mass+passengers.size*80.0
        val ready=packs().size==kind.packs && energy()>0 && charger==null && viewers==0
        val desired=if(ready)throttle*(if(throttle>=0)kind.topSpeed else 3.0) else 0.0
        val acceleration=if(kind==RoadVehicleKind.CART)3.2 else 9.0
        var next=speed+(desired-speed).coerceIn(-acceleration*.05,acceleration*.05)
        val rolling=mass*9.81*.016*abs(next)*.05
        val drag=.5*1.225*(if(kind==RoadVehicleKind.CART)1.1 else .55)*abs(next).pow(3)*.05
        val kinetic=max(0.0,.5*mass*(next*next-speed*speed))/.88
        val power=kind.motorWatts.coerceAtMost(packs().sumOf { (s,b)->b.getDischagePower(s) })
        val budget=min(dischargeLeft.values.sum(),power*.05)
        if(kinetic+rolling+drag>budget && abs(next)>abs(speed)) {
            val allowed=max(0.0,budget-rolling-drag-(if(isVehicle)5.0 else 0.0))*.88
            next=sign(next)*sqrt(speed*speed+2*allowed/mass)
        }
        val needed=max(0.0,.5*mass*(next*next-speed*speed))/.88+rolling+drag+(if(isVehicle)5.0 else 0.0)
        speed=if(ready && spend(needed)+1e-6>=needed)next else speed*.94
        if(charger!=null)speed=0.0
        if(abs(speed)<.01)speed=0.0
        val yawRate=-steering*(if(kind==RoadVehicleKind.CART)1.5 else .9)*speed/(abs(speed)+2.0)
        if(kind==RoadVehicleKind.CART && RoadPhysics.dangerousCorner(speed,yawRate))cornerTicks++ else cornerTicks=max(0,cornerTicks-2)
        if(cornerTicks>=8) { explode();return }
        val previousYaw=yRot
        yRot+=(yawRate*.05*180/PI).toFloat()
        if(!level().noCollision(this,makeBoundingBox()))yRot=previousYaw
        boundingBox=makeBoundingBox()
        val r=yRot*PI/180
        deltaMovement=Vec3(-sin(r)*speed*.05,deltaMovement.y-.08,cos(r)*speed*.05)
        val before=position();val beforeY=y
        move(MoverType.SELF,deltaMovement)
        // Slow climbs can accumulate paid lifting work; faster climbs also use kinetic energy.
        if(y>beforeY+.05) {
            val required=mass*9.81*(y-beforeY)/.88
            climbReserve+=spend(max(0.0,required-climbReserve))
            val fromMotion=max(0.0,required-climbReserve)
            if(.5*mass*speed*speed/.88+1e-6>=fromMotion) {
                speed=sign(speed)*sqrt(max(0.0,speed*speed-2*fromMotion*.88/mass));climbReserve=0.0
            } else { setPos(before);speed=0.0 }
        } else if(!horizontalCollision)climbReserve=0.0
        if(horizontalCollision)speed=0.0
        deltaMovement=Vec3(0.0,deltaMovement.y*.98,0.0)
        publish()
    }
    private fun drops(vehicle: Boolean) {
        if(vehicle)spawnAtLocation(Eln.findItemStack(kind.title,1))
        (0 until items.containerSize).forEach { val s=items.removeItemNoUpdate(it);if(!s.isEmpty)spawnAtLocation(s) }
    }
    private fun explode() {
        val occupants=passengers.toList();ejectPassengers();charger=null
        drops(false);discard()
        // TNT interaction is intentional: a dangerous corner destroys the cart and can ignite blocks.
        level().explode(this,x,y+.5,z,3.0f,true,Level.ExplosionInteraction.TNT)
        occupants.forEach { it.igniteForSeconds(5f) }
    }
    override fun hurt(source: DamageSource,amount: Float): Boolean {
        if(level().isClientSide || isInvulnerableTo(source) || !isAlive || !amount.isFinite() || amount<=0)return false
        health-=amount
        if((source.entity as? Player)?.abilities?.instabuild==true || health<=0) { ejectPassengers();charger=null;drops(true);discard() }
        return true
    }
    override fun lerpTo(x: Double,y: Double,z: Double,yaw: Float,pitch: Float,steps: Int) { target=Vec3(x,y,z);targetYaw=yaw;lerpSteps=steps.coerceIn(1,4) }
    override fun addAdditionalSaveData(tag: CompoundTag) { (0..14).forEach { tag.put("bay$it",items.getItem(it).writeToNBT(CompoundTag())) };tag.putFloat("vehicleHealth",health) }
    override fun readAdditionalSaveData(tag: CompoundTag) { (0..14).forEach { val s=stackFromNbt(tag.getCompound("bay$it"));if(s.isEmpty || items.canPlaceItem(it,s))items.setItem(it,s) };health=tag.getFloat("vehicleHealth").takeIf { it.isFinite() && it>0 } ?: 40f;publish() }
}

class VehicleMenu(id: Int,inv: Inventory,val vehicle: RoadVehicleEntity?): AbstractContainerMenu(RoadVehicles.menu.get(),id) {
    private val contents=vehicle?.items ?: SimpleContainer(15)
    init {
        contents.startOpen(inv.player)
        for(i in 0..5)addSlot(object: Slot(contents,i,35+i*18,32) {
            override fun isActive()=i<(vehicle?.kind?.packs ?: 6)
            override fun mayPlace(stack: ItemStack)=vehicle?.let { !it.plugged && it.items.canPlaceItem(i,stack) } ?: (i<(vehicle?.kind?.packs ?: 6) && RoadVehicles.battery(stack)!=null)
            override fun mayPickup(player: Player)=vehicle?.plugged!=true
            override fun getMaxStackSize()=1
        })
        for(i in 0..8)addSlot(object: Slot(contents,i+6,8+i*18,74) { override fun mayPlace(stack: ItemStack)=contents.canPlaceItem(i+6,stack) })
        for(row in 0..2)for(col in 0..8)addSlot(Slot(inv,col+row*9+9,8+col*18,106+row*18))
        for(col in 0..8)addSlot(Slot(inv,col,8+col*18,164))
    }
    override fun stillValid(player: Player)=vehicle?.items?.stillValid(player)==true
    override fun removed(player: Player) { super.removed(player);contents.stopOpen(player) }
    override fun quickMoveStack(player: Player,index: Int): ItemStack {
        val slot=slots.getOrNull(index) ?: return ItemStack.EMPTY
        if(!slot.hasItem() || !slot.mayPickup(player))return ItemStack.EMPTY
        val stack=slot.item;val original=stack.copy()
        val moved=if(index<15)moveItemStackTo(stack,15,slots.size,true) else if(RoadVehicles.battery(stack)!=null)moveItemStackTo(stack,0,vehicle?.kind?.packs ?: 6,false) else moveItemStackTo(stack,6,15,false)
        if(!moved)return ItemStack.EMPTY
        if(stack.isEmpty)slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        return original
    }
}
