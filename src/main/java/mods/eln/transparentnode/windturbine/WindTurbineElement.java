package mods.eln.transparentnode.windturbine;

import mods.eln.Eln;
import mods.eln.i18n.I18N;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.NodePeriodicPublishProcess;
import mods.eln.node.transparent.TransparentNode;
import mods.eln.node.transparent.TransparentNodeDescriptor;
import mods.eln.node.transparent.TransparentNodeElement;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.mna.component.SwitchableVoltageSource;
import mods.eln.mechanical.ShaftGeneratorProcess;
import mods.eln.sim.nbt.NbtElectricalLoad;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WindTurbineElement extends TransparentNodeElement {
    private final NbtElectricalLoad positiveLoad = new NbtElectricalLoad("positiveLoad");
    final SwitchableVoltageSource powerSource = new SwitchableVoltageSource("powerSource");
    final WindTurbineSlowProcess slowProcess = new WindTurbineSlowProcess("slowProcess", this);
    final ShaftGeneratorProcess electricalProcess;
    final WindTurbineDescriptor descriptor;
    private Direction cableFront = Direction.ZP;

    public WindTurbineElement(TransparentNode transparentNode, TransparentNodeDescriptor descriptor) {
        super(transparentNode, descriptor);

        this.descriptor = (WindTurbineDescriptor) descriptor;
        powerSource.connectTo(positiveLoad, null);
        WindTurbineDescriptor d = this.descriptor;
        electricalProcess = new ShaftGeneratorProcess(positiveLoad, null, powerSource,
            () -> Math.max(0.0, Math.min(d.maxVoltage,
                d.nominalVoltage * slowProcess.getRotorRads() / d.nominalRotorRads)),
            slowProcess::getRotorEnergy, d.maxVoltage / d.regulatorVoltageFilterTime,
            d.regulatorDroopResistance, d.regulatorCurrentLimit, d.generationEfficiency, false);
        electricalProcessList.add(slowProcess::advanceRotor);

        electricalLoadList.add(positiveLoad);
        electricalComponentList.add(powerSource);
        slowProcessList.add(new NodePeriodicPublishProcess(transparentNode, 4, 4));
        slowProcessList.add(slowProcess);
    }

    @Override
    public ElectricalLoad getElectricalLoad(Direction side, LRDU lrdu) {
        if (lrdu != LRDU.Down) return null;
        if (side == cableFront.left()) return positiveLoad;
        return null;
    }

    @Nullable
    @Override
    public ThermalLoad getThermalLoad(@NotNull Direction side, @NotNull LRDU lrdu) {
        return null;
    }

    @Override
    public int getConnectionMask(Direction side, LRDU lrdu) {
        if (lrdu != LRDU.Down) return 0;
        if (side == cableFront.left()) return NodeBase.maskElectricalPower;
        if (side == cableFront.right() && !grounded) return NodeBase.maskElectricalPower;
        return 0;
    }

    @NotNull
    @Override
    public String multiMeterString(@NotNull Direction side) {
        return Utils.plotRads("", slowProcess.getRotorRads()) + Utils.plotUIP(positiveLoad.getVoltage(), powerSource.getCurrent());
    }

    @NotNull
    @Override
    public String thermoMeterString(@NotNull Direction side) {
        return "";
    }

    @Override
    public void initialize() {
        setPhysicalValue();
        connect();
    }

    private void setPhysicalValue() {
        descriptor.cable.applyTo(positiveLoad);
    }

    @Override
    public void connectJob() {
        super.connectJob();
        Eln.simulator.mna.addProcess(electricalProcess);
    }

    @Override
    public void disconnectJob() {
        super.disconnectJob();
        Eln.simulator.mna.removeProcess(electricalProcess);
    }

    @Override
    public boolean onBlockActivated(Player player, Direction side, float vx, float vy, float vz) {
        if (Utils.isPlayerUsingWrench(player)) {
            cableFront = cableFront.right();
            reconnect();
        }
        return false;
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            stream.writeFloat((float) slowProcess.getWind());
            stream.writeFloat((float) (slowProcess.getRotorRads() / descriptor.nominalRotorRads));
            node.lrduCubeMask.getTranslate(Direction.YN).serialize(stream);
        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    @Override
    public void writeToNBT(CompoundTag nbt) {
        super.writeToNBT(nbt);
        cableFront.writeToNBT(nbt, "cableFront");
        slowProcess.writeToNBT(nbt, "");
        powerSource.writeToNBT(nbt, "");
        nbt.putDouble("regulatorFilteredOutputVoltage", powerSource.getVoltage());
        Utils.println(cableFront);
    }

    @Override
    public void readFromNBT(CompoundTag nbt) {
        super.readFromNBT(nbt);
        cableFront = Direction.readFromNBT(nbt, "cableFront");
        slowProcess.readFromNBT(nbt, "");
        powerSource.readFromNBT(nbt, "");
        Utils.println(cableFront);
    }

    @NotNull
    @Override
    public Map<String, String> getWaila() {
        Map<String, String> wailaList = new HashMap<String, String>();
        wailaList.put(I18N.tr("Generating"), powerSource.getPower() > 1e-7 ? I18N.tr("Yes") : I18N.tr("No"));
        wailaList.put(I18N.tr("Wind"), Utils.plotValue(slowProcess.getWind(), "m/s"));
        wailaList.put(I18N.tr("Rotor speed"), Utils.plotRads("", slowProcess.getRotorRads()));
        wailaList.put(I18N.tr("Produced power"), Utils.plotPower("", Math.max(0.0, powerSource.getPower())));
        if (Eln.config.getBooleanOrElse("ui.waila.easyMode", false)) {
            wailaList.put(I18N.tr("Voltage"), Utils.plotVolt("", positiveLoad.getVoltage()));
            wailaList.put(I18N.tr("Current"), Utils.plotAmpere("", powerSource.getCurrent()));
            wailaList.put(I18N.tr("Current limit"), Utils.plotAmpere("", this.descriptor.regulatorCurrentLimit));
            wailaList.put(I18N.tr("Rotor energy"), Utils.plotEnergy("", slowProcess.getRotorEnergy()));
        }
        return wailaList;
    }

}
