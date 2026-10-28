package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.VoltageState
import org.junit.Test
import kotlin.math.sqrt
import kotlin.test.*

class LocalCommandJacobianTest {
    @Test fun exactTransformerResponseMatchesIndependentCommandPerturbations() {
        fun network(name: String, volts: Double): Triple<SubSystem, SwitchableVoltageSource, SwitchableVoltageSource> {
            val system=SubSystem(null,.01);val a=VoltageState();val b=VoltageState()
            system.addState(a);system.addState(b)
            val port=SwitchableVoltageSource(name).apply { connectTo(a,null);voltage=volts;enabled=true }
            val drive=SwitchableVoltageSource("drive").apply { connectTo(b,null);voltage=volts*1.1;enabled=true }
            system.addComponent(port);system.addComponent(drive)
            system.addComponent(Resistor(a,b).apply { resistance=2.0 })
            return Triple(system,port,drive)
        }
        val (a,input,driveA)=network("primary",240.0)
        val (b,output,driveB)=network("secondary",120.0)
        val process=SafeTransformerProcess(input.aPin!!,output.aPin!!,input,output) { true }.apply { ratio=.5 }
        val commands=arrayOf(input,output,driveA,driveB)
        val start=commands.map { it.voltage }
        process.rootSystemPreStepProcess()
        commands.indices.forEach { commands[it].voltage=start[it] }
        val exact=assertNotNull(process.localCommandDerivative(commands))
        for(column in commands.indices) {
            fun sample(shift: Double): DoubleArray {
                commands.indices.forEach { commands[it].voltage=start[it] }
                commands[column].voltage+=shift;process.rootSystemPreStepProcess()
                return doubleArrayOf(input.voltage,output.voltage)
            }
            val h=.001;val plus=sample(h);val minus=sample(-h)
            for(row in 0..1)assertEquals((plus[row]-minus[row])/(2*h),exact[row][column],1e-9)
        }
        // Sensitivity extraction changes neither the electrical state nor physical time.
        assertEquals(0.0,input.aPin!!.state);assertEquals(0.0,output.aPin!!.state)
        assertTrue(a.hasValidStepSolution().not());assertTrue(b.hasValidStepSolution().not())
    }

    @Test fun serialAndSimultaneousChainsMatchWholeGroupDifferencesIncludingTrialClamps() {
        val system=SubSystem(null,.01)
        val sources=List(3) { i->
            val p=VoltageState();system.addState(p)
            SwitchableVoltageSource("s$i").apply { connectTo(p,null);enabled=true;system.addComponent(this) }
        }
        fun process(index: Int,law: ()->Double)=object: ConservativePowerProcess {
            override fun rootSystemPreStepProcess() { sources[index].voltage=law() }
            override fun controlledSources()=listOf(sources[index])
            override fun supportsLocalCommandDerivative()=true
            override fun connectedSystems()=setOf(system)
            override fun acceptsCandidate()=false
            override fun failClosed() {}
        }
        val group=listOf(process(0) { sqrt(sources[1].voltage*sources[1].voltage+4)+.1*sources[2].voltage },
            process(1) { .3*sources[0].voltage+.2*sources[2].voltage }, process(2) { 2.0 })
        for(simultaneous in listOf(false,true))for(clamped in listOf(false,true)) {
            val scales=doubleArrayOf(4.0,3.0,2.0);val weights=doubleArrayOf(1.0,2.0,3.0)
            val x=doubleArrayOf(1.0,.8,1.0)
            fun trial(i: Int,v: Double)=if(clamped && i==0)(v*scales[i]).coerceAtMost(4.0) else v*scales[i]
            fun evaluate(v: DoubleArray): DoubleArray {
                val initial=DoubleArray(3) { trial(it,v[it]) }
                sources.indices.forEach { sources[it].voltage=initial[it] }
                val next=initial.clone()
                for((i,p) in group.withIndex()) {
                    if(simultaneous)sources.indices.forEach { sources[it].voltage=initial[it] }
                    p.rootSystemPreStepProcess();next[i]=sources[i].voltage
                }
                return DoubleArray(3) { (next[it]/scales[it]-v[it])*weights[it] }
            }
            sources.indices.forEach { sources[it].voltage=trial(it,x[it]) }
            val actual=assertNotNull(LocalCommandJacobian.build(group,sources,sources,scales,weights,x,simultaneous,::trial))
            for(column in 0..2) {
                val h=1e-5
                val plus=evaluate(x.clone().apply { this[column]+=h })
                val minus=evaluate(x.clone().apply { this[column]-=h })
                for(row in 0..2)assertEquals((plus[row]-minus[row])/(2*h),actual[row][column],2e-6)
            }
        }
    }

    @Test fun unoptedControllersAndChangingSourceMasksUseTheOrdinaryFallback() {
        val system=SubSystem(null,.01);val pin=VoltageState();system.addState(pin)
        val source=SwitchableVoltageSource("s").apply { connectTo(pin,null);enabled=true;voltage=1.0;system.addComponent(this) }
        for(opted in listOf(false,true)) {
            val process=object: ConservativePowerProcess {
                override fun rootSystemPreStepProcess() { source.enabled=false }
                override fun controlledSources()=listOf(source)
                override fun supportsLocalCommandDerivative()=opted
                override fun connectedSystems()=setOf(system)
                override fun acceptsCandidate()=false
                override fun failClosed() {}
            }
            source.enabled=true
            assertNull(LocalCommandJacobian.build(listOf(process),listOf(source),listOf(source),doubleArrayOf(1.0),
                doubleArrayOf(1.0),doubleArrayOf(1.0),false) { _,v->v })
        }
    }

    @Test fun regulatedVoltageAndPowerLimitBranchesMatchTheFullControllerResponse() {
        for(powerLimit in listOf(100.0,1000.0)) {
            val primary=SubSystem(null,.01);val secondary=SubSystem(null,.01)
            val a=VoltageState();val feed=VoltageState();val b=VoltageState()
            primary.addState(a);primary.addState(feed);secondary.addState(b)
            val input=SwitchableVoltageSource("input").apply { connectTo(a,null);enabled=true;voltage=119.0 }
            val output=SwitchableVoltageSource("output").apply { connectTo(b,null);enabled=true;voltage=24.0 }
            val drive=SwitchableVoltageSource("drive").apply { connectTo(feed,null);enabled=true;voltage=120.0 }
            primary.addComponent(input);primary.addComponent(drive);secondary.addComponent(output)
            primary.addComponent(Resistor(feed,a).apply { resistance=.5 })
            secondary.addComponent(Resistor(b,null).apply { resistance=4.0 })
            val process=RegulatedPowerProcess(a,null,b,null,input,output,{ true },
                { ConverterLimits(.1,1000.0,1000.0,100.0,100.0,powerLimit,.97,1.0/256,256.0) },{ 24.0 })
            val sources=listOf(input,output,drive)
            val scales=doubleArrayOf(120.0,24.0,120.0);val x=doubleArrayOf(.99,1.0,1.0)
            fun evaluate(candidate: DoubleArray): DoubleArray {
                sources.indices.forEach { sources[it].voltage=candidate[it]*scales[it];sources[it].enabled=true }
                process.rootSystemPreStepProcess()
                assertTrue(input.enabled && output.enabled)
                return DoubleArray(3) { sources[it].voltage/scales[it]-candidate[it] }
            }
            evaluate(x)
            sources.indices.forEach { sources[it].voltage=x[it]*scales[it] }
            val local=assertNotNull(LocalCommandJacobian.build(listOf(process),sources,sources,scales,
                doubleArrayOf(1.0,1.0,1.0),x,false) { i,v->v*scales[i] })
            for(column in sources.indices) {
                val h=1e-5
                val plus=evaluate(x.clone().apply { this[column]+=h })
                val minus=evaluate(x.clone().apply { this[column]-=h })
                for(row in sources.indices)assertEquals((plus[row]-minus[row])/(2*h),local[row][column],1e-7)
            }
        }
    }
}
