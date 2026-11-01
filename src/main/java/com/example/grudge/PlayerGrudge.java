package com.example.grudge;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieGrudgeEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.ritual.RitualBand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 "Memory of the player": the zombie noticed or suffered damage from the player - continues the hunt,
 * even if vanilla dropped the target (the player hid behind the blocks/underground).
 * While memory is active, zombies don't go to torches ({@link com.example.entity.ai.LightSeekGoal}).
 *
 Overseers, raiders and ritual packs use their hunting - they do not need memory.
 */
public final class PlayerGrudge {

	// ===================== SETTINGS (change these numbers) =====================

	/*Enable player memory for ordinary zombies. */
	public static final boolean ENABLED = true;

	/**
	 At what distance (blocks) the zombie still remembers the player and holds the goal.
	 Then he forgets (as if he had gone too far). Coincides with the default digging radius.
	 */
	public static final double GRUDGE_MAX_DISTANCE = 32.0;

	/*Maximum hunting time without repeated contact (tics): 6000 = 5 minutes. 0 = no time limit. */
	public static final int GRUDGE_TIMEOUT_TICKS = 5 * 60 * 20;

	// ========================================================================

	private PlayerGrudge() {
	}

	public static boolean isEligible(Zombie zombie) {
		if (!ENABLED) {
			return false;
		}
		if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return false;
		}
		if (RitualBand.isRitual(zombie)) {
			return false;
		}
		return zombie instanceof ZombieGrudgeEntity;
	}

	/*Is there an active hunt (player within range, memory not reset) */
	public static boolean isPursuingPlayer(Zombie zombie) {
		return resolveGrudgePlayer(zombie) != null;
	}

	/**
	 Return the player to @code setTarget from memory. Called before the goalSelector tick.
	 In 26.3 on “odd” tics AI targets rotate without checking canContinueToUse – if the target
	 * null, {@code SpearUseGoal} (spear) falls off NPE. Therefore, synchronization is mandatory.
	 */
	public static void syncTarget(Zombie zombie) {
		if (!isEligible(zombie)) {
			return;
		}
		if (zombie.getTarget() instanceof Player player) {
			rememberPlayer(zombie, player);
		}
		ServerPlayer grudge = resolveGrudgePlayer(zombie);
		if (grudge != null && zombie.getTarget() != grudge) {
			zombie.setTarget(grudge);
		}
	}

	/*Remember the player (called when a zombie hits or hits him) */
	public static void rememberPlayer(Zombie zombie, Player player) {
		if (!isEligible(zombie) || !(zombie instanceof ZombieGrudgeEntity grudge)) {
			return;
		}
		grudge.evolvingthreats$setGrudgePlayerId(player.getUUID());
		grudge.evolvingthreats$setGrudgeStartedGameTime(zombie.level().getGameTime());
	}

	public static void clear(Zombie zombie) {
		if (zombie instanceof ZombieGrudgeEntity grudge) {
			grudge.evolvingthreats$setGrudgePlayerId(null);
			grudge.evolvingthreats$setGrudgeStartedGameTime(0L);
		}
	}

	@Nullable
	public static ServerPlayer resolveGrudgePlayer(Zombie zombie) {
		if (!isEligible(zombie) || !(zombie instanceof ZombieGrudgeEntity grudge)) {
			return null;
		}
		UUID id = grudge.evolvingthreats$getGrudgePlayerId();
		if (id == null) {
			return null;
		}
		if (!(zombie.level() instanceof ServerLevel serverLevel)) {
			return null;
		}

		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
		if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			clear(zombie);
			return null;
		}
		if (player.level() != zombie.level()) {
			clear(zombie);
			return null;
		}

		double maxSqr = GRUDGE_MAX_DISTANCE * GRUDGE_MAX_DISTANCE;
		if (zombie.distanceToSqr(player) > maxSqr) {
			clear(zombie);
			zombie.setTarget(null);
			return null;
		}

		if (GRUDGE_TIMEOUT_TICKS > 0) {
			long started = grudge.evolvingthreats$getGrudgeStartedGameTime();
			if (started > 0 && serverLevel.getGameTime() - started >= GRUDGE_TIMEOUT_TICKS) {
				clear(zombie);
				zombie.setTarget(null);
				return null;
			}
		}

		return player;
	}
}
