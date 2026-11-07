package com.example.skeleton;

import com.example.threat.ThreatScale;

import com.example.MobEnchantments;
import com.example.RandomArmor;
import com.example.advancement.ModAdvancements;
import com.example.entity.TacticianSkeletonEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 700 IQ skeleton. A special type, not a mixture of the others.
 Shoots with the same arrow. Got him, pulls out his sword and runs.
 When the effects of the player subside, he takes the bow again.
 The sword that is not in the hand is visible on the back.
 */
public final class TacticianSkeleton {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - these skeletons do not appear. Those already living remain as they are. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0–1.0) that an ordinary skeleton or wanderer will be this type.
	 0.05 = 5% on day 100 (GROWING: 10 times less at the beginning of the game, see ThreatTable.DANGEROUS_START_RATIO).
	 Less than small and far: this one is stronger in battle.
	 * 0 = There will be no such skeletons.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	/**
	 Health, both edges coming in. 50 and 50 = always 50 lives (25 hearts).
	 In the usual skeleton in this mod by chance 20-40. That number is put after that throw.
	 Less than 1 game kills almost immediately. Already living skeletons retain their old health.
	 */
	public static final int HEALTH_MIN = 50;
	public static final int HEALTH_MAX = 50;

	/**
	 How much experience falls at death. 30 = exactly 30, not top to normal.
	 MobExperience.MULTIPLIER does not increase this number.
	 * 0 = no experience. A typical skeleton in this mod gives about 10, plus an extra for things.
	 */
	public static final int XP_REWARD = 30;

	/*Weakness on the player after hitting, in seconds. Level I. */
	public static final int WEAKNESS_SECONDS = 20;

	/*Slowdown after hit, in seconds. Level I. */
	public static final int SLOWNESS_SECONDS = 20;

	/*Blindness after hit, in seconds. In short: almost without review, but not for the whole fight. */
	public static final int BLINDNESS_SECONDS = 5;

	/**
	 Swords behind: wood, stone, gold, copper, iron, diamond. No Neserite.
	 The weights are the same as those of random mob weapons. Amount of 100: wood 30% diamond 10%.
	 */
	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD,
			Items.STONE_SWORD,
			Items.GOLDEN_SWORD,
			Items.COPPER_SWORD,
			Items.IRON_SWORD,
			Items.DIAMOND_SWORD,
	};

	private static final int[] SWORD_WEIGHTS = { 30, 25, 10, 10, 15, 10 };

	// ========================================================================

	/*Name on the arrow to distinguish it from any other arrow with a potion. */
	public static final String ARROW_NAME = "tactician";

	/**
	 * Item on the back slot. An ordinary skeleton is empty, this one has a bow or a sword.
	 * The slot number is engaged in {@code TacticianSkeletonDataMixin} immediately when the skeleton is loaded.
	 Do not transfer this field to the method that is called during the spawn: the number will match
	 * with the number of the skeleton itself, and the world will fall.
	 */
	public static final EntityDataAccessor<ItemStack> BACK_ITEM = SynchedEntityData.defineId(
			AbstractSkeleton.class, EntityDataSerializers.ITEM_STACK);

	private TacticianSkeleton() {
	}

	/**
	 * True - this skeleton became "700 IQ". Then he doesn't need a little stature, a totem.
	 Two weapons, a harmful arrow, shield, armor and a distant bow.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(skeleton instanceof TacticianSkeletonEntity data)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		// The team puts the mark before it appears. Then we do not give up the chance - immediately give out the bow and sword.
		if (data.evolvingthreats$isTactician()) {
			equip(skeleton, data);
			return true;
		}
		if (skeleton.getRandom().nextFloat() >= ThreatScale.dangerousChance(skeleton.level(), SPAWN_CHANCE)) {
			return false;
		}
		equip(skeleton, data);
		return true;
	}

	/**
	 Team: This skeleton is in front of the player, with no chance.
	 He looks at the one who called, and immediately with the bow. Sword on your back.
	 */
	public static void spawn(ServerLevel level, Vec3 pos, LivingEntity lookAt) {
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof TacticianSkeletonEntity data) {
			data.evolvingthreats$setTactician(true);
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

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}

	/*He hit a player with our arrow. The sword will reach the nearest tick, not in the middle of the shot. */
	public static void onArrowHit(AbstractSkeleton skeleton, Player player) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return;
		}
		if (!player.isAlive()) {
			return;
		}
		data.evolvingthreats$setMarkedPlayer(player.getUUID());
		data.evolvingthreats$setMeleeUntil(skeleton.level().getGameTime() + meleeTicks());
	}

	/*Each tick on the server: a sword, while the effects are on the player, otherwise a bow. */
	public static void serverTick(AbstractSkeleton skeleton) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return;
		}
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		Player player = markedPlayer(level, data);
		boolean chasing = player != null && level.getGameTime() < data.evolvingthreats$getMeleeUntil() && hasAnyDebuff(player);
		boolean holdingBow = skeleton.getMainHandItem().is(Items.BOW);
		if (chasing) {
			if (holdingBow) {
				swapToMelee(skeleton, data);
			}
			if (skeleton.getTarget() != player) {
				skeleton.setTarget(player);
			}
			if (player instanceof ServerPlayer serverPlayer) {
				ModAdvancements.onThreeEffects(serverPlayer);
			}
			return;
		}
		if (!holdingBow) {
			swapToRanged(skeleton, data);
		}
	}

	/**
	 True - the goal of melee is not to turn off, even if the player is around the corner.
	 Otherwise, after hitting the skeleton stops as soon as the path is broken.
	 */
	public static boolean isChasingMarked(LivingEntity mob) {
		if (!(mob instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return false;
		}
		if (mob.getMainHandItem().is(Items.BOW)) {
			return false;
		}
		UUID marked = data.evolvingthreats$getMarkedPlayer();
		LivingEntity target = mob instanceof AbstractSkeleton skeleton ? skeleton.getTarget() : null;
		return marked != null && target != null && target.isAlive() && marked.equals(target.getUUID());
	}

	/*• Release a special arrow instead of the usual one. False is not him, or the sword is in his hand. */
	public static boolean shoot(AbstractSkeleton skeleton, LivingEntity target, float power) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return false;
		}
		if (!skeleton.getMainHandItem().is(Items.BOW) || !(skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack projectile = arrow();
		ItemStack bow = skeleton.getMainHandItem();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(skeleton, projectile, power, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double xd = target.getX() - skeleton.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - skeleton.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		float uncertainty = ((RangedAttackMob) skeleton).rangedAttackUncertainty(level);
		Projectile.spawnProjectileUsingShoot(arrow, level, projectile, xd, yd + distance * 0.2, zd, 1.6F, uncertainty);
		skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		return true;
	}

	/*A sword or bow from the back falls at the same chance as armor. The object in the hand drops the game itself. */
	public static void dropBackItem(AbstractSkeleton skeleton, ServerLevel level) {
		if (!(skeleton instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return;
		}
		ItemStack back = data.evolvingthreats$getBackItem();
		if (back.isEmpty() || skeleton.getRandom().nextFloat() >= RandomArmor.ARMOR_DROP_CHANCE) {
			return;
		}
		skeleton.spawnAtLocation(level, back.copy());
	}

	private static void equip(AbstractSkeleton skeleton, TacticianSkeletonEntity data) {
		data.evolvingthreats$setTactician(true);
		skeleton.setCanPickUpLoot(false);
		skeleton.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

		// First, the sword in the hand, so that the spell of weapons hangs on him, not on the bow.
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pickSword(skeleton.getRandom())));
		MobEnchantments.rollForSkeleton(skeleton);
		data.evolvingthreats$setBackItem(skeleton.getMainHandItem().copy());

		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setDropChance(EquipmentSlot.MAINHAND, RandomArmor.ARMOR_DROP_CHANCE);
		RandomArmor.rollHelmet(skeleton);
		MobEnchantments.rollArmorForSkeleton(skeleton);
		rollHealth(skeleton);
	}

	private static void swapToMelee(AbstractSkeleton skeleton, TacticianSkeletonEntity data) {
		ItemStack bow = skeleton.getMainHandItem().copy();
		ItemStack sword = data.evolvingthreats$getBackItem().copy();
		if (sword.isEmpty()) {
			return;
		}
		data.evolvingthreats$setBackItem(bow);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, sword);
		skeleton.setDropChance(EquipmentSlot.MAINHAND, RandomArmor.ARMOR_DROP_CHANCE);
	}

	private static void swapToRanged(AbstractSkeleton skeleton, TacticianSkeletonEntity data) {
		ItemStack sword = skeleton.getMainHandItem().copy();
		ItemStack bow = data.evolvingthreats$getBackItem().copy();
		if (!bow.is(Items.BOW)) {
			bow = new ItemStack(Items.BOW);
		}
		data.evolvingthreats$setBackItem(sword);
		data.evolvingthreats$setMarkedPlayer(null);
		data.evolvingthreats$setMeleeUntil(0L);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
		skeleton.setDropChance(EquipmentSlot.MAINHAND, RandomArmor.ARMOR_DROP_CHANCE);
	}

	private static Player markedPlayer(ServerLevel level, TacticianSkeletonEntity data) {
		UUID id = data.evolvingthreats$getMarkedPlayer();
		if (id == null) {
			return null;
		}
		Player player = level.getPlayerByUUID(id);
		return player != null && player.isAlive() ? player : null;
	}

	/*As long as the player remains at least one of the three - a skeleton with a sword. Milk takes everything off. */
	private static boolean hasAnyDebuff(Player player) {
		return player.hasEffect(MobEffects.WEAKNESS)
				|| player.hasEffect(MobEffects.SLOWNESS)
				|| player.hasEffect(MobEffects.BLINDNESS);
	}

	/*How many tics to hold the sword if the effects have not been removed before? This is the longest of the three. */
	private static int meleeTicks() {
		int seconds = Math.max(WEAKNESS_SECONDS, Math.max(SLOWNESS_SECONDS, BLINDNESS_SECONDS));
		return Math.max(1, seconds) * 20;
	}

	private static ItemStack arrow() {
		List<MobEffectInstance> effects = List.of(
				new MobEffectInstance(MobEffects.WEAKNESS, Math.max(1, WEAKNESS_SECONDS) * 20),
				new MobEffectInstance(MobEffects.SLOWNESS, Math.max(1, SLOWNESS_SECONDS) * 20),
				new MobEffectInstance(MobEffects.BLINDNESS, Math.max(1, BLINDNESS_SECONDS) * 20));
		ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
		arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.empty(),
				effects,
				Optional.of(ARROW_NAME)));
		arrow.set(DataComponents.POTION_DURATION_SCALE, 1.0F);
		return arrow;
	}

	/*How much experience to give. -1 is not 700 IQ, let the game decide. */
	public static int experienceReward(Mob mob) {
		if (!(mob instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	private static void rollHealth(AbstractSkeleton skeleton) {
		int low = Math.min(HEALTH_MIN, HEALTH_MAX);
		int high = Math.max(HEALTH_MIN, HEALTH_MAX);
		int health = low + skeleton.getRandom().nextInt(high - low + 1);
		var maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		maxHealth.setBaseValue(health);
		skeleton.setHealth(health);
	}

	private static Item pickSword(RandomSource random) {
		int total = 0;
		for (int weight : SWORD_WEIGHTS) {
			if (weight > 0) {
				total += weight;
			}
		}
		if (total <= 0) {
			return Items.WOODEN_SWORD;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < SWORDS.length && i < SWORD_WEIGHTS.length; i++) {
			if (SWORD_WEIGHTS[i] <= 0) {
				continue;
			}
			roll -= SWORD_WEIGHTS[i];
			if (roll < 0) {
				return SWORDS[i];
			}
		}
		return SWORDS[0];
	}
}
