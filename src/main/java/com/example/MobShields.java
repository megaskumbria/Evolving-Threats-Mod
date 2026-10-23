package com.example;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.ArrayList;
import java.util.List;

/**
 * Щиты у зомби и скелетов: случайный узор флага и шанс полностью погасить удар.
 * Щит всегда во второй руке. Если она уже занята (меч скелета с луком) — щит не выдаётся.
 */
public final class MobShields {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — щиты не выдаются и удары не гасятся. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что зомби или скелет появится со щитом. 0.25 = 25%.
	 * Бросается один раз на моба. 0 = щитов не будет.
	 */
	public static final float SPAWN_CHANCE = 0.25f;

	/**
	 * Шанс (0.0–1.0), что удар по мобу со щитом будет полностью поглощён. 0.10 = 10%.
	 * Срабатывает на удар существа или снаряда, не на падение, лаву и пустоту.
	 * 0 = щит только красивый, урон всегда проходит.
	 */
	public static final float BLOCK_CHANCE = 0.10f;

	/**
	 * Сколько слоёв узора на щите. 6 — как у полного флага: вместе с цветом основы
	 * это миллионы разных рисунков. Меньше — рисунок проще и читается лучше. 0 = только цвет, без узора.
	 */
	public static final int PATTERN_LAYERS = 6;

	/**
	 * Шанс, что щит выпадет при смерти моба. 0.085 ≈ 8.5%, как у брони в {@link RandomArmor#ARMOR_DROP_CHANCE}.
	 * 1.0 = щит падает всегда.
	 */
	public static final float DROP_CHANCE = 0.085f;

	// ========================================================================

	private MobShields() {
	}

	/** Вызвать после выдачи оружия: вторая рука к этому моменту уже может быть занята. */
	public static void tryEquip(Mob mob) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return;
		}
		if (!mob.getOffhandItem().isEmpty()) {
			return;
		}
		RandomSource random = mob.getRandom();
		if (random.nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.OFFHAND, createShield(mob, random));
		mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
	}

	/**
	 * Погасить удар, если во второй руке щит и выпал {@link #BLOCK_CHANCE}.
	 *
	 * @return true — урон не проходит
	 */
	public static boolean tryAbsorb(LivingEntity mob, DamageSource source, float damage) {
		if (!ENABLED || BLOCK_CHANCE <= 0.0f || damage <= 0.0f) {
			return false;
		}
		if (!mob.getOffhandItem().is(Items.SHIELD)) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (source.getEntity() == null && source.getDirectEntity() == null) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= BLOCK_CHANCE) {
			return false;
		}
		mob.level().playSound(
				null,
				mob.getX(),
				mob.getY(),
				mob.getZ(),
				SoundEvents.SHIELD_BLOCK,
				SoundSource.HOSTILE,
				1.0f,
				0.8f + mob.getRandom().nextFloat() * 0.4f);
		return true;
	}

	/** Щит: случайный цвет основы и {@link #PATTERN_LAYERS} случайных слоёв флага. */
	private static ItemStack createShield(Mob mob, RandomSource random) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		DyeColor[] colors = DyeColor.values();
		shield.set(DataComponents.BASE_COLOR, colors[random.nextInt(colors.length)]);

		List<Holder<BannerPattern>> patterns = patternsExceptBase(mob);
		if (patterns.isEmpty() || PATTERN_LAYERS <= 0) {
			return shield;
		}
		BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
		for (int i = 0; i < PATTERN_LAYERS; i++) {
			Holder<BannerPattern> pattern = patterns.get(random.nextInt(patterns.size()));
			DyeColor color = colors[random.nextInt(colors.length)];
			builder.add(pattern, color);
		}
		shield.set(DataComponents.BANNER_PATTERNS, builder.build());
		return shield;
	}

	/** Все узоры флагов из игры, кроме «основы»: её цвет задаётся отдельно и вторым слоем только мешает. */
	private static List<Holder<BannerPattern>> patternsExceptBase(Mob mob) {
		Registry<BannerPattern> registry = mob.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<Holder<BannerPattern>> patterns = new ArrayList<>();
		registry.listElements().forEach(holder -> {
			if (!holder.is(BannerPatterns.BASE)) {
				patterns.add(holder);
			}
		});
		return patterns;
	}
}
