package com.example.skeleton;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Попадание спектральной стрелы скелета: враждебные мобы рядом бросают свои дела и идут на этого игрока.
 * Мирные животные не реагируют. Цель принудительно обновляется, иначе моб забывает игрока, как только теряет его из виду.
 */
public final class SpectralArrowHunt {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Радиус (блоки) вокруг попавшего игрока. Мобы дальше этого в момент попадания не узнают о нём.
	 * Кто был внутри, идёт на игрока, даже если потом вышел из радиуса.
	 */
	public static final double AGGRO_RADIUS = 80.0;

	/**
	 * Сколько секунд мобы помнят игрока после попадания. 30 = полминуты.
	 * Пока время не вышло, они не переключаются на другую цель.
	 */
	public static final int HUNT_SECONDS = 30;

	// ========================================================================

	private static final Map<UUID, Hunt> HUNTS = new HashMap<>();

	private record Hunt(UUID playerId, long untilGameTime) {
	}

	private SpectralArrowHunt() {
	}

	/** Вызвать, когда спектральная стрела скелета попала в игрока. */
	public static void alert(Player player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		long until = now + HUNT_SECONDS * 20L;
		HUNTS.entrySet().removeIf(entry -> entry.getValue().untilGameTime < now);

		AABB area = player.getBoundingBox().inflate(AGGRO_RADIUS);
		for (Monster monster : level.getEntitiesOfClass(Monster.class, area, mob -> mob.distanceTo(player) <= AGGRO_RADIUS)) {
			HUNTS.put(monster.getUUID(), new Hunt(player.getUUID(), until));
			monster.setTarget(player);
		}
	}

	/** Перед шагом навигации: вернуть цель, если охота ещё идёт. */
	public static void keepChasing(Mob mob) {
		Hunt hunt = HUNTS.get(mob.getUUID());
		if (hunt == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (!mob.isAlive() || level.getGameTime() > hunt.untilGameTime) {
			HUNTS.remove(mob.getUUID());
			return;
		}
		Player player = level.getPlayerByUUID(hunt.playerId);
		if (player == null || !player.isAlive() || player.level() != level) {
			HUNTS.remove(mob.getUUID());
			return;
		}
		if (mob.getTarget() != player) {
			mob.setTarget(player);
		}
	}

	/** Моб исчез — не держать его в списке охоты. */
	public static void forget(Mob mob) {
		HUNTS.remove(mob.getUUID());
	}
}
