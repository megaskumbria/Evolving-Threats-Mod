package com.example.witch;

import com.example.advancement.ModAdvancements;
import com.example.entity.NullifierWitchEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 Rare witch. From time to time throws a white explosive potion.
 Getting into the spray removes all the effects from the player at once: both useful and harmful.
 Between such throws she throws the usual witch potions (poison, slowdown, damage).
 Allies in the raid still throws treatment - zeroing only for the player.
 */
public final class WitchNullifier {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - such witches do not appear, and the white potion does not remove anything. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) that an ordinary witch will appear to be null. 0.15 = 15%.
	 There are fewer witches in the world than the Blazes, so the chance is higher than that of the Blaze Alchemist (4%).
	 Hut, raid and spawner throw this chance. Spawn team doesn't take chances.
	 */
	public static final float SPAWN_CHANCE = 0.15f;

	/**
	 Pause between white potions, seconds.
	 * 15 = next zero no earlier than 15 seconds.
	 The average witch throws about once every 3 seconds, so that white will be about every fifth throw.
	 * 0 = each throw on the player removes the effects.
	 The first shot on the player does not wait for a pause.
	 */
	public static final int THROW_COOLDOWN_SECONDS = 15;

	/**
	 * Splash color, RGB. 0xF4F4F4 is almost white to distinguish from purple poison and black damage.
	 You can put any color, it does not affect the removal of effects.
	 */
	public static final int POTION_COLOR = 0xF4F4F4;

	/**
	 How far horizontally do the sprays reach in the blocks. 4 - like an ordinary explosive potion.
	 The cloud of particles is drawn by the game itself and there are always about 4 blocks. More than 4 - the effect will be removed where the spray is no longer visible.
	 */
	public static final double SPLASH_RADIUS_BLOCKS = 4.0;

	/**
	 How much splashes reach up and down, in blocks. 2 - like a regular potion.
	 */
	public static final double SPLASH_VERTICAL_BLOCKS = 2.0;

	// ========================================================================

	/**
	 A label inside the potion to distinguish our throw from the usual.
	 In the game, this line is not shown: the witch does not hold a bottle in his hand, the projectile is white due to POTION COLOR.
	 */
	private static final String POTION_MARKER = "evolvingthreats_nullify";

	private WitchNullifier() {
	}

	public static boolean isNullifier(Witch witch) {
		return ENABLED
				&& witch instanceof NullifierWitchEntity data
				&& data.evolvingthreats$isNullifier();
	}

	/** The end of the witch's tick. Reduces the pause to the next white potion. The client doesn't count. */
	public static void tickCooldown(Witch witch) {
		if (witch.level().isClientSide() || !(witch instanceof NullifierWitchEntity data) || !data.evolvingthreats$isNullifier()) {
			return;
		}
		int cooldown = data.evolvingthreats$getNullifyCooldown();
		if (cooldown > 0) {
			data.evolvingthreats$setNullifyCooldown(cooldown - 1);
		}
	}

	/**
	 * Throw start. If the pause has passed and the goal is the player, this throw will become a white potion.
	 While the witch drinks the potion herself, she throws nothing - the flag is not put.
	 Treatment of an ally in a raid does not fall here: the target is not a player.
	 */
	public static void armThrow(Witch witch, LivingEntity target) {
		if (!(witch instanceof NullifierWitchEntity data)) {
			return;
		}
		data.evolvingthreats$setNullifyArmed(false);
		if (!isNullifier(witch) || witch.level().isClientSide() || witch.isDrinkingPotion()) {
			return;
		}
		if (!(target instanceof Player) || data.evolvingthreats$getNullifyCooldown() > 0) {
			return;
		}
		data.evolvingthreats$setNullifyArmed(true);
	}

	/**
	 The witch just created an explosive potion. Replace it with white and start a pause.
	 Call out the projectile designer while the throw is still "armed".
	 */
	public static boolean consumeArmed(LivingEntity owner) {
		if (!(owner instanceof Witch witch) || !(witch instanceof NullifierWitchEntity data)) {
			return false;
		}
		if (!data.evolvingthreats$isNullifyArmed()) {
			return false;
		}
		data.evolvingthreats$setNullifyArmed(false);
		data.evolvingthreats$setNullifyCooldown(Math.max(0, THROW_COOLDOWN_SECONDS) * 20);
		return true;
	}

	/** White explosive potion without its effects. Removal makes a hit, not the contents of the bottle. */
	public static ItemStack potionStack() {
		ItemStack stack = new ItemStack(Items.SPLASH_POTION);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(POTION_COLOR),
				List.of(),
				Optional.of(POTION_MARKER)));
		return stack;
	}

	/**
	 Explosive potion hit. Ordinary potions come and go here as well.
	 White removes all the icons of effects from players in the spray: potion, apple, lighthouse.
	 The poison and drying also subside. Shield explosive potion does not catch - as in a normal game.
	 Other mobs do not touch: zombies retain their effects next to them.
	 */
	public static void onSplash(AbstractThrownPotion potion, HitResult hit) {
		if (!(potion.level() instanceof ServerLevel level) || !ENABLED || !isNullifyPotion(potion.getItem())) {
			return;
		}
		double radius = Math.max(0.0, SPLASH_RADIUS_BLOCKS);
		AABB splashBox = potion.getBoundingBox().move(hit.getLocation().subtract(potion.position()));
		AABB search = splashBox.inflate(radius, Math.max(0.0, SPLASH_VERTICAL_BLOCKS), radius);
		float margin = ProjectileUtil.computeMargin(potion);
		double radiusSq = radius * radius;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, search)) {
			if (!(entity instanceof Player) || !entity.isAffectedByPotions()) {
				continue;
			}
			double dist = splashBox.distanceToSqr(entity.getBoundingBox().inflate(margin));
			if (dist >= radiusSq) {
				continue;
			}
			if (!entity.removeAllEffects()) {
				continue;
			}
			if (entity instanceof ServerPlayer player) {
				ModAdvancements.onEffectsWiped(player);
			}
			level.playSound(
					null,
					entity.getX(),
					entity.getY(),
					entity.getZ(),
					SoundEvents.WITCH_DRINK,
					SoundSource.PLAYERS,
					0.6f,
					1.4f);
		}
	}

	/** Call from finalizespace. The team spawn puts a flag in advance and doesn't throw up a chance. */
	public static void rollOnSpawn(Witch witch, EntitySpawnReason reason) {
		if (!ENABLED || !(witch instanceof NullifierWitchEntity data)) {
			return;
		}
		if (data.evolvingthreats$isNullifier() || reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (witch.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setNullifier(true);
	}

	/** Team: Witch zero in front of the player. null - the mechanics are off. */
	public static @Nullable Witch spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Witch witch = EntityTypes.WITCH.create(level, EntitySpawnReason.COMMAND);
		if (witch == null) {
			throw new IllegalStateException("Failed to create nullifier witch");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		witch.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (witch instanceof NullifierWitchEntity data) {
			data.evolvingthreats$setNullifier(true);
		}
		witch.finalizeSpawn(level, level.getCurrentDifficultyAt(witch.blockPosition()), EntitySpawnReason.COMMAND, null);
		if (witch instanceof NullifierWitchEntity data) {
			data.evolvingthreats$setNullifier(true);
			data.evolvingthreats$setNullifyCooldown(0);
			data.evolvingthreats$setNullifyArmed(false);
		}
		if (lookAt != null) {
			witch.setTarget(lookAt);
		}
		level.addFreshEntity(witch);
		return witch;
	}

	private static boolean isNullifyPotion(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && POTION_MARKER.equals(contents.customName().orElse(""));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
