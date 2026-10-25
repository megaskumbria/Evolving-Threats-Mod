package com.example.phantom;

import com.example.entity.WeakeningPhantomEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;

/**
 * Часть фантомов вешает слабость, когда укус снял здоровье.
 * Кто умеет — решается один раз при появлении. Остальные кусают как раньше, только с уроном.
 * Щит, который отбил удар, и короткое мигание после удара слабость не дают.
 */
public final class PhantomWeakness {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — укус снова только с уроном, слабость не вешается даже у уже помеченных фантомов. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что фантом при появлении умеет вешать слабость.
	 * 0.30 = 30%, примерно каждый третий. Бросок один: ночная стая, яйцо и команда спавна.
	 * Такой фантом вешает слабость каждым укусом, который прошёл. Остальные — никогда.
	 * 0 = слабости нет. 1 = её вешает каждый фантом.
	 */
	public static final float SPAWN_CHANCE = 0.30f;

	/**
	 * Сколько секунд длится слабость. 10 = десять секунд.
	 * Повторный укус обновляет время, если на игроке слабость уже короче или такая же.
	 * Более долгая слабость (например от стрелы) этим укусом не укорачивается.
	 * Молоко снимает эффект сразу. 0 = эффект не появится.
	 */
	public static final int WEAKNESS_SECONDS = 10;

	/**
	 * Сила слабости. 0 = Слабость I: удары игрока вблизи слабее на 4 здоровья (2 сердца).
	 * 1 = Слабость II: слабее на 8 здоровья (4 сердца), меч часто почти не бьёт.
	 */
	public static final int AMPLIFIER = 0;

	// ========================================================================

	private PhantomWeakness() {
	}

	/** Один раз при появлении фантома. Повторный вызов уже помеченного не перебрасывает шанс. */
	public static void rollOnSpawn(Phantom phantom) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(phantom instanceof WeakeningPhantomEntity data)) {
			return;
		}
		if (data.evolvingthreats$weakensOnBite()) {
			return;
		}
		if (phantom.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setWeakensOnBite(true);
	}

	/**
	 * Укус уже снял здоровье.
	 * Не игрок сюда не попадает: голем, животное и другой моб слабость не получают.
	 */
	public static void onBite(Phantom phantom, LivingEntity target) {
		if (!ENABLED || WEAKNESS_SECONDS <= 0 || !(target instanceof Player)) {
			return;
		}
		if (!(phantom instanceof WeakeningPhantomEntity data) || !data.evolvingthreats$weakensOnBite()) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				MobEffects.WEAKNESS,
				WEAKNESS_SECONDS * 20,
				Math.max(0, AMPLIFIER),
				false,
				true,
				true), phantom);
	}
}
