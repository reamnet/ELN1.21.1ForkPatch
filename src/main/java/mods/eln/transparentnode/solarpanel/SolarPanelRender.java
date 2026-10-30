package mods.eln.transparentnode.solarpanel;

import mods.eln.cable.CableRenderDescriptor;
import mods.eln.cable.CableRenderType;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.LRDUMask;
import mods.eln.misc.RcInterpolator;
import mods.eln.node.transparent.TransparentNodeDescriptor;
import mods.eln.node.transparent.TransparentNodeElementInventory;
import mods.eln.node.transparent.TransparentNodeElementRender;
import mods.eln.node.transparent.TransparentNodeEntity;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SolarPanelRender extends TransparentNodeElementRender {

    public SolarPanelDescriptor descriptor;
    public SolarRoofMount roofMount = new SolarRoofMount(null);
    public boolean roofMountKnown = false;
    private CableRenderType renderPreProcess;

    public SolarPanelRender(TransparentNodeEntity tileEntity, TransparentNodeDescriptor descriptor) {
        super(tileEntity, descriptor);
        this.descriptor = (SolarPanelDescriptor) descriptor;

    }

    RcInterpolator interpol = new RcInterpolator(1f);
    boolean boot = true;

    @Override
    public void draw() {
        renderPreProcess = drawCable(Direction.YN, descriptor.cableRender, eConn, renderPreProcess);
        SolarRoofMount roof=SolarRoofMount.at(getTileEntity().getLevel(),getTileEntity().getBlockPos());
        mods.eln.client.gl.GL11.glPushMatrix();
        if(roof.getPitched()) {
            var up=roof.getUphill();
            mods.eln.client.gl.GL11.glTranslatef(0f,-.5f,0f);
            mods.eln.client.gl.GL11.glRotatef(45f,-up.getStepZ(),0f,up.getStepX());
            mods.eln.client.gl.GL11.glTranslatef(0f,.5f,0f);
        }
        descriptor.draw((float) (interpol.get() * 180 / Math.PI - 90), front);
        mods.eln.client.gl.GL11.glPopMatrix();
    }

    @Override
    public void refresh(float deltaT) {
        float alpha;
        if (hasTracker == false) {
            alpha = (float) descriptor.alphaTrunk(pannelAlphaSyncValue);
        } else {
            alpha = (float) descriptor.alphaTrunk(SolarRoofMount.at(getTileEntity().getLevel(),getTileEntity().getBlockPos()).targetAlpha(SolarPannelSlowProcess.getSolarAlpha(getTileEntity().getLevel())));
        }
        interpol.setTarget(alpha);
        if (boot) {
            boot = false;
            interpol.setValueFromTarget();
        }

        interpol.step(deltaT);
    }

    @Nullable
    @Override
    public CableRenderDescriptor getCableRenderSide(@NotNull Direction side, @NotNull LRDU lrdu) {
        return descriptor.cableRender;
    }

    public boolean pannelAlphaSyncNew = false;
    public float pannelAlphaSyncValue = -1234;

    public boolean hasTracker;

    LRDUMask eConn = new LRDUMask();

    @Override
    public void networkUnserialize(DataInputStream stream) {
        super.networkUnserialize(stream);

        try {

            hasTracker = stream.readBoolean();

            float pannelAlphaIncoming = stream.readFloat();

            if (pannelAlphaIncoming != pannelAlphaSyncValue) {
                pannelAlphaSyncValue = pannelAlphaIncoming;
                pannelAlphaSyncNew = true;
            }

            eConn.deserialize(stream);
            roofMount=SolarRoofMount.fromId(stream.readByte());roofMountKnown=true;

            renderPreProcess = null;

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void clientSetPannelAlpha(float value) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bos);

            preparePacketForServer(stream);

            stream.writeByte(SolarPanelElement.unserializePannelAlpha);
            stream.writeFloat(value);

            sendPacketToServer(bos);
        } catch (IOException e) {

            e.printStackTrace();
        }

    }

    TransparentNodeElementInventory inventory = new TransparentNodeElementInventory(1, 64, this);

    @Nullable
    @Override
    public Screen newGuiDraw(@NotNull Direction side, @NotNull Player player) {
        return new SolarPannelGuiDraw(player, inventory, this);
    }

    @Override
    public boolean cameraDrawOptimisation() {
        return false;
    }
}
