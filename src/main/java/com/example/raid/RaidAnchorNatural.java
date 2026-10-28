package com.example.raid;

import com.example.siege.SiegeNight;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Естественное появление Рейдового якоря (без команды /etraid).
 *
 * <p>Как это работает: когда в верхнем мире ночью на поверхности появляется обычный зомби,
 * у него есть крошечный шанс не стать обычным, а превратиться в якорь. Вокруг сразу
 * появляются 12–15 прислуг (их число и снаряжение настраиваются в {@link RaidAnchor}).
 * Вызывается из ZombieMixin при спавне зомби — тот же приём, что у Гнилого надсмотрщика.
 *
 * <p>Шанс от дня НЕ растёт (метка ФИКС в mobs.txt): с 80-го дня он просто «включается».
 */
public final class RaidAnchorNatural {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — якорь больше не появляется сам (команда /etraid продолжает работать). */
	public static final boolean ENABLED = true;

	/**
	 * Шанс, что появившийся ночью на поверхности обычный зомби станет якорем.
	 * 0.001 = 0,1% (примерно один якорь на ~1000 зомби). Диапазон 0.0–1.0.
	 * Для сравнения: у надсмотрщика 0.002 (0,2%). Увеличишь — якоря пойдут чаще.
	 * Пауза ниже всё равно не даст им идти подряд.
	 */
	public static final float SPAWN_CHANCE = 0.001f;

	/**
	 * Минимум дней между двумя естественными якорями на весь мир.
	 * 5 = следующий не раньше, чем через 5 игровых дней. 0 = без паузы.
	 */
	public static final int MIN_DAYS_BETWEEN = 5;

	/**
	 * С какого дня якорь вообще может появиться — берётся из ThreatTable.RAID_ANCHOR_DAY (сейчас 80).
	 * Менять день нужно там, вместе с остальными порогами.
	 */
	public static final int FROM_DAY = ThreatTable.RAID_ANCHOR_DAY;

	// ========================================================================

	private RaidAnchorNatural() {
	}

	/**
	 * Пробует превратить только что заспавненного зомби в якорь.
	 *
	 * <p>Условия (все сразу): включено; естественный спавн игрой (не спавнер, не яйцо, не ритуал);
	 * верхний мир; ночь; зомби под открытым небом (в пещерах и данжах якорь не нужен — рейд там не развернуться);
	 * это именно обычный зомби (не житель, не хаск, не утопленник, не ребёнок); день не раньше {@link #FROM_DAY};
	 * не ночь осады; прошла пауза после прошлого якоря; выпал шанс.
	 *
	 * @return true — зомби стал якорем, дальше ему обычное снаряжение выдавать не нужно
	 */
	public static boolean tryConvertOnSpawn(Zombie zombie, ServerLevel level, EntitySpawnReason reason) {
		if (!ENABLED || reason != EntitySpawnReason.NATURAL) {
			return false;
		}
		if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside()) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby() || zombie.isPassenger()) {
			return false;
		}
		if (!ThreatScale.reached(level, FROM_DAY) || SiegeNight.isWindowOpen(level)) {
			return false;
		}
		BlockPos pos = zombie.blockPosition();
		if (!level.canSeeSky(pos)) {
			return false;
		}

		RaidAnchorData data = RaidAnchorData.get(level);
		int today = ThreatScale.day(level);
		// last > today бывает, если день перемотали командой назад. Тогда паузу не считаем.
		boolean pauseOver = data.lastNaturalAnchorDay < 0
				|| data.lastNaturalAnchorDay > today
				|| today - data.lastNaturalAnchorDay >= MIN_DAYS_BETWEEN;
		if (!pauseOver) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}

		ServerPlayer hunter = level.getNearestPlayer(zombie, RaidAnchor.HUNT_MAX_DISTANCE) instanceof ServerPlayer sp ? sp : null;
		RaidAnchor.convertToAnchor(level, zombie, hunter);

		data.lastNaturalAnchorDay = today;
		data.setDirty();
		return true;
	}
}
