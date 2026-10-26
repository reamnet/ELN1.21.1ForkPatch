package mods.eln.gridnode

import mods.eln.misc.stackFromNbt

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.INBTTReady
import mods.eln.misc.UserError
import mods.eln.node.NodeManager
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

import java.util.HashSet
import java.util.Optional
import mods.eln.misc.writeToNBT

/**
 * Created by svein on 23/08/15.
 */
class GridLink : INBTTReady {

    internal var a = Coordinate()
    internal var b = Coordinate()

    internal var connected = false
    // Drop this if the link is broken.
    lateinit internal var cable: ItemStack
    private var aTerminal=0
    private var bTerminal=0
    private var ae = Optional.empty<GridElement>()
    private var be = Optional.empty<GridElement>()
    private var ab: ElectricalConnection? = null
    private val extraConnections=ArrayList<ElectricalConnection>()
    private val extraThermals=ArrayList<WireSpanThermal>()
    private var rs = MnaConst.highImpedance
    internal var spanThermal: WireSpanThermal? = null

    constructor(a: Coordinate,b: Coordinate,`as`: Direction,bs: Direction,cable: ItemStack,rs: Double):this(a,b,`as`.int,bs.int,cable,rs)

    constructor(a: Coordinate, b: Coordinate, fromTerminal: Int, toTerminal: Int, cable: ItemStack, rs: Double) {
        this.rs = rs
        this.a = a
        this.b = b
        this.aTerminal = fromTerminal
        this.bTerminal = toTerminal
        this.cable = cable
    }

    constructor(nbt: CompoundTag, str: String) {
        readFromNBT(nbt, str)
    }

    fun elementA(): GridElement {
        if (!ae.isPresent) {
            ae = Optional.of(getElementFromCoordinate(a)!!)
        }
        return ae.get()
    }

    fun elementB(): GridElement {
        if (!be.isPresent) {
            be = Optional.of(getElementFromCoordinate(b)!!)
        }
        return be.get()
    }

    fun connect(): Boolean {
        val a = getElementFromCoordinate(this.a)
        val b = getElementFromCoordinate(this.b)

        if (a == null || b == null || connected) {
            return false
        }

        if(!a.acceptsSavedGridTerminal(aTerminal) || !b.acceptsSavedGridTerminal(bTerminal))return false

        // Both chunks save the span. Merge their records into one live link after reload.
        val canonical=(a.gridLinkList+b.gridLinkList).firstOrNull { other -> other!==this && sameTerminals(other) }
        if(canonical!=null) {
            a.gridLinkList.add(canonical);b.gridLinkList.add(canonical);canonical.connect();return false
        }
        ae=Optional.of(a);be=Optional.of(b)
        val loadsA=a.gridLoads(aTerminal);val loadsB=b.gridLoads(bTerminal)
        if(loadsA.isEmpty() || loadsA.size!=loadsB.size)throw UserError(tr("Invalid grid terminal circuit"))
        val utility=Eln.sixNodeItem.getDescriptor(cable) as? UtilityCableDescriptor
        val meters=if(utility!=null)utility.getRemainingLengthMeters(cable)/loadsA.size else cable.count.toDouble()/loadsA.size
        for(index in loadsA.indices) {
            val connection=if(utility!=null)WireSpanConnection(loadsA[index],loadsB[index],rs) else ElectricalConnection(loadsA[index],loadsB[index])
            if(index==0)ab=connection else extraConnections.add(connection)
            Eln.simulator.addElectricalComponent(connection)
            if(utility==null)connection.resistance=rs
            else {
                val thermal=if(index==0)spanThermal ?: WireSpanThermal(utility,meters).also { spanThermal=it }
                    else extraThermals.getOrNull(index-1) ?: WireSpanThermal(utility,meters).also { extraThermals.add(it) }
                thermal.connect(connection as WireSpanConnection,
                    { (a.spanAmbientCelsius()+b.spanAmbientCelsius())*.5 },{ onBreakElement() })
            }
        }

        // Add link to link lists.
        if (!a.gridLinkList.contains(this)) a.gridLinkList.add(this)
        if (!b.gridLinkList.contains(this)) b.gridLinkList.add(this)
        updateElement(a)
        updateElement(b)

        connected = true
        return true
    }

    private fun updateElement(e: GridElement) {
        e.updateIdealRenderAngle()
        // Need to also publish everything connected to this.
        val s = HashSet<GridElement>()
        s.add(e)
        for (link in e.gridLinkList) {
            s.add(link.elementA())
            s.add(link.elementB())
        }
        for (element in s) {
            element.needPublish()
        }
    }

    fun disconnect() {
        if (!connected)
            return

        val a = getElementFromCoordinate(this.a)
        val b = getElementFromCoordinate(this.b)

        spanThermal?.disconnect()
        extraThermals.forEach { it.disconnect() }
        extraConnections.forEach { Eln.simulator.removeElectricalComponent(it);it.breakConnection() };extraConnections.clear()
        Eln.simulator.removeElectricalComponent(ab)
        ab?.breakConnection()
        ab = null

        a?.let { updateElement(it) }
        b?.let { updateElement(it) }

        connected = false
    }

    private fun sameTerminals(other: GridLink)=
        a==other.a && b==other.b && aTerminal==other.aTerminal && bTerminal==other.bTerminal ||
        a==other.b && b==other.a && aTerminal==other.bTerminal && bTerminal==other.aTerminal

    private fun links(a: GridElement, b: GridElement): Boolean {
        if (this.a == a.coordinate()) {
            return this.b == b.coordinate()
        }
        if (this.a == b.coordinate()) {
            return this.b == a.coordinate()
        }
        return false
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        a.readFromNBT(nbt, str + "a")
        b.readFromNBT(nbt, str + "b")
        aTerminal=nbt.getInt(str+"as")
        bTerminal=nbt.getInt(str+"bs")
        rs = nbt.getDouble(str + "rs")
        cable = stackFromNbt(nbt)
        // Migrate the old saved, arbitrary resistance using the actual paid length in the cable stack.
        (Eln.sixNodeItem.getDescriptor(cable) as? UtilityCableDescriptor)?.let {
            val channels=if(aTerminal in 50..53)3 else 1
            val meters=it.getRemainingLengthMeters(cable)/channels
            rs = it.resistanceOhms(meters)
            spanThermal = WireSpanThermal(it, meters).also { thermal -> thermal.read(nbt) }
            extraThermals.clear()
            for(i in 1 until channels)extraThermals.add(WireSpanThermal(it,meters).also { t -> t.read(nbt.getCompound("spanThermal$i")) })
        }
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        a.writeToNBT(nbt, str + "a")
        b.writeToNBT(nbt, str + "b")
        nbt.putInt(str+"as",aTerminal)
        nbt.putInt(str+"bs",bTerminal)
        nbt.putDouble(str + "rs", rs)
        cable.writeToNBT(nbt)
        spanThermal?.write(nbt)
        extraThermals.forEachIndexed { i,t -> nbt.put("spanThermal${i+1}",CompoundTag().also(t::write)) }
    }

    fun selfDestroy() {
        onBreakElement()
    }

    fun onBreakElement(): ItemStack {
        val a = getElementFromCoordinate(this.a)
        val b = getElementFromCoordinate(this.b)
        a?.gridLinkList?.remove(this)
        b?.gridLinkList?.remove(this)
        disconnect()
        val thermal = spanThermal
        if (thermal != null && thermal.insulationDamaged) {
            thermal.descriptor.meltedDescriptor?.let {
                cable = it.newItemStack().also { stack -> it.setRemainingLengthMeters(stack, thermal.meters*(1+extraThermals.size)) }
            }
        }
        return cable
    }

    // Terminal lookup also runs while a saved span's other chunk is unloaded.
    fun getTerminal(element: GridElement)=if(element.coordinate()==a)aTerminal else bTerminal
    fun getSide(gridElement: GridElement): Direction = Direction.fromInt(getTerminal(gridElement)) ?: Direction.XN

    fun getOtherElement(gridElement: GridElement): GridElement {
        if (gridElement === elementA()) {
            return elementB()
        } else {
            return elementA()
        }
    }

    companion object {

        fun resistanceForCable(cable: ElectricalCableDescriptor, meters: Int): Double =
            if (cable is UtilityCableDescriptor) cable.resistanceOhms(meters.toDouble()) else cable.electricalRs * meters

        fun getElementFromCoordinate(coord: Coordinate?): GridElement? {
            if (coord == null) return null
            val element = NodeManager.instance!!.getTransparentNodeFromCoordinate(coord)
            if (element is GridElement) {
                return element
            } else {
                return null
            }
        }

        /** Shared by click-to-link and direct callers. Validate before changing either endpoint. */
        internal fun validateNewLink(a: GridElement, b: GridElement, fromSide: Direction, toSide: Direction,
                                     cable: ElectricalCableDescriptor, cableLength: Int) {
            validateOverheadEndpoints(a,b)
            if (!(a.transparentNodeDescriptor as GridDescriptor).acceptsGridCable(cable) ||
                !(b.transparentNodeDescriptor as GridDescriptor).acceptsGridCable(cable)) {
                throw UserError(tr("Grid links require an intact power cable; signal and melted cables are not supported"))
            }
            if (a === b || cableLength <= 0) throw UserError(tr("Invalid grid cable span"))
            if (a.gridLoads(fromSide.int).isEmpty() || b.gridLoads(toSide.int).isEmpty()) {
                throw UserError(tr("Select a grid terminal, not the device body"))
            }
        }

        internal fun linkStack(cable: ElectricalCableDescriptor, cableLength: Int, supplied: ItemStack?): ItemStack {
            if (cableLength <= 0) throw UserError(tr("Invalid grid cable span"))
            val stack = supplied?.copy() ?: when (cable) {
                is UtilityCableDescriptor -> cable.newItemStack(1).also { cable.setRemainingLengthMeters(it, cableLength.toDouble()) }
                else -> cable.newItemStack(cableLength)
            }
            if (stack.isEmpty || ElectricalCableDescriptor.getDescriptor(stack) !== cable) {
                throw UserError(tr("Grid cable item does not match the selected cable"))
            }
            if (cable is UtilityCableDescriptor) {
                val meters = cable.getRemainingLengthMeters(stack)
                if (stack.count != 1 || !meters.isFinite() || kotlin.math.abs(meters - cableLength) > UtilityCableDescriptor.LENGTH_METERS_EPSILON) {
                    throw UserError(tr("Grid cable item does not match the paid length"))
                }
            } else if (stack.count != cableLength) {
                throw UserError(tr("Grid cable item does not match the paid length"))
            }
            return stack
        }

        fun addLink(a: GridElement,b: GridElement,`as`: Direction,bs: Direction,cable: ElectricalCableDescriptor,cableLength: Int,cableStack: ItemStack?=null) =
            addLink(a,b,`as`.int,bs.int,cable,cableLength,cableStack)

        fun addLink(a: GridElement,b: GridElement,from: Int,to: Int,cable: ElectricalCableDescriptor,cableLength: Int,cableStack: ItemStack?=null) {
            validateOverheadEndpoints(a,b)
            if(!(a.transparentNodeDescriptor as GridDescriptor).acceptsGridCable(cable) || !(b.transparentNodeDescriptor as GridDescriptor).acceptsGridCable(cable))
                throw UserError(tr("Grid links require an intact power cable; signal and melted cables are not supported"))
            validateServiceCable(cable,from,to)
            val x=a.gridLoads(from);val y=b.gridLoads(to)
            if(a===b || x.isEmpty() || x.size!=y.size || cableLength<=0)throw UserError(tr("Select matching grid terminal circuits"))
            val ka=a.gridCircuitKey(from);val kb=b.gridCircuitKey(to)
            val phaseBranch=x.size==1 && (ka=="HV_SINGLE" && kb in setOf("HV0","HV1","HV2","HV_SINGLE") || kb=="HV_SINGLE" && ka in setOf("HV0","HV1","HV2","HV_SINGLE"))
            if(ka!=kb && ka!="LEGACY" && kb!="LEGACY" && !phaseBranch)throw UserError(tr("Grid phases or service voltages do not match"))
            if(phaseBranch && !GridPhaseRouting.compatible(a,from,b,to))throw UserError(tr("This single-phase run is already connected to a different HV phase"))
            if((a.gridLinkList+b.gridLinkList).any { it.links(a,b) && ((from<6 && to<6) || (it.a==a.coordinate() && it.aTerminal==from && it.bTerminal==to) || (it.a==b.coordinate() && it.bTerminal==from && it.aTerminal==to)) })
                throw UserError(tr("Already connected to this terminal"))
            // A bundled span and individual phase spans must never duplicate the same circuit.
            if(a.gridLinkList.any { old -> old.links(a,b) &&
                a.gridLoads(old.getTerminal(a)).zip(b.gridLoads(old.getTerminal(b))).any { (oldA,oldB) ->
                    x.zip(y).any { (newA,newB) -> oldA===newA && oldB===newB }
                } })throw UserError(tr("Already connected to this terminal"))
            if(x.size>1 && cable !is UtilityCableDescriptor && cableLength>cable.newItemStack().maxStackSize)
                throw UserError(tr("Use a cable spool for this long multiconductor connection"))
            val stack=linkStack(cable,cableLength,cableStack)
            val meters=cableLength.toDouble()/x.size
            val resistance=if(cable is UtilityCableDescriptor)cable.resistanceOhms(meters) else cable.electricalRs*meters
            val link=GridLink(a.coordinate(),b.coordinate(),from,to,stack,resistance)
            if(!link.connect())throw UserError(tr("Grid endpoint is no longer available"))
        }

        private fun validateOverheadEndpoints(a: GridElement,b: GridElement) {
            if(!a.allowsNewGridLinks || !b.allowsNewGridLinks)
                throw UserError(tr("Terminate overhead wires at a Service Weatherhead; use placed wall cables for meters and panels"))
        }

        internal fun validateServiceCable(cable: ElectricalCableDescriptor,from: Int,to: Int) {
            if(!GridCablePolicy.acceptsTerminal(cable,from) || !GridCablePolicy.acceptsTerminal(cable,to))
                throw UserError(tr("120/240 V service spans require insulated power cable"))
        }

    }
}

/** A span is additional wire, not merely the two pole contacts. Keep it across Rs notifications. */
internal class WireSpanConnection(private val from: ElectricalLoad, private val to: ElectricalLoad,
                                  var spanOhms: Double) : ElectricalConnection(from, to) {
    override fun notifyRsChange() {
        resistance = spanOhms + from.serialResistance + to.serialResistance
    }
}
