package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
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
 Shields in zombies and skeletons: a random pattern of the flag and a chance to completely extinguish the blow.
 The shield is always in the second hand. If it is already occupied (skeletal sword with a bow), the shield is not issued.
 */
public final class MobShields {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - shields are not issued and blows are not extinguished. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) that a zombie or skeleton will appear with a shield on day 100 (end of the threat scale). 0.25 = 25%.
	 At the beginning of the game, the chance is smaller and increases with the scale. Drops once at the mob. 0 = no shields.
	 */
	public static final float SPAWN_CHANCE = 0.25f;

	/**
	 Chance (0.0-1.0) that the impact on the mob with the shield will be completely absorbed. 0.10 = 10%.
	 It works on the impact of a creature or projectile, not on the fall, lava and emptiness.
	 * 0 = shield only beautiful, damage always passes.
	 */
	public static final float BLOCK_CHANCE = 0.10f;

	/**
	 * How many layers of pattern on the shield. 6 - like a full flag: along with the color of the base
	 These are millions of different drawings. Less – the drawing is simpler and reads better. 0 = color only, without pattern.
	 */
	public static final int PATTERN_LAYERS = 6;

	/**
	 The chance that the shield will fall out when the mob dies. 0.085 ≈ 8.5% like the reservation in {@link RandomArmor#ARMOR DROP CHANCE}.
	 * 1.0 = the shield always falls.
	 */
	public static final float DROP_CHANCE = 0.085f;

	// ========================================================================

	private MobShields() {
	}

	/*Call after issuing a weapon: the second hand may already be occupied. */
	public static void tryEquip(Mob mob) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return;
		}
		if (!mob.getOffhandItem().isEmpty()) {
			return;
		}
		RandomSource random = mob.getRandom();
		// GROWING: SPAWN CHANCE - the chance on the 100th day, at the beginning of the game it is less (ThreatTable.SHIELD START RATIO).
		if (random.nextFloat() >= ThreatScale.growingChance(mob.level(), SPAWN_CHANCE, ThreatTable.SHIELD_START_RATIO)) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.OFFHAND, createShield(mob, random));
		mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
	}

	/**
	 * Extinguish the impact if the shield falls in the second hand {@link #BLOCK CHANCE}
	 *
	 * @return true - damage doesn't go away
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

	/** Shield: random base color and {@link #PATTERN LAYERS} random flag layers. */
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

	/*All flag patterns from the game, except the "base": its color is set separately and the second layer only interferes. */
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
