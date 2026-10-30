package mods.eln.transparentnode.solarpanel;

import mods.eln.cable.CableRenderDescriptor;
import mods.eln.ghost.GhostGroup;
import mods.eln.misc.*;
import mods.eln.misc.Obj3D.Obj3DPart;
import mods.eln.node.transparent.TransparentNodeDescriptor;
import mods.eln.sim.ElectricalLoad;
import mods.eln.wiki.Data;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import mods.eln.client.gl.GL11;

import java.util.List;

import static mods.eln.i18n.I18N.tr;

public class SolarPanelDescriptor extends TransparentNodeDescriptor {

    final Coordinate groundCoordinate;
    boolean basicModel;
    private Obj3D obj;
    private Obj3DPart main;
    private Obj3DPart panel;

    public SolarPanelDescriptor(
            String name,
            Obj3D obj, CableRenderDescriptor cableRender,
            GhostGroup ghostGroup, int solarOffsetX, int solarOffsetY, int solarOffsetZ,
            Coordinate groundCoordinate, double openCircuitVoltage, double optimumVoltage,
            double optimumCurrent, double shortCircuitCurrent,
            double electricalDropFactor,
            double alphaMin, double alphaMax

    ) {
        super(name, SolarPanelElement.class, SolarPanelRender.class);
        this.groundCoordinate = groundCoordinate;
        this.ghostGroup = ghostGroup;

        electricalRs = optimumVoltage * optimumVoltage * electricalDropFactor
            / (optimumVoltage * optimumCurrent) / 2.0;
        this.solarOffsetX = solarOffsetX;
        this.solarOffsetY = solarOffsetY;
        this.solarOffsetZ = solarOffsetZ;
        this.alphaMax = alphaMax;
        this.alphaMin = alphaMin;
        basicModel = true;
        this.openCircuitVoltage = openCircuitVoltage;
        this.optimumVoltage = optimumVoltage;
        this.optimumCurrent = optimumCurrent;
        this.shortCircuitCurrent = shortCircuitCurrent;

        this.obj = obj;
        if (obj != null) {
            main = obj.getPart("main");
            panel = obj.getPart("panel");
        }

        this.cableRender = cableRender;

        canRotate = alphaMax != alphaMin;

        voltageLevelColor = VoltageLevelColor.fromVoltage(openCircuitVoltage);
    }


    public void setParent(net.minecraft.world.item.Item item, int damage) {
        super.setParent(item, damage);
        Data.addEnergy(newItemStack());
    }

    CableRenderDescriptor cableRender;
    double openCircuitVoltage;
    double optimumVoltage;
    double optimumCurrent;
    double shortCircuitCurrent;

    int solarOffsetX, solarOffsetY, solarOffsetZ;
    double alphaMin, alphaMax;
    //double efficiency;
    double electricalRs;

    boolean canRotate;

    public void applyTo(ElectricalLoad load) {
        load.setSerialResistance(electricalRs);
    }

    public double getStcPower() {
        return optimumVoltage * optimumCurrent;
    }


    public double alphaTrunk(double alpha) {
        if (alpha > alphaMax) return alphaMax;
        if (alpha < alphaMin) return alphaMin;
        return alpha;
    }

    void draw(float alpha, Direction front) {
        front.glRotateZnRef();
        GL11.glTranslatef(0, 0, main.getFloat("offZ"));
        if (main != null) main.draw();
        if (panel != null) {
            front.glRotateZnRefInv();
            panel.draw(alpha, 0f, 0f, 1f);
        }
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item,
                                         ItemRendererHelper helper) {
        return type != ItemRenderType.INVENTORY;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (type == ItemRenderType.INVENTORY) {
            super.renderItem(type, item, data);
        } else {
            draw((float) alphaMin, Direction.XN);
        }
    }

    @Override
    public void addInformation(ItemStack itemStack, Player entityPlayer, List<String> list, boolean par4) {
        super.addInformation(itemStack, entityPlayer, list, par4);

        list.add(tr("Produces power from solar radiation."));
        list.add("  " + tr("Open-circuit voltage: %1$V", Utils.plotValue(openCircuitVoltage)));
        list.add("  " + tr("Optimum voltage: %1$V", Utils.plotValue(optimumVoltage)));
        list.add("  " + tr("Optimum current: %1$A", Utils.plotValue(optimumCurrent)));
        list.add("  " + tr("Short-circuit current: %1$A", Utils.plotValue(shortCircuitCurrent)));
        list.add("  " + tr("STC power: %1$W", Utils.plotValue(getStcPower())));
        if (canRotate) list.add(tr("Can be geared towards the sun."));
    }

    @Override
    public mods.eln.misc.Direction mountingFront(Level world, net.minecraft.core.BlockPos pos, Direction proposed) {
        SolarRoofMount roof = SolarRoofMount.at(world,pos);
        return roof.getUphill()==null ? proposed : Direction.fromFacing(roof.getUphill().getOpposite());
    }

    @Override
    public GhostGroup getGhostGroupAt(Coordinate coord, Direction front) {
        return SolarRoofMount.at(coord.world(),coord.getPos()).footprint(ghostGroup,front);
    }

    @Override
    public void addCollisionBoxesToList(AABB query, List<AABB> list, Level world, int x, int y, int z) {
        net.minecraft.core.BlockPos pos=new net.minecraft.core.BlockPos(x,y,z);
        var ghost = world==null ? null : mods.eln.Eln.ghostManager.getGhost(new Coordinate(x,y,z,world));
        if(ghost!=null)pos=ghost.getObservatorCoordonate().getPos();
        SolarRoofMount roof=world==null ? new SolarRoofMount(null) : SolarRoofMount.at(world,pos);
        // Narrow strips follow the sloped panel instead of leaving a full cube over the roof.
        double base=x-pos.getX();
        double bz=z-pos.getZ();
        if(roof.getPitched()) {
            for(int strip=0;strip<8;strip++) {
                double a=strip/8.0,b=(strip+1)/8.0;
                var up=roof.getUphill();
                double h0=up.getStepX()*(base+a-.5)+up.getStepZ()*(bz+a-.5);
                double h1=up.getStepX()*(base+b-.5)+up.getStepZ()*(bz+b-.5);
                AABB bb=up.getAxis()==net.minecraft.core.Direction.Axis.X
                    ? new AABB(x+a,pos.getY()+Math.min(h0,h1),z,x+b,pos.getY()+Math.max(h0,h1)+.16,z+1)
                    : new AABB(x,pos.getY()+Math.min(h0,h1),z+a,x+1,pos.getY()+Math.max(h0,h1)+.16,z+b);
                if(query.intersects(bb))list.add(bb);
            }
        } else {
            AABB bb=new AABB(x,y,z,x+1,y+(canRotate?1:.5),z+1);
            if(query.intersects(bb))list.add(bb);
        }
    }
}
