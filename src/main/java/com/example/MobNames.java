package com.example;

import net.minecraft.world.entity.Mob;

/**
 * Именные мобы не показывают имя всегда.
 * Игра сама рисует его, когда центральный прицел наведён на моба
 * и до него не больше примерно 64 блоков. Дальше имя скрыто, даже если целиться точно.
 * Это правило самой игры, отдельного числа в моде нет.
 * Вызвать после выдачи имени и ещё раз при загрузке мира:
 * иначе уже живущий моб останется с именем, которое видно издалека.
 */
public final class MobNames {

	private MobNames() {
	}

	public static void showOnlyWhenLookedAt(Mob mob) {
		mob.setCustomNameVisible(false);
	}
}
