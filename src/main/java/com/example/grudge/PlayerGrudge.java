package com.example.grudge;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieGrudgeEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * «Память об игроке»: зомби заметил или получил урон от игрока — продолжает охоту,
 * даже если ваниль сбросила цель (игрок спрятался за блоками / под землёй).
 * Пока память активна, зомби не идёт на факелы ({@link com.example.entity.ai.LightSeekGoal}).
 *
 * Надсмотрщик и рейд используют свою охоту — для них память не нужна.
 */
public final class PlayerGrudge {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить память об игроке для обычных зомби. */
	public static final boolean ENABLED = true;

	/**
	 * На какой дистанции (блоки) зомби ещё помнит игрока и держит цель.
	 * Дальше — забывает (как «ушёл слишком далеко»). Совпадает с радиусом копания по умолчанию.
	 */
	public static final double GRUDGE_MAX_DISTANCE = 32.0;

	/** Максимум времени охоты без повторного контакта (тики): 6000 = 5 минут. 0 = без лимита по времени. */
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
		return zombie instanceof ZombieGrudgeEntity;
	}

	/** Есть ли активная охота (игрок в радиусе, память не сброшена). */
	public static boolean isPursuingPlayer(Zombie zombie) {
		return resolveGrudgePlayer(zombie) != null;
	}

	/**
	 * Вернуть игрока в {@code setTarget} из памяти. Вызывается перед тиком goalSelector.
	 * В 26.3 на «нечётных» тиках AI цели крутятся без проверки canContinueToUse — если цель
	 * null, {@code SpearUseGoal} (копьё) падает с NPE. Поэтому синхронизация здесь обязательна.
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

	/** Запомнить игрока (вызывается, когда зомби получил его в цель или ударил). */
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
