package mods.eln.transparentnode.windturbine;

import mods.eln.misc.McBridge;
import mods.eln.misc.Coordinate;
import mods.eln.misc.INBTTReady;
import mods.eln.misc.Utils;
import mods.eln.sim.IProcess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

class WindTurbineSlowProcess implements IProcess, INBTTReady {
    private static final double AIR_DENSITY = 1.225;
    private static final double OPTIMAL_TIP_SPEED_RATIO = 6.0;
    private static final double MAX_POWER_COEFFICIENT = 0.36;
    private static final double CUT_IN_WIND = 3.0;
    private static final double CUT_OUT_DRAG_FACTOR = 0.015;
    //private static final double localWinDeriveMax = 0.1;
    private static final double environmentTimeCounterReset = 10.0;
    private static final double localWindTimeCounterReset = 1.0;
    private static final double localWindMax = 3.0;
    private static final double localWinDeriveLostFactor = 0.3;
    private static final double localWinDeriveDeriveMax = 0.1;

    private final WindTurbineElement turbine;
    private final String name;

    private double environmentWindFactor = 0.0;
    private double environmentTimeCounter = 0;
    private double localWind = 0;
    private double localWindDerive = 0;
    private double localWindTimeCounter = 0;
    private double rotorRads = 0.0;
    private double lastElectricalPower = 0.0;
    private double lastAerodynamicPower = 0.0;
    private double stallFactor = 0.0;
    private double lastLossPower = 0.0;

    WindTurbineSlowProcess(String name, WindTurbineElement turbine) {
        this.turbine = turbine;
        this.name = name;
    }

    double getWind() {
        return Math.abs(localWind + Utils.getWind(turbine.node.coordinate.dimension, turbine.node.coordinate.y +
            turbine.descriptor.offY)) * environmentWindFactor;
    }

    double getRotorRads() {
        return rotorRads;
    }

    double getRotorEnergy() {
        return rotorEnergy(rotorRads, turbine.descriptor.rotorInertia);
    }

    double getLastElectricalPower() {
        return lastElectricalPower;
    }

    double getLastAerodynamicPower() {
        return lastAerodynamicPower;
    }

    static double optimalRotorRads(double wind, double rotorRadius) {
        if (rotorRadius <= 0.0) return 0.0;
        return Math.max(0.0, OPTIMAL_TIP_SPEED_RATIO * wind / rotorRadius);
    }

    static double powerCoefficient(double rotorRads, double wind, double rotorRadius) {
        if (wind < CUT_IN_WIND || rotorRads <= 0.0 || rotorRadius <= 0.0) return 0.0;
        double ratio = rotorRads * rotorRadius / (wind * OPTIMAL_TIP_SPEED_RATIO);
        if (ratio <= 0.0) return 0.0;
        double curve = 2.0 * ratio / (1.0 + ratio * ratio);
        return MAX_POWER_COEFFICIENT * Math.max(0.0, Math.min(1.0, curve));
    }

    static double availableWindPower(double wind, double sweptArea) {
        if (wind < CUT_IN_WIND) return 0.0;
        return 0.5 * AIR_DENSITY * sweptArea * wind * wind * wind;
    }

    static double aerodynamicPower(double rotorRads, double wind, double rotorRadius, double sweptArea) {
        if (wind < CUT_IN_WIND) return 0.0;
        double windPower = availableWindPower(wind, sweptArea);
        if (rotorRads <= 0.1) {
            return windPower * MAX_POWER_COEFFICIENT * 0.08;
        }
        return windPower * powerCoefficient(rotorRads, wind, rotorRadius);
    }

    private static double rotorEnergy(double rads, double inertia) {
        return 0.5 * inertia * rads * rads;
    }

    private void setRotorEnergy(double energy) {
        if (!Double.isFinite(energy) || energy <= 0.0) {
            rotorRads = 0.0;
            return;
        }
        rotorRads = Math.sqrt(2.0 * energy / turbine.descriptor.rotorInertia);
    }

    void setWind(double wind) {
        this.localWind = wind;
    }

    @Override
    public void process(double time) {
        if (!Double.isFinite(time) || time <= 0.0) return;
        WindTurbineDescriptor d = turbine.descriptor;
        environmentTimeCounter -= time;
        if (environmentTimeCounter < 0.0) {
            environmentTimeCounter += environmentTimeCounterReset * (0.75 + Math.random() * 0.5);

            int x1, x2, y1, y2, z1, z2;

            Coordinate coord = new Coordinate(turbine.node.coordinate);

            x1 = coord.x - d.rayX;
            x2 = coord.x + d.rayX;
            y1 = coord.y - d.rayY + d.offY;
            y2 = coord.y + d.rayY + d.offY;
            z1 = coord.z - d.rayZ;
            z2 = coord.z + d.rayZ;

            int blockBusyCount = -d.blockMalusSubCount;
            boolean notInCache = false;
            if (turbine.node.coordinate.getWorldExist()) {
                Level world = turbine.node.coordinate.world();
                //IChunkProvider chunk = world.getChunkProvider();

                for (int x = x1; x <= x2; x++) {
                    for (int y = y1; y <= y2; y++) {
                        for (int z = z1; z <= z2; z++) {
                            if (!McBridge.isBlockLoaded(world, x, y, z)) {
                                notInCache = true;
                                break;
                            }
                            if (McBridge.getBlock(world, x, y, z) != Blocks.AIR) {
                                blockBusyCount++;
                            }
                        }
                        if (notInCache) break;
                    }
                    if (notInCache) break;
                }
            } else {
                notInCache = true;
            }
            if (!notInCache) {
                environmentWindFactor = Math.max(0.0, Math.min(1.0, 1.0 - blockBusyCount * d.blockMalus));

                Utils.println("EnvironementWindFactor : " + environmentWindFactor);
            }
        }

        localWindTimeCounter -= time;
        if (localWindTimeCounter < 0) {
            localWindTimeCounter += localWindTimeCounterReset;

            localWindDerive *= 1 - (localWinDeriveLostFactor * localWindTimeCounterReset);
            localWindDerive += (Math.random() * 2.0 - 1.0) * localWinDeriveDeriveMax * localWindTimeCounterReset;
        }

        localWind += localWindDerive * time;

        if (localWind > localWindMax) {
            localWind = localWindMax;
            localWindDerive = 0.0;
        }
        if (localWind < -localWindMax) {
            localWind = -localWindMax;
            localWindDerive = 0.0;
        }

    }

    double maximumRotorRads() {
        WindTurbineDescriptor d = turbine.descriptor;
        return d.nominalRotorRads * d.maxVoltage / d.nominalVoltage;
    }

    double governedRotorRads(double wind) {
        if (!Double.isFinite(wind) || wind < CUT_IN_WIND || wind >= turbine.descriptor.maxWind) return 0.0;
        return Math.min(maximumRotorRads(), optimalRotorRads(wind, turbine.descriptor.rotorRadius) * 1.25);
    }

    double getLastLossPower() { return lastLossPower; }

    void advanceRotor(double seconds) { advanceRotor(seconds, getWind()); }

    /** Once after each accepted electrical step, never during algebraic trials. */
    void advanceRotor(double seconds, double wind) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) return;
        WindTurbineDescriptor d = turbine.descriptor;
        double limit = governedRotorRads(wind);
        double optimum = Math.min(d.nominalRotorRads, optimalRotorRads(wind, d.rotorRadius));
        double governor = limit <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0,
            (limit - rotorRads) / Math.max(0.01, limit - optimum)));
        double aeroPower = governor == 0.0 ? 0.0 : Math.min(d.nominalPower / d.generationEfficiency,
            aerodynamicPower(rotorRads, wind, d.rotorRadius, d.rotorSweptArea) * governor);
        double electricalPower = Math.max(0.0, turbine.powerSource.getPower());
        double shaftElectricalPower = electricalPower / d.generationEfficiency;
        double energy = getRotorEnergy();
        // Furl/brake on overspeed, calm weather or storm cutout. This removes
        // energy as a mechanical loss rather than exporting it as unpaid power.
        double brakePower = Math.max(0.0, energy - rotorEnergy(limit, d.rotorInertia)) / 5.0;
        double requestedLoss = CUT_OUT_DRAG_FACTOR * rotorRads * rotorRads +
            0.15 * Math.max(rotorRads, 1.0) + brakePower;
        double available = Math.max(0.0, energy + (aeroPower - shaftElectricalPower) * seconds);
        double losses = Math.min(requestedLoss * seconds, available);
        double maximumEnergy = rotorEnergy(maximumRotorRads(), d.rotorInertia);
        double next = Math.min(maximumEnergy, available - losses);
        losses += available - losses - next;
        setRotorEnergy(next);
        lastLossPower = losses / seconds + shaftElectricalPower - electricalPower;
        stallFactor = optimum <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, rotorRads / optimum));
        lastElectricalPower = electricalPower;
        lastAerodynamicPower = aeroPower;
    }

    private static double finiteClamp(double value, double low, double high) {
        return Double.isFinite(value) ? Math.max(low, Math.min(high, value)) : 0.0;
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        localWind = finiteClamp(nbt.getDouble(str + name + "localWind"), -localWindMax, localWindMax);
        environmentWindFactor = finiteClamp(nbt.getDouble(str + name + "environementWindFactor"), 0.0, 1.0);
        rotorRads = finiteClamp(nbt.getDouble(str + name + "rotorRads"), 0.0, maximumRotorRads());
        lastElectricalPower = nbt.getDouble(str + name + "lastElectricalPower");
        lastAerodynamicPower = nbt.getDouble(str + name + "lastAerodynamicPower");
        stallFactor = nbt.getDouble(str + name + "stallFactor");
    }

    @Override
    public void writeToNBT(CompoundTag nbt, String str) {
        nbt.putDouble(str + name + "localWind", localWind);
        nbt.putDouble(str + name + "environementWindFactor", environmentWindFactor);
        nbt.putDouble(str + name + "rotorRads", rotorRads);
        nbt.putDouble(str + name + "lastElectricalPower", lastElectricalPower);
        nbt.putDouble(str + name + "lastAerodynamicPower", lastAerodynamicPower);
        nbt.putDouble(str + name + "stallFactor", stallFactor);
    }
}
