package com.example.entity;

/** У этого существа текущее отравление может снять последнее здоровье. */
public interface LethalPoisonEntity {

	boolean evolvingthreats$isLethalCavePoison();

	void evolvingthreats$setLethalCavePoison(boolean lethal);
}
