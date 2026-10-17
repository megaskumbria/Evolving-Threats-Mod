package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Участник рейда «Рейдовый якорь»: якорь или прислуга (зомби/скелет).
 * Реализация — mixins на {@link net.minecraft.world.entity.monster.zombie.Zombie}
 * и {@link net.minecraft.world.entity.monster.skeleton.AbstractSkeleton}.
 */
public interface RaidParticipantEntity {

	boolean evolvingthreats$isRaidAnchor();

	boolean evolvingthreats$isRaidMinion();

	default boolean evolvingthreats$isRaidParticipant() {
		return evolvingthreats$isRaidAnchor() || evolvingthreats$isRaidMinion();
	}

	void evolvingthreats$setRaidAnchor(boolean anchor);

	void evolvingthreats$setRaidMinion(boolean minion);

	@Nullable
	UUID evolvingthreats$getRaidId();

	void evolvingthreats$setRaidId(@Nullable UUID raidId);

	@Nullable
	UUID evolvingthreats$getRaidHuntTargetId();

	void evolvingthreats$setRaidHuntTargetId(@Nullable UUID playerId);

	/** Пока true — прислуги «видят» игрока через {@link com.example.entity.ai.RaidHuntGoal}. */
	boolean evolvingthreats$isRaidGpsActive();

	void evolvingthreats$setRaidGpsActive(boolean active);
}
