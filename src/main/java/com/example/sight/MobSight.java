package com.example.sight;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Враждебные мобы, которые замечали игрока слишком близко, видят дальше.
 * Кто и так смотрит дальше — зомби, блейз, эндермен, надсмотрщик, осада — не урезается и не растягивается.
 * Стену это не пробивает: без прямой видимости цель по-прежнему не берётся.
 * С первого дня, от шкалы угрозы не растёт.
 */
public final class MobSight {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — дальность снова как в обычной игре. Уже появившиеся мобы сами не откатываются. */
	public static final boolean ENABLED = true;

	/**
	 * Если моб замечает игрока не дальше этого числа блоков, дальность поднимается до {@link #SEE_BLOCKS}.
	 * 16 = как у скелета, крипера, паука и ведьмы в обычной игре. Поборник и брут видят ещё ближе, с 12,
	 * и тоже попадают сюда. Больше 16 не трогаем: зомби и так видит с 35.
	 */
	public static final double RAISE_IF_AT_MOST = 16.0;

	/**
	 * Новая дальность, блоки. 32 = вдвое дальше обычных 16.
	 * 24 = чуть дальше. 48 = почти как блейз. Меньше {@link #RAISE_IF_AT_MOST} ничего не меняет.
	 */
	public static final double SEE_BLOCKS = 32.0;

	// ========================================================================

	private MobSight() {
	}

	/** Конец появления. Особые мобы потом ставят свою дальность сами, если она больше. */
	public static void raiseShortSight(Mob mob) {
		if (!ENABLED || SEE_BLOCKS <= RAISE_IF_AT_MOST || mob.getType().getCategory() != MobCategory.MONSTER) {
			return;
		}
		var follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow == null || follow.getBaseValue() > RAISE_IF_AT_MOST) {
			return;
		}
		follow.setBaseValue(SEE_BLOCKS);
	}
}
