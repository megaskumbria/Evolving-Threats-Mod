package com.example.skeleton;

import com.example.threat.ThreatScale;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.Optional;

/**
 Skeleton with one harmful arrow in the second hand. The bow in the main hand releases it:
 A normal arrow from the air is no longer issued to him while this one is in the second hand.
 Close combat and shield are not given to such a skeleton - the second hand is occupied by an arrow.
 */
public final class HarmfulArrowSkeleton {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - these skeletons do not appear. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) that an ordinary skeleton or wanderer will appear with a harmful arrow on day 100
	 * (GROWING: 10 times smaller at the beginning of the game, see ThreatTable.DANGEROUS_START_RATIO). 0.20 = 20%.
	 Throws before an accidental sword and shield: if it falls out, the second hand is occupied by an arrow.
	 */
	public static final float SPAWN_CHANCE = 0.20f;

	/**
	 How many times longer than an ordinary arrow with a potion lasts the effect.
	 The vanilla arrow has a multiplier of 0.125: poison II lasts about 2.7 seconds.
	 0.25 = twice as long, poison II about 5.4 sec. Instant damage does not depend on duration.
	 */
	public static final float EFFECT_DURATION_SCALE = 0.25f;

	/**
	 The chance that an arrow will fall at death. 0.085 - 8.5% as armor.
	 * 1.0 = always falling.
	 Blindness, drying, hunger, nausea and levitation do not take advantage of this opportunity: they never fall out.
	 */
	public static final float DROP_CHANCE = 0.085f;

	/**
	 How many seconds of blindness does the player feel after being hit?
	 There is no such arrow in the game, the duration is selected separately. 8 = 8 seconds with almost no view.
	 */
	public static final int BLINDNESS_SECONDS = 8;

	/**
	 How many seconds of drying after hitting. 7 = slightly longer than a strong poison (about 5 seconds).
	 Level I: Black hearts tick, but not as often as the viser skeleton on II.
	 */
	public static final int WITHER_SECONDS = 7;

	/**
	 Hunger after hit, in minutes. Each skeleton throws its number from minimum to maximum.
	 * 1 and 10 = from one minute to ten. Level I: A strip of food goes away, but not in seconds.
	 */
	public static final int HUNGER_MIN_MINUTES = 1;
	public static final int HUNGER_MAX_MINUTES = 10;

	/**
	 Nausea after hit, in minutes. The same scatter as hunger: the screen swings from 1 to 10 minutes.
	 */
	public static final int NAUSEA_MIN_MINUTES = 1;
	public static final int NAUSEA_MAX_MINUTES = 10;

	/**
	 How many seconds of levitation? 8 = the player raises about 8 blocks.
	 When the effect is over, the fall already hits, but does not take you to the sky.
	 Level I, like the Shilker, is only shorter: the Shilker has about 10 seconds.
	 */
	public static final int LEVITATION_SECONDS = 8;

	/**
	 Instant damage I, in half-hearts, on top of the arrow hit. 6 = 3 hearts.
	 The game's short invulnerability after the arrow was swallowing this.
	 0 = the harming arrow deals no extra hearts.
	 */
	public static final float HARMING_DAMAGE = 6.0f;

	/**
	 Instant damage II, in half-hearts, on top of the arrow hit. 12 = 6 hearts.
	 Same invulnerability fix as {@link #HARMING_DAMAGE}.
	 */
	public static final float STRONG_HARMING_DAMAGE = 12.0f;

	// ========================================================================

	/**
	 Harmful potions. Ordinary, long and strong are different arrows.
	 Useful (power, treatment, invisibility, etc.) are not included.
	 Turtle power is also not: along with slowing down, it gives protection, it is easier for the player from it.
	 Wind charged, weaving, oozing and infested are not here. Nothing happens on hit:
	 a burst, cobwebs or slimes only if the target dies, and silverfish only sometimes on a later hit.
	 * Spectral arrow and effects without their potion are added separately to {@link #randomArrow}.
	 Blindness is painted with the color of weakness, drying with the color of weaving.
	 Hunger, nausea and levitation are painted with their color: olive, purple and pale blue.
	 */
	private static final List<Holder<Potion>> HARMFUL_POTIONS = List.of(
			Potions.SLOWNESS,
			Potions.LONG_SLOWNESS,
			Potions.STRONG_SLOWNESS,
			Potions.HARMING,
			Potions.STRONG_HARMING,
			Potions.POISON,
			Potions.LONG_POISON,
			Potions.STRONG_POISON,
			Potions.WEAKNESS,
			Potions.LONG_WEAKNESS
	);

	private HarmfulArrowSkeleton() {
	}

	/**
	 To summon the skeleton from the spawn to the sword and shield.
	 *
	 * @return true - the skeleton received an arrow, no other weapon in the second hand
	 */
	public static boolean tryEquip(Mob skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		RandomSource random = skeleton.getRandom();
		if (random.nextFloat() >= ThreatScale.dangerousChance(skeleton.level(), SPAWN_CHANCE)) {
			return false;
		}
		if (!skeleton.getMainHandItem().is(Items.BOW)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
		ItemStack arrow = randomArrow(random);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, arrow);
		skeleton.setDropChance(EquipmentSlot.OFFHAND, neverDrops(arrow) ? 0.0f : DROP_CHANCE);
		return true;
	}

	/** Accidental harmful arrow. All options are equally likely, including hunger, nausea and levitation. */
	private static ItemStack randomArrow(RandomSource random) {
		int extra = 6;
		int roll = random.nextInt(HARMFUL_POTIONS.size() + extra);
		int special = roll - HARMFUL_POTIONS.size();
		if (special == 0) {
			return new ItemStack(Items.SPECTRAL_ARROW);
		}
		if (special == 1) {
			return effectArrow(MobEffects.BLINDNESS, BLINDNESS_SECONDS, MobEffects.WEAKNESS.value().getColor(), "blindness");
		}
		if (special == 2) {
			return effectArrow(MobEffects.WITHER, WITHER_SECONDS, MobEffects.WEAVING.value().getColor(), "wither");
		}
		if (special == 3) {
			int minutes = randomMinutes(random, HUNGER_MIN_MINUTES, HUNGER_MAX_MINUTES);
			return effectArrow(MobEffects.HUNGER, minutes * 60, MobEffects.HUNGER.value().getColor(), "hunger");
		}
		if (special == 4) {
			int minutes = randomMinutes(random, NAUSEA_MIN_MINUTES, NAUSEA_MAX_MINUTES);
			return effectArrow(MobEffects.NAUSEA, minutes * 60, MobEffects.NAUSEA.value().getColor(), "nausea");
		}
		if (special == 5) {
			return effectArrow(MobEffects.LEVITATION, LEVITATION_SECONDS, MobEffects.LEVITATION.value().getColor(), "levitation");
		}
		ItemStack arrow = PotionContents.createItemStack(Items.TIPPED_ARROW, HARMFUL_POTIONS.get(roll));
		arrow.set(DataComponents.POTION_DURATION_SCALE, EFFECT_DURATION_SCALE);
		return arrow;
	}

	/*Full minutes from minimum to maximum, both edges come in. */
	private static int randomMinutes(RandomSource random, int minMinutes, int maxMinutes) {
		int min = Math.min(minMinutes, maxMinutes);
		int max = Math.max(minMinutes, maxMinutes);
		return min + random.nextInt(max - min + 1);
	}

	/**
	 An arrow with an effect not found among vanilla potions.
	 Color is taken from another arrow, seconds are already what the player feels.
	 */
	private static ItemStack effectArrow(Holder<MobEffect> effect, int seconds, int color, String name) {
		int storedTicks = Math.max(1, Math.round(seconds * 20.0f / EFFECT_DURATION_SCALE));
		ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
		arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(color),
				List.of(new MobEffectInstance(effect, storedTicks)),
				Optional.of(name)));
		arrow.set(DataComponents.POTION_DURATION_SCALE, EFFECT_DURATION_SCALE);
		return arrow;
	}

	/**
	 Harming arrow hit. Called after the game has already put the effect on the target.
	 The arrow's own hit starts a short invulnerability, and magic damage that is not
	 bigger than that hit is thrown away. These hearts are dealt on top of the arrow,
	 then the one-tick effect is removed so it does not land a second time.
	 Undead are healed instead, same as a harming potion in the normal game.
	 The cycling-effect skeleton uses the same harming arrow, so this covers that shot too.
	 */
	public static void onHarmingHit(Arrow arrow, LivingEntity target) {
		if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) {
			return;
		}
		ItemStack stack = arrow.getPickupItemStackOrigin();
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || !contents.hasEffects()) {
			return;
		}
		float scale = stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0f);
		contents.forEachEffect(effect -> {
			if (!effect.is(MobEffects.INSTANT_DAMAGE)) {
				return;
			}
			boolean strong = effect.getAmplifier() >= 1;
			if (target.isInvertedHealAndHarm()) {
				// A harming potion heals undead. 4 = 2 hearts, 8 = 4 hearts, as in the normal game.
				target.heal(strong ? 8.0f : 4.0f);
			} else {
				float damage = strong ? STRONG_HARMING_DAMAGE : HARMING_DAMAGE;
				if (damage > 0.0f) {
					target.damageCooldownTime = 0;
					target.hurtServer(level, target.damageSources().magic(), damage);
				}
			}
			target.removeEffect(MobEffects.INSTANT_DAMAGE);
		}, scale);
	}

	/**
	 Arrows without vanilla potion do not fall from the skeleton:
	 blindness, drying, hunger, nausea, levitation.
	 */
	private static boolean neverDrops(ItemStack arrow) {
		PotionContents contents = arrow.get(DataComponents.POTION_CONTENTS);
		return contents != null && !contents.customEffects().isEmpty();
	}
}
