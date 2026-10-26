package mods.eln.transparentnode.industrial

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.*
import mods.eln.item.electricalitem.BatteryItem
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.*
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier
import kotlin.math.*

class PalletEntity(type: EntityType<out PalletEntity>,world: Level): Entity(type,world) {
    companion object {
        @JvmField var TYPE: Supplier<EntityType<PalletEntity>> = Supplier { error("Pallet type not registered") }
        val LOADED: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(PalletEntity::class.java,EntityDataSerializers.BOOLEAN)
    }
    val loaded get()=entityData.get(LOADED)
    var viewers=0;private set
    val items=object: SimpleContainer(27) {
        override fun setChanged() { super.setChanged();entityData.set(LOADED,!isEmpty) }
        override fun stillValid(player: Player)=this@PalletEntity.isAlive && player.distanceToSqr(this@PalletEntity)<64
        override fun startOpen(player: Player) { viewers++ }
        override fun stopOpen(player: Player) { viewers=max(0,viewers-1) }
        override fun canPlaceItem(slot: Int,stack: ItemStack)=GenericItemUsingDamageDescriptor.getDescriptor(stack) !is TransportItem
    }
    override fun defineSynchedData(builder: SynchedEntityData.Builder) { builder.define(LOADED,false) }
    fun packed(): ItemStack=Eln.findItemStack(IndustrialItems.PALLET,1).also { it.set(DataComponents.CONTAINER,ItemContainerContents.fromItems((0 until 27).map(items::getItem))) }
    fun unpack(stack: ItemStack) { val list=net.minecraft.core.NonNullList.withSize(27,ItemStack.EMPTY);stack.get(DataComponents.CONTAINER)?.copyInto(list);list.forEachIndexed(items::setItem) }
    override fun interact(player: Player,hand: InteractionHand): InteractionResult {
        if(level().isClientSide)return InteractionResult.SUCCESS
        if(player.isShiftKeyDown && player.getItemInHand(hand).isEmpty && viewers==0) { player.inventory.placeItemBackInInventory(packed());discard() }
        else if(player is ServerPlayer)player.openMenu(SimpleMenuProvider({ id,inv,_->ChestMenu.threeRows(id,inv,items) },Component.literal(tr("Warehouse Equipment Pallet"))))
        return InteractionResult.CONSUME
    }
    override fun tick() { super.tick();if(!level().isClientSide) { deltaMovement=deltaMovement.add(0.0,-.08,0.0);move(MoverType.SELF,deltaMovement);deltaMovement=deltaMovement.multiply(.6,.98,.6) } }
    override fun isPickable()=true
    override fun canBeCollidedWith()=true
    override fun hurt(source: DamageSource,amount: Float): Boolean {
        if(level().isClientSide || isInvulnerableTo(source) || viewers>0 || !isAlive)return false
        spawnAtLocation(packed());discard();return true
    }
    override fun readAdditionalSaveData(tag: CompoundTag) { unpack(stackFromNbt(tag.getCompound("pallet"))) }
    override fun addAdditionalSaveData(tag: CompoundTag) { tag.put("pallet",packed().writeToNBT(CompoundTag())) }
}

class ForkliftEntity(type: EntityType<out ForkliftEntity>,world: Level): Entity(type,world) {
    companion object {
        @JvmField var TYPE: Supplier<EntityType<ForkliftEntity>> = Supplier { error("Forklift type not registered") }
        val LIFT: EntityDataAccessor<Float> = SynchedEntityData.defineId(ForkliftEntity::class.java,EntityDataSerializers.FLOAT)
        val CHARGE: EntityDataAccessor<Float> = SynchedEntityData.defineId(ForkliftEntity::class.java,EntityDataSerializers.FLOAT)
        val CARGO: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(ForkliftEntity::class.java,EntityDataSerializers.BOOLEAN)
    }
    var pack=ItemStack.EMPTY;private set
    var cargo=ItemStack.EMPTY;private set
    private var throttle=0.0;private var steering=0.0;private var liftInput=0.0
    private var controlAge=100
    private var speed=0.0
    private var oldPickup=false
    var lastCargoResult=false;private set
    var controlPackets=0L;private set
    private var health=20f
    private var lerpSteps=0
    private var lerpPos=Vec3.ZERO
    private var lerpYaw=0f
    val lift get()=entityData.get(LIFT).toDouble()
    val stateOfCharge get()=entityData.get(CHARGE).toDouble()
    val hasCargo get()=entityData.get(CARGO)
    override fun defineSynchedData(builder: SynchedEntityData.Builder) { builder.define(LIFT,0f);builder.define(CHARGE,0f);builder.define(CARGO,false) }
    override fun getControllingPassenger(): LivingEntity?=firstPassenger as? Player
    override fun isControlledByLocalInstance()=false // server-authoritative movement; client sends controls, never positions
    override fun getPassengerAttachmentPoint(passenger: Entity,dimensions: EntityDimensions,partialTick: Float)=Vec3(0.0,.72,-.14)
    override fun isPickable()=true
    override fun canBeCollidedWith()=true
    fun controls(player: Player,forward: Double,turn: Double,up: Double,pickup: Boolean) {
        if(controllingPassenger!==player)return
        controlPackets++
        throttle=if(forward.isFinite())forward.coerceIn(-1.0,1.0) else 0.0
        steering=if(turn.isFinite())turn.coerceIn(-1.0,1.0) else 0.0
        liftInput=if(up.isFinite())up.coerceIn(-1.0,1.0) else 0.0
        controlAge=0
        if(pickup && !oldPickup)lastCargoResult=handleCargo()
        oldPickup=pickup
    }
    private fun battery()=(GenericItemUsingDamageDescriptor.getDescriptor(pack) as? BatteryItem)?.takeIf { it.getPriority(pack)==3 && it.getEnergy(pack).isFinite() }
    private fun spend(joules: Double): Double {
        val b=battery() ?: return 0.0
        val available=b.getEnergy(pack).coerceIn(0.0,b.getEnergyMax(pack))
        val used=min(available,max(0.0,joules));b.setEnergy(pack,available-used);return used
    }
    override fun interact(player: Player,hand: InteractionHand): InteractionResult {
        if(level().isClientSide)return InteractionResult.SUCCESS
        if(player.isShiftKeyDown && !isVehicle && abs(speed)<.05) {
            val stack=player.getItemInHand(hand);val b=IndustrialElement.battery(stack)
            if(pack.isEmpty && b?.getPriority(stack)==3) { pack=stack.copyWithCount(1);stack.shrink(1) }
            else if(stack.isEmpty && !pack.isEmpty) { player.inventory.placeItemBackInInventory(pack);pack=ItemStack.EMPTY }
            publishBattery();return InteractionResult.CONSUME
        }
        if(!isVehicle) { player.startRiding(this);player.displayClientMessage(Component.literal(tr("Forklift: movement keys drive; Page Up/Down lift; G pallet; sneak dismounts")),true) }
        return InteractionResult.CONSUME
    }
    private fun publishBattery() { val b=battery();entityData.set(CHARGE,if(b==null)0f else (b.getEnergy(pack)/b.getEnergyMax(pack)).coerceIn(0.0,1.0).toFloat());entityData.set(CARGO,!cargo.isEmpty) }
    override fun tick() {
        super.tick()
        if(level().isClientSide) {
            if(lerpSteps>0) { setPos(position().add(lerpPos.subtract(position()).scale(1.0/lerpSteps)));yRot+=net.minecraft.util.Mth.wrapDegrees(lerpYaw-yRot)/lerpSteps;lerpSteps-- }
            return
        }
        if(++controlAge>5 || !isVehicle) { throttle=0.0;steering=0.0;liftInput=0.0;if(!isVehicle)oldPickup=false }
        val mass=if(cargo.isEmpty)650.0 else 1650.0
        val hasEnergy=(battery()?.getEnergy(pack) ?: 0.0)>0
        val requested=if(hasEnergy)throttle*if(throttle>=0)3.6 else 1.6 else 0.0
        val next=speed+(requested-speed).coerceIn(-.07,.07)
        val needed=max(0.0,.5*mass*(next*next-speed*speed))/.8+abs(next)*mass*.02*9.81*.05+if(isVehicle)5.0 else 0.0
        val paid=spend(needed)
        speed=if(paid+1e-6>=needed)next else speed*.9
        if(abs(speed)<.001)speed=0.0
        yRot-=(steering*speed*.9).toFloat()
        val radians=yRot*PI/180
        deltaMovement=Vec3(-sin(radians)*speed*.05,deltaMovement.y-.08,cos(radians)*speed*.05)
        move(MoverType.SELF,deltaMovement)
        if(horizontalCollision)speed=0.0
        deltaMovement=Vec3(0.0,deltaMovement.y*.98,0.0)
        var change=(liftInput*.025).coerceIn(-lift,2.5-lift)
        if(change>0) {
            val energy=(if(cargo.isEmpty)70 else 1070)*9.81*change/.7
            change*=if(energy>0)spend(energy)/energy else 0.0
        }
        if(change!=0.0)entityData.set(LIFT,(lift+change).coerceIn(0.0,2.5).toFloat())
        if(speed<-.1 && tickCount%20==0)playSound(SoundEvents.NOTE_BLOCK_BIT.value(),.28f,1.7f)
        if(tickCount%10==0)publishBattery()
    }
    private fun forkPosition(): Vec3 { val r=yRot*PI/180;return position().add(-sin(r)*1.5,lift,cos(r)*1.5) }
    fun handleCargo(): Boolean {
        if(level().isClientSide || abs(speed)>.15)return false
        val at=forkPosition()
        if(cargo.isEmpty) {
            if(lift>.3)return false
            val pallet=level().getEntitiesOfClass(PalletEntity::class.java,boundingBox.inflate(2.0)).filter { it.viewers==0 && it.position().distanceToSqr(at)<1.2 }.minByOrNull { it.distanceToSqr(this) } ?: return false
            cargo=pallet.packed();pallet.discard()
        } else {
            val pallet=PalletEntity(PalletEntity.TYPE.get(),level());pallet.moveTo(at.x,at.y,at.z,yRot,0f);pallet.unpack(cargo)
            if(!level().noCollision(pallet,pallet.boundingBox) || !level().addFreshEntity(pallet))return false
            cargo=ItemStack.EMPTY
        }
        publishBattery();playSound(SoundEvents.IRON_TRAPDOOR_CLOSE,.3f,.7f);return true
    }
    override fun lerpTo(x: Double,y: Double,z: Double,yaw: Float,pitch: Float,steps: Int) { lerpPos=Vec3(x,y,z);lerpYaw=yaw;lerpSteps=steps.coerceIn(1,5) }
    override fun hurt(source: DamageSource,amount: Float): Boolean {
        if(level().isClientSide || isInvulnerableTo(source) || !isAlive)return false
        health-=amount
        if((source.entity as? Player)?.abilities?.instabuild==true || health<=0) {
            ejectPassengers();spawnAtLocation(Eln.findItemStack(IndustrialItems.FORKLIFT,1));if(!pack.isEmpty)spawnAtLocation(pack);if(!cargo.isEmpty)spawnAtLocation(cargo)
            pack=ItemStack.EMPTY;cargo=ItemStack.EMPTY;discard()
        }
        return true
    }
    override fun addAdditionalSaveData(tag: CompoundTag) { tag.put("tractionPack",pack.writeToNBT(CompoundTag()));tag.put("cargo",cargo.writeToNBT(CompoundTag()));tag.putDouble("lift",lift);tag.putFloat("health",health) }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        pack=stackFromNbt(tag.getCompound("tractionPack"));cargo=stackFromNbt(tag.getCompound("cargo"));entityData.set(LIFT,tag.getDouble("lift").takeIf { it.isFinite() }?.coerceIn(0.0,2.5)?.toFloat() ?: 0f);health=tag.getFloat("health").takeIf { it.isFinite() && it>0 } ?: 20f;publishBattery()
    }
}
