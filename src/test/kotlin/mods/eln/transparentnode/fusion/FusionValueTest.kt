package mods.eln.transparentnode.fusion

import mods.eln.transparentnode.fusion.value.SimpleGraphMapper
import mods.eln.transparentnode.fusion.value.arithmetic.HiddenBigFractionArithmetic
import mods.eln.transparentnode.fusion.value.arithmetic.FullBigFractionArithmetic
import org.apache.commons.math3.fraction.BigFraction
import org.junit.Test
import kotlin.test.*

class FusionValueTest {
    @Test fun embeddedProjectEMapperPropagatesRecipesAndOutputCountsWithCheapestAlternatives() {
        val mapper=SimpleGraphMapper<String,BigFraction,FullBigFractionArithmetic>(FullBigFractionArithmetic())
        mapper.setValueBefore("iron",BigFraction(256))
        mapper.setValueBefore("copper",BigFraction(128))
        mapper.addConversion(1,"metal",listOf("iron"));mapper.addConversion(1,"metal",listOf("copper"))
        mapper.addConversion(1,"iron_block",List(9) { "iron" })
        mapper.addConversion(9,"nugget",listOf("iron"))
        mapper.addConversion(1,"iron",List(9) { "nugget" })
        mapper.addConversion(4,"plate",listOf("metal","metal"))
        mapper.addConversion(1,"unknown_block",listOf("unmapped_ingredient"))
        val result=mapper.generateValues()
        assertEquals(BigFraction(2304),result["iron_block"])
        assertEquals(BigFraction(256,9),result["nugget"],"Reversible ingot recipes retain their exact fractions")
        assertEquals(BigFraction(64),result["plate"])
        assertNull(result["unknown_block"])
    }
    @Test fun valuesControlBothSustainedDemandAndDurationWithinTheRequestedRange() {
        val cheap=FusionCost.fromValue(1.0)!!;val expensive=FusionCost.fromValue(1048576.0)!!
        assertEquals(100_000.0,cheap.watts);assertEquals(600.0,cheap.seconds);assertEquals(60_000_000.0,cheap.joules)
        assertEquals(500_000.0,expensive.watts);assertEquals(1800.0,expensive.seconds);assertEquals(900_000_000.0,expensive.joules)
        val iron=FusionCost.fromValue(2304.0)!!;val diamond=FusionCost.fromValue(73728.0)!!
        assertTrue(diamond.joules>iron.joules)
        assertNull(FusionCost.fromValue(0.0));assertNull(FusionCost.fromValue(Double.NaN))
        assertNull(FusionCost.fromValue(Double.POSITIVE_INFINITY))
    }
    @Test fun embeddedFractionArithmeticRetainsFreeValueAndDivisionSemantics() {
        val a=HiddenBigFractionArithmetic()
        assertEquals(BigFraction(2),a.div(BigFraction(5),2))
        assertEquals(BigFraction(1,2),a.div(BigFraction.ONE,2))
        assertTrue(a.isFree(a.mul(9,a.free)))
        assertEquals(BigFraction(3),a.add(a.free,BigFraction(3)))
    }
}
