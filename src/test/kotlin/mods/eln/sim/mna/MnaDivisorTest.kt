package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*

class MnaDivisorTest {
    private fun factor(matrix: Array<Array<DD>>,optimized: Boolean,previous: MnaFactorization?=null): MnaFactorization {
        val key="eln.disableCachedMnaDivisors";val saved=System.getProperty(key)
        try { System.setProperty(key,(!optimized).toString());return MnaFactorization(matrix,previous) }
        finally { if(saved==null)System.clearProperty(key) else System.setProperty(key,saved) }
    }
    private fun residual(matrix: Array<Array<DD>>,rhs: DoubleArray,solution: Array<DD>) {
        for(r in matrix.indices) {
            var error=DD.of(-rhs[r]);var magnitude=kotlin.math.abs(rhs[r])
            for(c in matrix.indices)if(!matrix[r][c].isZero) {
                val term=matrix[r][c].multiply(solution[c]);error=error.add(term)
                magnitude+=term.abs().toDouble()
            }
            assertTrue(error.abs().toDouble()<=1e-25*maxOf(1.0,magnitude),"row=$r residual=$error scale=$magnitude")
        }
    }
    @Test fun cachedDivisorsPreservePreciseSolutionsAndTransposeResponses() {
        val random=Random(146)
        for(size in listOf(3,24,80)) {
            val matrix=Array(size) { r -> Array(size) { c -> DD.of(
                if(r==c)random.nextDouble(10.0,30.0) else if(random.nextDouble()<.12)random.nextDouble(-1.0,1.0) else 0.0) } }
            val first=matrix[0];matrix[0]=matrix.last();matrix[size-1]=first
            val fast=factor(matrix,true);val original=factor(matrix,false)
            repeat(50) {
                val rhs=DoubleArray(size) { random.nextDouble(-14_000.0,14_000.0) }
                val a=fast.solve(rhs);val b=original.solve(rhs)
                residual(matrix,rhs,a)
                for(i in a.indices)assertTrue(a[i].subtract(b[i]).abs().toDouble()<1e-24)
            }
            for(index in listOf(0,size/2,size-1)) {
                val row=fast.inverseRow(index);val prior=original.inverseRow(index)
                for(c in matrix.indices) {
                    var error=DD.of(if(c==index)-1.0 else 0.0)
                    for(r in matrix.indices)error=error.add(row[r].multiply(matrix[r][c]))
                    assertTrue(error.abs().toDouble()<1e-25)
                    assertTrue(row[c].subtract(prior[c]).abs().toDouble()<1e-25)
                }
            }
            matrix[1][size/2]=matrix[1][size/2].add(.123)
            val rebuilt=factor(matrix,true,fast)
            assertTrue(rebuilt.reusedOrdering)
            val rhs=DoubleArray(size) { random.nextDouble(-100.0,100.0) }
            residual(matrix,rhs,rebuilt.solve(rhs))
        }
    }
    @Test fun extremeDivisorsKeepTheOriginalDivisionPrecision() {
        // A tiny reciprocal can lose its DD low part, even when v/v is exactly one.
        for(v in listOf(1e-300,1e-250,1e250,1e300)) {
            val matrix=arrayOf(arrayOf(DD.of(v),DD.ZERO),arrayOf(DD.ZERO,DD.of(-v)))
            val fast=factor(matrix,true);val original=factor(matrix,false)
            val rhs=doubleArrayOf(v,-v)
            val a=fast.solve(rhs);val b=original.solve(rhs)
            for(i in a.indices) { assertEquals(1.0,a[i].toDouble());assertEquals(b[i],a[i]) }
            for(i in matrix.indices)assertContentEquals(original.inverseRow(i),fast.inverseRow(i))
        }
    }
    private fun feeder(nodes: Int): Array<Array<DD>> {
        val matrix=Array(nodes+1) { Array(nodes+1) { DD.ZERO } }
        fun conductance(a: Int,b: Int,g: Double) {
            matrix[a][a]=matrix[a][a].add(g);matrix[b][b]=matrix[b][b].add(g)
            matrix[a][b]=matrix[a][b].subtract(g);matrix[b][a]=matrix[b][a].subtract(g)
        }
        for(i in 1 until nodes)conductance((i-1)/2,i,10_000.0+i)
        for(i in 0 until nodes)matrix[i][i]=matrix[i][i].add(.001)
        matrix[0][nodes]=DD.ONE;matrix[nodes][0]=DD.ONE
        return matrix
    }
    @Test fun reportsAlternatingFullSolveBenchmarks() {
        for(nodes in listOf(80,240)) {
            val matrix=feeder(nodes)
            val original=factor(matrix,false);val fast=factor(matrix,true)
            val inputs=List(32) { j -> DoubleArray(nodes+1) { if(it==nodes)14_000.0+sin(j*.1) else if(it%7==0).01 else 0.0 } }
            repeat(200) { original.solve(inputs[it%32]);fast.solve(inputs[it%32]) }
            fun time(f: MnaFactorization): Double {
                var checksum=0.0;val start=System.nanoTime()
                repeat(2000) { checksum+=f.solve(inputs[it%32])[it%nodes].toDouble() }
                check(checksum.isFinite());return (System.nanoTime()-start)/1e6
            }
            val baseline=ArrayList<Double>();val optimized=ArrayList<Double>()
            repeat(4) { if(it%2==0) { baseline+=time(original);optimized+=time(fast) } else { optimized+=time(fast);baseline+=time(original) } }
            residual(matrix,inputs[0],fast.solve(inputs[0]))
            println("MNA_DIVISOR_BENCH nodes=$nodes solves_per_round=2000 baseline_ms=$baseline optimized_ms=$optimized")
        }
    }
}
