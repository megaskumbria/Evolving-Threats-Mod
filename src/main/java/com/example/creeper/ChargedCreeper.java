package com.example.creeper;

import com.example.mixin.CreeperPowerAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Обычный крипер изредка появляется уже заряженным, как после молнии.
 * Взрыв вдвое сильнее, с него может выпасть голова.
 * Динамит такой крипер не бросает. Подрывником или ломателем щита быть может.
 */
public final class ChargedCreeper {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — заряженные сами не появляются. Молния по-прежнему заряжает крипера. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что крипер при появлении в мире уже заряжен.
	 * 0.01 = 1%, примерно один из ста. Это реже подрывника и ломателя щита (у тех 5%).
	 * Бросок отдельный: заряженный может ещё оказаться подрывником или ломателем щита.
	 * Метателем динамита он не становится.
	 * 0 = сам не появляется. 1 = каждый крипер заряжен.
	 */
	public static final float SPAWN_CHANCE = 0.01f;

	// ========================================================================

	private ChargedCreeper() {
	}

	/** Вызвать из появления крипера, до броска «метатель динамита». Команда спавна шанс не бросает. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (creeper.isPowered()) {
			return;
		}
		if (creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		creeper.getEntityData().set(CreeperPowerAccessor.evolvingthreats$poweredId(), true);
	}
}
