package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 Participant of the raid "Raid anchor": anchor or servant (zombie / skeleton).
 * Implementation - mixins at {@link net.minecraft.world.entity.monster.zombie.Zombie}
 * and {@link net.minecraft.world.entity.monster.skeleton.AbstractSkeleton}.
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

	/** While true - the servants "see" the player via {@link com.example.entity.ai.RaidHuntGoal}. */
	boolean evolvingthreats$isRaidGpsActive();

	void evolvingthreats$setRaidGpsActive(boolean active);
}
