package com.example.tool;

import com.example.EvolvingThreats;
import com.example.MobEnchantments;
import com.example.MobPotionBuffs;
import com.example.RandomWeapons;
import com.example.entity.ai.FloatingToolAttackGoal;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hostile hovering tool: sword, axe, shovel, pickaxe or hoe.
 He flies to the player and hits close. In the ordinary world, there is no Neserite - the set is the same as that of zombies ({@link RandomWeapons}).
 In hell, the instrument is always unseritic and with Fire Conspiracy III, the object itself does not fall out.
 The charm in the ordinary world is the same as on zombie weapons ({@link MobEnchantments}): sharpness and fiery aspect beat,
 Efficiency and luck only adorn the fallen object.
 The potions are the same as the zombies (@link MobPotionBuffs): zero to five while the mob is alive.
 There is no invisibility: she would hide the object itself, this mob has no body.
 *
 There is no separate model and animation: the usual model of the subject is drawn on the screen
 * (see client renderer). It's just the body, the flight and the punch.
 In the ordinary world at night appears himself, in place of the usual hostile mob.
 * Chance - {@link #NATURAL SPAWN CHANCE} In hell - {@link #NETHER SPAWN CHANCE}, anytime.
 * The team {@code /ettool} does not give a chance: in hell it also gives nezerite.
 */
public class FloatingTool extends Monster {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 Chance (0.0-1.0) that instead of the usual night monster will appear this tool.
	 * 0.02 = 2%. Total throw with a damaged golem: first this chance, then the chance of a golem (0.2%).
	 0.02 and 0.002 together = 2.2% of special mobs, they do not occupy the same appearance.
	 Ordinary world, neither hell nor end. The game does not put closer than 24 blocks, like any monster.
	 Team /ettool does not throw this chance. 0 = does not appear in the world.
	 */
	public static final float NATURAL_SPAWN_CHANCE = 0.02f;

	/**
	 Chance (0.0-1.0) that instead of a hostile mob in hell will appear nonseritic tool.
	 * 0.05 = 5%. FIXED: From day one, the threat scale does not increase.
	 Hell does not wait for the night: monsters appear there during the day. 0 = there are none in hell.
	 The ordinary world does not take this opportunity.
	 */
	public static final float NETHER_SPAWN_CHANCE = 0.05f;

	/**
	 A conspiracy of fire on a hellish instrument. 3 = III. The Enchantment Table has Ceiling II.
	 Each level keeps the player on fire longer. 2 = Ordinary Fire Conspiracy II. 0 = no fire.
	 */
	public static final int NETHER_FIRE_ASPECT_LEVEL = 3;

	/**
	 The chance of a hellish instrument falling out. 0 = never falls, mining does not change:
	 * The game only increases the profit if the chance is no longer zero.
	 1 = always falling. The ordinary world is looking at {@link #DROP CHANCE}, not this number.
	 */
	public static final float NETHER_DROP_CHANCE = 0.0f;

	/**
	 Invisible body to be hit (width and height in blocks)
	 The tool itself is drawn separately and can be larger than this box.
	 0.75 is like a block, it is possible to get on it. Less is harder.
	 */
	public static final float HITBOX_WIDTH = 0.75f;
	public static final float HITBOX_HEIGHT = 0.75f;

	/**
	 How large is the object drawn? 1.0 = as a thing thrown to the ground.
	 * 1.6 = visible hovering instrument. Hitbox and damage don't matter.
	 */
	public static final float VISUAL_SCALE = 1.6f;

	/**
	 Where the picture looks while the instrument attacks, in degrees.
	 * 0 = where the mob itself is turned (on the player). It does not rotate around its axis at this moment.
	 90 or 180 – if the blade looks sideways or backwards
	 When no one is attacked, the object spins again like an abandoned thing.
	 */
	public static final float ATTACK_FACING_OFFSET = 0.0f;

	/**
	 * "Fist" mob, in the hearts of damage is half the number (1.0 = half the heart).
	 The damage to the tool itself is added from above: the wooden sword is weaker than the diamond one.
	 The hoe is weaker than the sword. Don't put 5-10 here, or even the hoe will beat like a sword.
	 */
	public static final double BASE_ATTACK_DAMAGE = 1.0;

	/**
	 Health by material (2.0 = 1 heart).
	 Wood and gold are weak, diamonds live longer.
	 */
	public static final double HEALTH_WOOD_GOLD = 12.0;
	public static final double HEALTH_STONE_COPPER = 16.0;
	public static final double HEALTH_IRON = 20.0;
	public static final double HEALTH_DIAMOND = 28.0;

	/*Neserite in hell. 36 = 18 hearts. Diamond tool – 28 (14 hearts). */
	public static final double HEALTH_NETHERITE = 36.0;

	/**
	 How fast it flies when it doesn’t catch up. More is faster.
	 * Together with {@link #CHASE SPEED}: 0.9 and 1.2 - catches up with the running player, but does not teleport.
	 */
	public static final double FLYING_SPEED = 0.9;

	/*The speed multiplier is in pursuit of the player. 1.0 = like a normal flight, 1.6 = noticeably meaner. */
	public static final double CHASE_SPEED = 1.2;

	/*From what distance (in blocks) the player notices. 24 = three chunks horizontally not necessary, but through the yard sees. */
	public static final double FOLLOW_RANGE = 24.0;

	/**
	 Pause between punches, ticks. 20 = once per second until the instrument is close.
	 40 = once every two seconds, it is easier to hit.
	 */
	public static final int ATTACK_COOLDOWN_TICKS = 20;

	/**
	 * Chance (0.0-1.0) that the instrument will be charmed. 0.30 = 30%, like a zombie weapon.
	 * What charms and what level - in {@link MobEnchantments} 0 = always without charm.
	 */
	public static final float ENCHANT_CHANCE = 0.30f;

	/**
	 Chance (0.0–1.0) that the instrument itself will fall out after death, along with the spell.
	 * 0.15 = 15%. 0 = nothing falls. 1 = falls always.
	 */
	public static final float DROP_CHANCE = 0.15f;

	/**
	 * Experience to the total multiplier. 5 - like a regular monster.
	 * Then {@link com.example.MobExperience} doubles: 10 drops off the mob.
	 The multiplier is turned off or costs 1 – this number will fall.
	 */
	public static final int EXPERIENCE_REWARD = 5;

	/*How often do particles of enchantment fall in tics? 5 = four times per second. More is less. */
	public static final int PARTICLE_INTERVAL_TICKS = 5;

	/** How many particles at a time. 2 is a light glow. 8 is already a cloud. */
	public static final int PARTICLE_COUNT = 2;

	/**
	 The volume of the quiet sound of the enchanting table. 0.35 = quiet. 0 = turn off that sound.
	 The sound of impact and death does not depend on this number.
	 */
	public static final float AMBIENT_VOLUME = 0.35f;

	// ========================================================================

	public static EntityType<FloatingTool> TYPE;

	/**
	 * True as long as there's a target. The client stops rotating the image on this flag.
	 The target itself does not come to the client, so the flag is sent separately.
	 */
	private static final EntityDataAccessor<Boolean> DATA_ATTACKING = SynchedEntityData.defineId(
			FloatingTool.class, EntityDataSerializers.BOOLEAN);

	public FloatingTool(EntityType<? extends FloatingTool> type, Level level) {
		super(type, level);
		this.xpReward = EXPERIENCE_REWARD;
		// HoversInPlace = True: When a target is lost, it does not fall to the ground.
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EvolvingThreats.id("floating_tool"));
		TYPE = Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.of(FloatingTool::new, MobCategory.MONSTER)
						.sized(HITBOX_WIDTH, HITBOX_HEIGHT)
						.eyeHeight(HITBOX_HEIGHT * 0.5f)
						.clientTrackingRange(10)
						.notInPeaceful()
						.build(key)
		);
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, HEALTH_WOOD_GOLD)
				.add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, FLYING_SPEED)
				.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
	}

	/**
	 * Team call. Appears in the air and immediately looks at the target if it is transmitted.
	 * Subject, charms and potions are selected inside {@link #finalizeSpawn}.
	 */
	public static @Nullable FloatingTool spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		FloatingTool tool = TYPE.create(level, EntitySpawnReason.COMMAND);
		if (tool == null) {
			return null;
		}
		tool.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		tool.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(BlockPos.containing(pos)),
				EntitySpawnReason.COMMAND,
				null
		);
		if (lookAt != null) {
			tool.setTarget(lookAt);
			tool.getLookControl().setLookAt(lookAt, 30.0f, 30.0f);
			// So that the picture does not spin in the first moment after /ettool.
			tool.entityData.set(DATA_ATTACKING, true);
		}
		level.addFreshEntity(tool);
		return tool;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ATTACKING, false);
	}

	/*True - chases the target, the picture does not rotate around its axis. */
	public boolean isAttacking() {
		return this.entityData.get(DATA_ATTACKING);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		boolean attacking = this.getTarget() != null;
		if (this.isAttacking() != attacking) {
			this.entityData.set(DATA_ATTACKING, attacking);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(2, new FloatingToolAttackGoal(this));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		navigation.setRequiredPathLength(48.0f);
		return navigation;
	}

	@Override
	public void aiStep() {
		// Without gravity, it hangs in the air even when it's not flying anywhere.
		this.setNoGravity(true);
		super.aiStep();
		repairTool();
		// Their particles of enchantment. They are invisible, otherwise the potion does not hide anything.
		if (this.level() instanceof ServerLevel server
				&& !this.isInvisible()
				&& this.tickCount % PARTICLE_INTERVAL_TICKS == 0) {
			server.sendParticles(
					ParticleTypes.ENCHANT,
					this.getX(),
					this.getY() + this.getBbHeight() * 0.5,
					this.getZ(),
					PARTICLE_COUNT,
					0.25,
					0.3,
					0.25,
					0.8
			);
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData
	) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
		if (level instanceof ServerLevel server && server.dimension() == Level.NETHER) {
			equipNetherTool(server);
		} else {
			// Same random sword/axe/shovel/brick/hoe as zombies. Neserite's not on the list.
			RandomWeapons.equipMeleeOnly(this);
			MobEnchantments.rollHeldWeapons(this, ENCHANT_CHANCE);
			this.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);
		}
		applyHealthForTool();
		// Same potions as zombies and skeletons: how many and what - in MobPotionBuffs.
		// Invisibility is not given to this mobu: see check in tryApply.
		MobPotionBuffs.tryApply(this);
		return data;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// Copy before impact: if the strength ends right in the impact, the slot will empty and the mob will “disappear”.
		ItemStack kept = this.getMainHandItem().copy();
		boolean hit = super.doHurtTarget(level, target);
		ItemStack hand = this.getMainHandItem();
		if (hand.isEmpty()) {
			if (!kept.isEmpty()) {
				kept.setDamageValue(0);
				this.setItemSlot(EquipmentSlot.MAINHAND, kept);
			}
		} else {
			hand.setDamageValue(0);
		}
		return hit;
	}

	/*The tool in combat does not waste strength, otherwise in a couple of minutes the mob will remain without a picture. */
	private void repairTool() {
		ItemStack tool = this.getMainHandItem();
		if (!tool.isEmpty() && tool.isDamaged()) {
			tool.setDamageValue(0);
		}
	}

	/**
	 Hell: nonseritic sword, axe, shovel, pickaxe or hoe, equally.
	 Conspiracy of Fire III. Odds of 0 - Mining adds nothing to zero.
	 */
	private void equipNetherTool(ServerLevel level) {
		Item[] tools = {
				Items.NETHERITE_SWORD,
				Items.NETHERITE_AXE,
				Items.NETHERITE_SHOVEL,
				Items.NETHERITE_PICKAXE,
				Items.NETHERITE_HOE,
		};
		ItemStack stack = new ItemStack(tools[this.random.nextInt(tools.length)]);
		if (NETHER_FIRE_ASPECT_LEVEL > 0) {
			Holder<Enchantment> fire = level.registryAccess()
					.lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(Enchantments.FIRE_ASPECT);
			EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(fire, NETHER_FIRE_ASPECT_LEVEL));
		}
		this.setItemSlot(EquipmentSlot.MAINHAND, stack);
		this.setDropChance(EquipmentSlot.MAINHAND, NETHER_DROP_CHANCE);
	}

	/**
	 Health by subject name: diamond sword, iron pickaxe, etc.
	 Strange material (if you add it to RandomWeapons and forget it here) gets the health of the tree.
	 */
	private void applyHealthForTool() {
		String name = BuiltInRegistries.ITEM.getKey(this.getMainHandItem().getItem()).getPath();
		double health = HEALTH_WOOD_GOLD;
		if (name.startsWith("netherite_")) {
			health = HEALTH_NETHERITE;
		} else if (name.startsWith("diamond_")) {
			health = HEALTH_DIAMOND;
		} else if (name.startsWith("iron_")) {
			health = HEALTH_IRON;
		} else if (name.startsWith("stone_") || name.startsWith("copper_")) {
			health = HEALTH_STONE_COPPER;
		}
		var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(health);
		}
		this.setHealth((float) health);
	}

	@Override
	protected boolean omnidirectionalAirMover() {
		return true;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
		// Flying, no fall damage needed.
	}

	@Override
	public void playAmbientSound() {
		if (AMBIENT_VOLUME <= 0.0f) {
			return;
		}
		this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, AMBIENT_VOLUME, 1.15f + this.random.nextFloat() * 0.2f);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 140;
	}

	@Override
	protected void playAttackSound() {
		this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.1f + this.random.nextFloat() * 0.3f);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ARMOR_EQUIP_IRON.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ITEM_BREAK.value();
	}

	@Override
	protected float getSoundVolume() {
		return 0.6f;
	}
}
