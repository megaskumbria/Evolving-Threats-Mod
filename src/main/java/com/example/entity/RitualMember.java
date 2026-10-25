package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Участник ритуальной стаи. Реализация — mixin на Zombie и на Skeleton. */
public interface RitualMember {

	boolean evolvingthreats$isRitual();

	void evolvingthreats$setRitual(boolean ritual);

	/** 1 игрок, 2 крипер, 3 пиглин, 4 иссушитель. 0 — не стая. */
	int evolvingthreats$getRitualHead();

	void evolvingthreats$setRitualHead(int head);

	@Nullable UUID evolvingthreats$getRitualGroup();

	void evolvingthreats$setRitualGroup(@Nullable UUID group);

	/** true — в руке красный факел, false — кость. */
	boolean evolvingthreats$isRitualTorch();

	void evolvingthreats$setRitualTorch(boolean torch);

	/** Хлопок при смерти уже был. Чтобы один моб не взрывался дважды. */
	boolean evolvingthreats$ritualExploded();

	void evolvingthreats$setRitualExploded(boolean exploded);
}
