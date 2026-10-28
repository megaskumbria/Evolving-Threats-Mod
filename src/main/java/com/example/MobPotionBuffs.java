package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import com.example.tool.FloatingTool;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

import java.util.ArrayList;
import java.util.List;

/**
 * Моб может появиться сразу под несколькими полезными зельями.
 * Эффект не кончается, пока моб жив, и сыпет частицы, как у игрока с зельем.
 *
 * <p>В игре 25 полезных зелий, но долгая версия при бесконечном эффекте ничем не отличается
 * от обычной: время и так не идёт. Поэтому здесь 10 разных эффектов.
 * Лечения нет: это мгновенное зелье, и если сделать его бесконечным, моб лечится каждый миг.
 */
public final class MobPotionBuffs {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — мобы появляются без этих зелий. */
	public static final boolean ENABLED = true;

	/**
	 * Сколько эффектов сразу на 100-й день (КОНЕЦ шкалы угрозы). Индекс 0 = ни одного, 1 = один, … 5 = пять.
	 * Числа — вес (шанс относительно остальных). {50, 24, 13, 7, 4, 2} в сумме 100:
	 * без зелья 50%, одно 24%, два 13%, три 7%, четыре 4%, пять 2%.
	 * В начале игры веса другие (ThreatTable.POTION_COUNT_WEIGHTS_START: почти у всех ни одного зелья)
	 * и плавно приходят к этим значениям вместе со шкалой и луной.
	 * Хочешь, чтобы пять эффектов почти не встречались — уменьши последнее число.
	 * Больше пяти не добавляй: на мобе всего 10 разных эффектов.
	 */
	public static final int[] COUNT_WEIGHTS = { 50, 24, 13, 7, 4, 2 };

	/**
	 * Шанс (0.0–1.0), что эффект будет II уровня, если у него есть сильное зелье.
	 * 0.33 ≈ 33%, то есть примерно одно из трёх: обычное, долгое и сильное, а долгое здесь то же, что обычное.
	 * Касается только прыжка, скорости, регенерации и силы. У остальных сильной версии нет.
	 * 0 = всегда I уровень. 1 = всегда II, где он бывает.
	 */
	public static final float STRONG_LEVEL_CHANCE = 0.33f;

	/**
	 * Сколько секунд эффект из тумана крипера держится на том, кто в него вошёл.
	 * Сам туман живёт 15 секунд, как у обычного крипера: это делает игра, не это число.
	 * 45 = как у взрывного зелья обычной длины (четверть от трёх минут).
	 * На живом крипере эффект по-прежнему не кончается. Меняется только туман после взрыва.
	 */
	public static final int CLOUD_EFFECT_SECONDS = 45;

	/**
	 * Игра умножает время эффекта в тумане крипера на это число. 0.25 = четверть.
	 * Поэтому в туман записывается в 4 раза больше, а на игроке остаётся {@link #CLOUD_EFFECT_SECONDS}.
	 */
	private static final float VANILLA_CREEPER_CLOUD_SCALE = 0.25f;

	// ========================================================================

	/**
	 * Полезные эффекты. Второй флаг — есть ли сильное зелье (уровень II).
	 * Ночное зрение и удача почти не помогают мобу в бою, но остаются в списке:
	 * это тоже полезные зелья, и частицы от них видны.
	 * Подводное дыхание не даёт зомби захлебнуться и превратиться в утопленника.
	 */
	private static final List<Buff> BUFFS = List.of(
			new Buff(MobEffects.NIGHT_VISION, false),
			new Buff(MobEffects.INVISIBILITY, false),
			new Buff(MobEffects.JUMP_BOOST, true),
			new Buff(MobEffects.FIRE_RESISTANCE, false),
			new Buff(MobEffects.SPEED, true),
			new Buff(MobEffects.WATER_BREATHING, false),
			new Buff(MobEffects.REGENERATION, true),
			new Buff(MobEffects.STRENGTH, true),
			new Buff(MobEffects.LUCK, false),
			new Buff(MobEffects.SLOW_FALLING, false)
	);

	private MobPotionBuffs() {
	}

	/**
	 * Крипер, эндермен, блейз, фантом, слизень и магмовый куб, страж и древний страж,
	 * шалкер, пиглин и брут, хоглин и зоглин.
	 * Древний страж — это тоже страж, поэтому отдельной проверки нет.
	 * У части из них нет своего появления, поэтому вызов идёт из общего спавна моба.
	 */
	public static void tryApplyExtra(Mob mob) {
		if (mob instanceof Creeper
				|| mob instanceof Enderman
				|| mob instanceof Blaze
				|| mob instanceof Phantom
				|| mob instanceof Slime
				|| mob instanceof MagmaCube
				|| mob instanceof Guardian
				|| mob instanceof Shulker
				|| mob instanceof Piglin
				|| mob instanceof PiglinBrute
				|| mob instanceof Hoglin
				|| mob instanceof Zoglin) {
			tryApply(mob);
		}
	}

	/** Вызвать один раз, когда моб только появился. Повторно при загрузке мира не вызывается. */
	public static void tryApply(Mob mob) {
		if (!ENABLED || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		int count = rollCount(mob);
		if (count <= 0) {
			return;
		}
		List<Buff> pool = new ArrayList<>(BUFFS);
		// Крипер и парящий инструмент без невидимости.
		// Крипера и так легко не заметить, а у инструмента нет тела: зелье прячет его целиком.
		// Остальные эффекты из списка им по-прежнему выпадают. Снять запрет — убрать эту проверку.
		if (mob instanceof Creeper || mob instanceof FloatingTool) {
			pool.removeIf(buff -> buff.effect == MobEffects.INVISIBILITY);
		}
		RandomSource random = mob.getRandom();
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			Buff buff = pool.remove(random.nextInt(pool.size()));
			int amplifier = buff.canBeStrong && random.nextFloat() < STRONG_LEVEL_CHANCE ? 1 : 0;
			// ambient = false — обычные частицы, как у выпитого зелья, а не тусклые от маяка.
			mob.addEffect(new MobEffectInstance(
					buff.effect,
					MobEffectInstance.INFINITE_DURATION,
					amplifier,
					false,
					true,
					true));
		}
	}

	/**
	 * Туман после взрыва крипера копирует зелья с моба. Бесконечное время так и осталось бы
	 * бесконечным: игра его не укорачивает. Здесь оно становится конечным.
	 * Обычное зелье со своим временем (крипера облили из бутылки) не трогаем.
	 */
	public static MobEffectInstance forDeathCloud(MobEffectInstance effect) {
		if (effect == null || !effect.isInfiniteDuration()) {
			return effect;
		}
		int seconds = Math.max(1, CLOUD_EFFECT_SECONDS);
		int storedTicks = Math.max(1, Math.round(seconds * 20.0f / VANILLA_CREEPER_CLOUD_SCALE));
		return new MobEffectInstance(
				effect.getEffect(),
				storedTicks,
				effect.getAmplifier(),
				effect.isAmbient(),
				effect.isVisible(),
				effect.showIcon());
	}

	/** 0 = без эффекта. Чем правее индекс в {@link #COUNT_WEIGHTS}, тем больше эффектов. */
	private static int rollCount(Mob mob) {
		// РАСТЁТ: в начале игры почти у всех ноль зелий, веса плавно доходят до COUNT_WEIGHTS к 100-му дню.
		return ThreatScale.pickWeighted(mob.level(), mob.getRandom(), ThreatTable.POTION_COUNT_WEIGHTS_START, COUNT_WEIGHTS);
	}

	private record Buff(Holder<MobEffect> effect, boolean canBeStrong) {
	}
}
