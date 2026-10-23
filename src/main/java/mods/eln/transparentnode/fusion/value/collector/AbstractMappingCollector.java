// Adapted from ProjectE (MIT); see assets/eln/licenses/ProjectE.txt.
package mods.eln.transparentnode.fusion.value.collector;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mods.eln.transparentnode.fusion.value.arithmetic.IValueArithmetic;
import mods.eln.transparentnode.fusion.value.collector.IExtendedMappingCollector;

import net.minecraft.core.HolderLookup;

public abstract class AbstractMappingCollector<T, V extends Comparable<V>, A extends IValueArithmetic<?>> implements IExtendedMappingCollector<T, V, A> {

	private final A defaultArithmetic;

	AbstractMappingCollector(A defaultArithmetic) {
		this.defaultArithmetic = defaultArithmetic;
	}

	@Override
	public void addConversion(int outnumber, T output, Iterable<T> ingredients, A arithmeticForConversion) {
		addConversion(outnumber, output, listToMapOfCounts(ingredients), arithmeticForConversion);
	}

	private Object2IntMap<T> listToMapOfCounts(Iterable<T> iterable) {
		Object2IntMap<T> map = new Object2IntOpenHashMap<>();
		for (T ingredient : iterable) {
			map.mergeInt(ingredient, 1, Integer::sum);
		}
		return map;
	}

	@Override
	public void setValueFromConversion(int outnumber, T output, Iterable<T> ingredients) {
		setValueFromConversion(outnumber, output, listToMapOfCounts(ingredients));
	}

	@Override
	public A getArithmetic() {
		return this.defaultArithmetic;
	}

	@Override
	public void finishCollection(HolderLookup.Provider registries) {
	}
}