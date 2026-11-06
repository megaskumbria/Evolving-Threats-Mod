package com.example.skeleton;

import com.example.advancement.ModAdvancements;
import com.example.entity.FastBowSkeletonEntity;
import com.example.entity.RandomArrowSkeletonEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 A skeleton with random arrows. In the hand is a regular bow, without an arrow with effect.
 The effect is selected at the time of the shot. There are ten different circles.
 The same thing is not repeated until the others have come out.
 The next lap does not start with the effect that just shot.
 */
public final class RandomArrowSkeleton {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - these skeletons do not appear. Those already alive are shooting as usual. */
	public static final boolean ENABLED = true;

	/**
	 The chance (0.0–1.0) that an ordinary skeleton or wanderer will shoot these effects.
	 * 0.01 = 1%, one in a hundred.
	 FIXED: From day one, the threat scale and the moon do not rise.
	 Two swords are removed bow - this skeleton does not throw this chance. 0 = commands only.
	 */
	public static final float SPAWN_CHANCE = 0.01f;

	/**
	 Arson after hit, in seconds. 5 = like a fire arrow in a normal game.
	 Fire resistance removes damage as usual. 1 = almost flash.
	 */
	public static final int FIRE_SECONDS = 5;

	/**
	 Blindness after hit, in seconds. Level I. 8 = almost without review.
	 A bad arrow with blindness also has 8.
	 */
	public static final int BLINDNESS_SECONDS = 8;

	/**
	 Poison after exposure, in seconds. Level I, not a strong poison.
	 * 5 = approximately like a conventional arrow of poison. 0 = no poison, the shot is still in line.
	 */
	public static final int POISON_SECONDS = 5;

	/**
	 Hunger after hit, in seconds. Level I.
	 * 30 = half a minute. The harmful arrow is hungry for minutes: here it is shorter, the shot is repeated in a circle.
	 */
	public static final int HUNGER_SECONDS = 30;

	/**
	 Slowdown after hit, in seconds. Level I.
	 15 = noticeable, but not for the whole fight. 0 = the shot is still in line.
	 */
	public static final int SLOWNESS_SECONDS = 15;

	/**
	 Weakness after hit, in seconds. Level I.
	 * 15 = weaker strokes for a short time. 0 = the shot is still in line.
	 */
	public static final int WEAKNESS_SECONDS = 15;

	/**
	 Drying after hit, in seconds. Level I, not like a dehumidifier skeleton on II.
	 * 7 = like a bad arrow with drying.
	 */
	public static final int WITHER_SECONDS = 7;

	/**
	 Nausea after hit, in seconds. Level I: The screen is rocking.
	 * 8 = short. The bad arrow has nausea for minutes, here the shot returns in a circle.
	 */
	public static final int NAUSEA_SECONDS = 8;

	/**
	 Levitation after hit, in seconds. Level I.
	 8 = rise of about 8 blocks, then fall. Like a bad arrow.
	 */
	public static final int LEVITATION_SECONDS = 8;

	/**
	 Instant damage, level. 0 = Level I, three hearts. 1 = Level II, six hearts.
	 There is no duration of this effect: it hits immediately.
	 */
	public static final int HARM_LEVEL = 0;

	// ========================================================================

	private static final String FIRE = "f";
	private static final String BLIND = "b";
	private static final String POISON = "p";
	private static final String HUNGER = "h";
	private static final String SLOW = "s";
	private static final String WEAK = "w";
	private static final String WITHER = "i";
	private static final String NAUSEA = "n";
	private static final String LEVITATE = "l";
	private static final String HARM = "d";
	private static final String[] EFFECTS = {
			FIRE, BLIND, POISON, HUNGER, SLOW, WEAK, WITHER, NAUSEA, LEVITATE, HARM
	};

	/*The name on the fire arrow so that the hit puts its arson, not just vanilla 5 seconds. */
	public static final String FIRE_ARROW_NAME = "random_fire";

	private static final float VANILLA_ARROW_SPEED = 1.6f;
	private static final double VANILLA_ARC = 0.2;

	private RandomArrowSkeleton() {
	}

	public static boolean isActive(AbstractSkeleton skeleton) {
		return ENABLED
				&& skeleton instanceof RandomArrowSkeletonEntity data
				&& data.evolvingthreats$isRandomArrow();
	}

	/**
	 * True - this skeleton shoots effects in a circle.
	 We do not put a harmful arrow in his hand; otherwise the same one would stick out in his hand.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || !(skeleton instanceof RandomArrowSkeletonEntity data)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		// The team puts the mark before it appears. Then we don't give up.
		if (data.evolvingthreats$isRandomArrow()) {
			return true;
		}
		if (SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setRandomArrow(true);
		return true;
	}

	/*If the set of mob took the bow, return it: without the bow, the circle of effects has nothing to shoot from. */
	public static void ensureBow(AbstractSkeleton skeleton) {
		if (!isActive(skeleton) || skeleton.isHolding(Items.BOW)) {
			return;
		}
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	/**
	 Team: Skeleton in front of the player, without chance.
	 Armor, charms on the bow and fast bow are hung with the same code as a regular spawn.
	 */
	public static void spawn(ServerLevel level, Vec3 pos, LivingEntity lookAt) {
		if (!ENABLED) {
			return;
		}
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof RandomArrowSkeletonEntity data) {
			data.evolvingthreats$setRandomArrow(true);
		}
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
	}

	/*Release the next arrow of the circle. False is not him or there is no bow in his hands. */
	public static boolean shoot(AbstractSkeleton skeleton, LivingEntity target, float power) {
		if (!isActive(skeleton) || !skeleton.isHolding(Items.BOW)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		String effect = nextEffect(skeleton);
		ItemStack projectile = arrow(effect);
		ItemStack bow = skeleton.getMainHandItem().is(Items.BOW)
				? skeleton.getMainHandItem()
				: skeleton.getOffhandItem();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(skeleton, projectile, power, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		if (FIRE.equals(effect)) {
			// In flight, the arrow burns, as with the spell of Fire. How much the player burns is in the hit.
			arrow.igniteForSeconds(100.0f);
		}
		double xd = target.getX() - skeleton.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - skeleton.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		float speed = VANILLA_ARROW_SPEED;
		double arc = VANILLA_ARC;
		// A fast bow is the same premium as the rest of the skeletons with this label.
		if (skeleton instanceof FastBowSkeletonEntity fast && fast.evolvingthreats$isFastBow()) {
			speed = FastBowSkeleton.ARROW_SPEED;
			arc = FastBowSkeleton.arrowArc();
		}
		float uncertainty = ((RangedAttackMob) skeleton).rangedAttackUncertainty(level);
		Projectile.spawnProjectileUsingShoot(arrow, level, projectile, xd, yd + distance * arc, zd, speed, uncertainty);
		skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		return true;
	}

	/*Arson of its duration. Vanilla after burning arrow puts 5 seconds, here is the number of settings. */
	/** A cycling arrow hit someone. Only a player hit counts, a miss does not. */
	public static void onPlayerHit(AbstractArrow arrow, Entity hit) {
		if (!(hit instanceof ServerPlayer player) || player.isSpectator()) {
			return;
		}
		if (arrow.getOwner() instanceof AbstractSkeleton skeleton && isActive(skeleton)) {
			ModAdvancements.onVariedArrow(player);
		}
	}

	public static void onFireHit(AbstractArrow arrow, Entity hit) {
		if (!(hit instanceof LivingEntity living) || FIRE_SECONDS <= 0) {
			return;
		}
		PotionContents contents = arrow.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS);
		if (contents == null || contents.customName().isEmpty() || !FIRE_ARROW_NAME.equals(contents.customName().get())) {
			return;
		}
		living.igniteForSeconds(FIRE_SECONDS);
	}

	private static String nextEffect(AbstractSkeleton skeleton) {
		RandomArrowSkeletonEntity data = (RandomArrowSkeletonEntity) skeleton;
		String queue = data.evolvingthreats$getArrowQueue();
		if (queue == null || queue.isEmpty()) {
			queue = shuffle(skeleton, data.evolvingthreats$getLastArrow());
		}
		String effect = queue.substring(0, 1);
		data.evolvingthreats$setArrowQueue(queue.substring(1));
		data.evolvingthreats$setLastArrow(effect);
		return effect;
	}

	/*All effects of the circle in random order. The first one doesn't equal the last shot. */
	private static String shuffle(AbstractSkeleton skeleton, String last) {
		List<String> bag = new ArrayList<>(List.of(EFFECTS));
		var random = skeleton.getRandom();
		for (int i = bag.size() - 1; i > 0; i--) {
			int swap = random.nextInt(i + 1);
			String tmp = bag.get(i);
			bag.set(i, bag.get(swap));
			bag.set(swap, tmp);
		}
		if (last != null && !last.isEmpty() && last.equals(bag.get(0)) && bag.size() > 1) {
			int swap = 1 + random.nextInt(bag.size() - 1);
			String tmp = bag.get(0);
			bag.set(0, bag.get(swap));
			bag.set(swap, tmp);
		}
		StringBuilder joined = new StringBuilder();
		for (String effect : bag) {
			joined.append(effect);
		}
		return joined.toString();
	}

	private static ItemStack arrow(String effect) {
		if (FIRE.equals(effect)) {
			return namedArrow(FIRE_ARROW_NAME, 0xFF6A00, List.of());
		}
		if (HARM.equals(effect)) {
			// Level I or II. The vanilla arrow does not need its own duration.
			ItemStack harm = PotionContents.createItemStack(
					Items.TIPPED_ARROW,
					HARM_LEVEL >= 1 ? Potions.STRONG_HARMING : Potions.HARMING);
			return harm;
		}
		if (BLIND.equals(effect)) {
			return effectArrow(MobEffects.BLINDNESS, BLINDNESS_SECONDS, "random_blind");
		}
		if (POISON.equals(effect)) {
			return effectArrow(MobEffects.POISON, POISON_SECONDS, "random_poison");
		}
		if (HUNGER.equals(effect)) {
			return effectArrow(MobEffects.HUNGER, HUNGER_SECONDS, "random_hunger");
		}
		if (SLOW.equals(effect)) {
			return effectArrow(MobEffects.SLOWNESS, SLOWNESS_SECONDS, "random_slow");
		}
		if (WEAK.equals(effect)) {
			return effectArrow(MobEffects.WEAKNESS, WEAKNESS_SECONDS, "random_weak");
		}
		if (WITHER.equals(effect)) {
			return effectArrow(MobEffects.WITHER, WITHER_SECONDS, "random_wither");
		}
		if (NAUSEA.equals(effect)) {
			return effectArrow(MobEffects.NAUSEA, NAUSEA_SECONDS, "random_nausea");
		}
		return effectArrow(MobEffects.LEVITATION, LEVITATION_SECONDS, "random_levitate");
	}

	private static ItemStack effectArrow(Holder<MobEffect> effect, int durationSeconds, String name) {
		return effectArrow(
				new MobEffectInstance(effect, seconds(durationSeconds)),
				effect.value().getColor(),
				name);
	}

	private static ItemStack effectArrow(MobEffectInstance effect, int color, String name) {
		return namedArrow(name, color, List.of(effect));
	}

	private static ItemStack namedArrow(String name, int color, List<MobEffectInstance> effects) {
		ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
		arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(color),
				effects,
				Optional.of(name)));
		// 1 = the duration of the effect is already in ticks, the game does not shorten it.
		arrow.set(DataComponents.POTION_DURATION_SCALE, 1.0F);
		return arrow;
	}

	private static int seconds(int value) {
		return Math.max(1, value) * 20;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
