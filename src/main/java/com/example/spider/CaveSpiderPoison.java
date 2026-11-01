package com.example.spider;

import com.example.entity.LethalPoisonEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import org.jetbrains.annotations.Nullable;

/**
 * Укус пещерного паука снова может убить, как в бете 1.8.
 * Длительность яда мод не трогает — её задаёт сама игра в ударе паука:
 * легко — яда нет, нормально — Отравление I на 7 секунд, сложно — на 15 секунд.
 * Каждый тик снимает половину сердца (1 HP) раз в 1,25 секунды. Броня этот урон не режет.
 * Отличие от нынешней игры одно: последний тик может довести здоровье до нуля.
 * Яд ведьмы, слизня и зелий так не умеет: у них источник не пещерный паук.
 */
public final class CaveSpiderPoison {

	// ===================== SETTINGS (меняй эти числа) =====================

	/**
	 * true — яд именно пещерного паука может убить.
	 * false — как в обычной игре: отравление останавливается на половине сердца.
	 * Секунды укуса этим выключателем не меняются.
	 */
	public static final boolean ENABLED = true;

	// ========================================================================

	/** Столько HP снимает один тик яда в обычной игре. 1 = половина сердца. Броня не помогает. */
	private static final float TICK_DAMAGE = 1.0f;

	private CaveSpiderPoison() {
	}

	/**
	 * Яд только что повесился или продлился.
	 * Если удар не прошёл (яд слабее того, что уже висит), метку не трогаем:
	 * старый яд пещерного паука остаётся смертельным.
	 */
	public static void onPoisonApplied(LivingEntity target, MobEffectInstance effect, @Nullable Entity source, boolean applied) {
		if (!applied || !effect.getEffect().equals(MobEffects.POISON)) {
			return;
		}
		if (!(target instanceof LethalPoisonEntity data)) {
			return;
		}
		data.evolvingthreats$setLethalCavePoison(ENABLED && source instanceof CaveSpider);
	}

	/**
	 * Команда и маяк могут заменить яд, минуя обычное вешание эффекта.
	 * Если замена не случилась, метка старого яда остаётся.
	 */
	public static void onPoisonForced(LivingEntity target, MobEffectInstance effect, @Nullable Entity source) {
		if (!effect.getEffect().equals(MobEffects.POISON) || target.getEffect(MobEffects.POISON) != effect) {
			return;
		}
		onPoisonApplied(target, effect, source, true);
	}

	/**
	 * Тик отравления. Пока здоровье больше половины сердца, бьёт сама игра.
	 * На половине сердца и ниже добивает только яд пещерного паука.
	 */
	public static void onPoisonTick(ServerLevel level, LivingEntity target) {
		if (!ENABLED || target.getHealth() > 1.0f) {
			return;
		}
		if (!(target instanceof LethalPoisonEntity data) || !data.evolvingthreats$isLethalCavePoison()) {
			return;
		}
		target.hurtServer(level, target.damageSources().magic(), TICK_DAMAGE);
	}
}
