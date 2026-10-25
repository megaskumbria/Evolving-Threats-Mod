package com.example.entity;

/**
 * Костяная лошадь, на которую мод посадил скелета.
 * Обычная лошадь из грозы этот флаг не получает.
 */
public interface SkeletonHorsemanMount {

	boolean evolvingthreats$isHorseman();

	void evolvingthreats$setHorseman(boolean horseman);
}
