package com.example.piglin;

import com.example.EvolvingThreats;
import com.example.entity.ai.HellBerserkDigGoal;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A piglin brute's brain and fight, with his own body and one netherite axe.
 * Spawns in the Nether in place of some hostile mobs. Chance does not grow with the day.
 * Picture and walk: client/piglin/HellBerserkModel.
 */
public class HellBerserk extends PiglinBrute {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Chance (0.0–1.0) that a natural Nether monster is this berserk instead.
	 * 0.02 = 2%, about one in fifty. FIXED: the same from the first day, the threat scale does not raise it.
	 * Rolled after the floating netherite tool (5%). If the spot is too low for him, the original mob stays.
	 * /etberserk ignores this chance. 0 = he never appears on his own.
	 */
	public static final float NATURAL_SPAWN_CHANCE = 0.02f;

	/** Health. 80 = 40 hearts. A normal brute in this mod has 60. */
	public static final double MAX_HEALTH = 80.0;

	/**
	 * How hard the body hits before the axe. A brute is 7.
	 * The netherite axe in his main hand adds its own damage on top.
	 */
	public static final double ATTACK_DAMAGE = 7.0;

	/** Walk speed before rage. 0.35 is a piglin brute. */
	public static final double MOVE_SPEED = 0.35;

	/** How far he notices a player, in blocks. 24 is across a bastion hall. A brute is 12. */
	public static final double FOLLOW_RANGE = 24.0;

	/**
	 * Knockback resistance before rage. 0.0 = a hit shoves him like a zombie.
	 * 0.5 = a hit moves him half as far. 1.0 = he does not move.
	 */
	public static final double KNOCKBACK_RESISTANCE = 0.5;

	/**
	 * Rage starts when health is at or below this fraction of the maximum.
	 * 0.5 = half, 40 health out of 80. He stays enraged after that, even if healed.
	 */
	public static final float RAGE_HEALTH_FRACTION = 0.5f;

	/**
	 * Speed effect during rage. 0 = Speed I, 1 = Speed II (+40% speed).
	 * The particles show, so the rage is visible. Higher is faster.
	 */
	public static final int RAGE_SPEED_AMPLIFIER = 1;

	/**
	 * Knockback resistance during rage. 1.0 = hits do not shove him.
	 * 0.5 = the same as before rage.
	 */
	public static final double RAGE_KNOCKBACK_RESISTANCE = 1.0;

	/**
	 * How fast he breaks a block in rage, compared with a player holding the same axe.
	 * 0.45 = a bit under half a player's speed. Zombies use 0.7.
	 * Netherack goes quickly. Stone is slow: an axe is the wrong tool for it.
	 * 1.0 = as fast as the player. He does not break blocks before rage.
	 */
	public static final float DIG_SPEED_MULTIPLIER = 0.45f;

	/**
	 * Experience for the kill. 60.
	 * The mod's double for other monsters does not add on top, so this stays 60.
	 * 0 = no experience.
	 */
	public static final int EXPERIENCE_REWARD = 60;

	/**
	 * Chance (0.0–1.0) to drop one wither skeleton skull when a player lands the kill.
	 * 0.025 = 2.5%, the same as a wither skeleton with no Looting.
	 * A death that is not a player's kill drops no skull. 0 = never. 1 = always.
	 */
	public static final float SKULL_DROP_CHANCE = 0.025f;

	/**
	 * Extra skull chance per Looting level on the killing blow.
	 * 0.01 = 1%: Looting I is 3.5%, II is 4.5%, III is 5.5%. Same as a wither skeleton.
	 * 0 = Looting does not help.
	 */
	public static final float SKULL_LOOTING_BONUS = 0.01f;

	// ========================================================================

	public static EntityType<HellBerserk> TYPE;

	private boolean raging;
	/** True while he is breaking a block. The brute brain stays paused so he does not walk off. */
	private boolean breakingBlock;

	public HellBerserk(EntityType<? extends HellBerserk> type, Level level) {
		super(type, level);
		this.xpReward = EXPERIENCE_REWARD;
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EvolvingThreats.id("hell_berserk"));
		TYPE = Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.of(HellBerserk::new, MobCategory.MONSTER)
						.sized(0.6f, 1.95f)
						.eyeHeight(1.79f)
						.clientTrackingRange(8)
						.fireImmune()
						.notInPeaceful()
						.noLootTable()
						.build(key)
		);
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PiglinBrute.createAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
				.add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
				.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
				.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
	}

	/** True once he has dropped to half health. Stays true. */
	public boolean isRaging() {
		return this.raging;
	}

	/** Dig goal. True = do not run the brute brain this tick. */
	public void setBreakingBlock(boolean breaking) {
		this.breakingBlock = breaking;
	}

	/**
	 * Room for a body 1.95 blocks tall. Feet on {@code feet}, solid ground under them, nothing in the body.
	 */
	public static boolean hasRoom(ServerLevel level, BlockPos feet) {
		if (TYPE == null) {
			return false;
		}
		BlockPos below = feet.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		return level.noCollision(TYPE.getSpawnAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
	}

	/** Test command. Null when the entity cannot be created. */
	public static @Nullable HellBerserk spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (TYPE == null) {
			return null;
		}
		HellBerserk berserk = TYPE.create(level, EntitySpawnReason.COMMAND);
		if (berserk == null) {
			return null;
		}
		berserk.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		berserk.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(BlockPos.containing(pos)),
				EntitySpawnReason.COMMAND,
				null
		);
		if (lookAt != null) {
			berserk.setTarget(lookAt);
			berserk.getLookControl().setLookAt(lookAt, 30.0f, 30.0f);
		}
		level.addFreshEntity(berserk);
		return berserk;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Chase, look, and the hit are the piglin brute brain. This only stops him to break a wall in rage.
		this.goalSelector.addGoal(1, new HellBerserkDigGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.breakingBlock) {
			// The brute brain would path around the wall and walk him off the block he is breaking.
			return;
		}
		super.customServerAiStep(level);
	}

	@Override
	public SpawnGroupData finalizeSpawn(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		// PiglinBruteHealth writes 60 onto every brute. This mob keeps his own 80.
		AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(MAX_HEALTH);
		}
		this.setHealth(this.getMaxHealth());
		this.setCanPickUpLoot(false);
		this.setLeftHanded(false);
		this.setCustomName(null);
		this.setCustomNameVisible(false);
		this.equipAxe();
		return data;
	}

	/** One netherite axe in the main hand. It does not drop. The off hand stays empty. */
	private void equipAxe() {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
		this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		this.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
	}

	@Override
	public void aiStep() {
		if (!this.level().isClientSide() && this.isAlive()) {
			this.updateRage();
		}
		super.aiStep();
	}

	private void updateRage() {
		if (!this.raging && this.getHealth() <= this.getMaxHealth() * RAGE_HEALTH_FRACTION) {
			this.raging = true;
			AttributeInstance knockback = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (knockback != null) {
				knockback.setBaseValue(RAGE_KNOCKBACK_RESISTANCE);
			}
			this.playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 1.0f, 0.8f);
		}
		if (this.raging) {
			// 40 ticks = 2 seconds, refreshed every tick, so the Speed effect does not blink off.
			this.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, RAGE_SPEED_AMPLIFIER, false, true));
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (!killedByPlayer || SKULL_DROP_CHANCE <= 0.0f) {
			return;
		}
		if (this.getRandom().nextFloat() < this.skullChance(source)) {
			this.spawnAtLocation(level, new ItemStack(Items.WITHER_SKELETON_SKULL));
		}
	}

	/** 2.5%, plus 1% for each Looting level. Same steps as a wither skeleton skull. */
	private float skullChance(DamageSource source) {
		float chance = SKULL_DROP_CHANCE;
		if (!(source.getEntity() instanceof LivingEntity killer) || !(killer.level() instanceof ServerLevel server)) {
			return chance;
		}
		var looting = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
		int levels = Math.max(
				EnchantmentHelper.getItemEnchantmentLevel(looting, killer.getMainHandItem()),
				EnchantmentHelper.getItemEnchantmentLevel(looting, killer.getOffhandItem()));
		if (levels <= 0 || SKULL_LOOTING_BONUS <= 0.0f) {
			return chance;
		}
		return Math.min(1.0f, chance + levels * SKULL_LOOTING_BONUS);
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PIGLIN_BRUTE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PIGLIN_BRUTE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PIGLIN_BRUTE_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.PIGLIN_BRUTE_STEP, 0.15f, 1.0f);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("HellBerserkRage", this.raging);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.raging = input.getBooleanOr("HellBerserkRage", false);
		if (this.raging) {
			AttributeInstance knockback = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (knockback != null) {
				knockback.setBaseValue(RAGE_KNOCKBACK_RESISTANCE);
			}
		}
	}
}
