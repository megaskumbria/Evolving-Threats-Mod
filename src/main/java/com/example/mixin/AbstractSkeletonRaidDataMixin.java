package com.example.mixin;

import com.example.entity.RaidParticipantEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonRaidDataMixin implements RaidParticipantEntity {

	@Unique
	private boolean evolvingthreats$raidAnchor;

	@Unique
	private boolean evolvingthreats$raidMinion;

	@Unique
	@Nullable
	private UUID evolvingthreats$raidId;

	@Unique
	@Nullable
	private UUID evolvingthreats$raidHuntTargetId;

	@Unique
	private boolean evolvingthreats$raidGpsActive;

	@Override
	public boolean evolvingthreats$isRaidAnchor() {
		return evolvingthreats$raidAnchor;
	}

	@Override
	public boolean evolvingthreats$isRaidMinion() {
		return evolvingthreats$raidMinion;
	}

	@Override
	public void evolvingthreats$setRaidAnchor(boolean anchor) {
		this.evolvingthreats$raidAnchor = anchor;
	}

	@Override
	public void evolvingthreats$setRaidMinion(boolean minion) {
		this.evolvingthreats$raidMinion = minion;
	}

	@Override
	@Nullable
	public UUID evolvingthreats$getRaidId() {
		return evolvingthreats$raidId;
	}

	@Override
	public void evolvingthreats$setRaidId(@Nullable UUID raidId) {
		this.evolvingthreats$raidId = raidId;
	}

	@Override
	@Nullable
	public UUID evolvingthreats$getRaidHuntTargetId() {
		return evolvingthreats$raidHuntTargetId;
	}

	@Override
	public void evolvingthreats$setRaidHuntTargetId(@Nullable UUID playerId) {
		this.evolvingthreats$raidHuntTargetId = playerId;
	}

	@Override
	public boolean evolvingthreats$isRaidGpsActive() {
		return evolvingthreats$raidGpsActive;
	}

	@Override
	public void evolvingthreats$setRaidGpsActive(boolean active) {
		this.evolvingthreats$raidGpsActive = active;
	}
}
