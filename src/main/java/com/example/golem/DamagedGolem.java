package com.example.golem;

import com.example.EvolvingThreats;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 An iron golem that is immediately hostile to the player.
 Looks cracked and black eyes, but healthy.
 * Eyes are drawn in texture {@code textures/entity/damaged golem.png}.
 Himself appears at night in the ordinary world, chance - {@link #NATURAL SPAWN CHANCE}
 */
public class DamagedGolem extends IronGolem implements Enemy {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 Chance (0.0-1.0) that instead of the usual night monster will appear this golem.
	 0.002 = 0.2%, one in five hundred. FIXED: From day one, the threat scale does not increase.
	 A shot shared with a hovering instrument: first the instrument's chance (2%), then this one.
	 If in this place the golem does not get up (low ceiling), there will be an ordinary mob, the attempt does not disappear.
	 Ordinary world, neither hell nor end. The game does not put more than 24 blocks.
	 Team /etgolem does not throw this chance. 0 = does not appear in the world.
	 */
	public static final float NATURAL_SPAWN_CHANCE = 0.002f;

	/**
	 Health. 100 = 50 hearts, like an iron golem.
	 Cracks do not depend on this number: it looks damaged even in full health.
	 */
	public static final double MAX_HEALTH = 100.0;

	/**
	 Strength of impact, like a normal golem. The stroke itself still throws a random number.
	 * Therefore, the player arrives from about half of this value to a little more.
	 */
	public static final double ATTACK_DAMAGE = 15.0;

	/*From what distance (in blocks) the player notices. 24 - it's coming through the yard. */
	public static final double FOLLOW_RANGE = 24.0;

	/**
	 * How cracked in appearance. It doesn't affect your health.
	 3 = strong, as if almost collapsed. 2 = average. 1 = a little bit. 0 = whole.
	 */
	public static final int CRACK_LEVEL = 3;

	/**
	 * Experience to the total multiplier. 10 is more than a naked zombie.
	 * Then {@link com.example.MobExperience} doubles: 20 falls off the golem.
	 The multiplier is turned off or costs 1 – this number will fall.
	 */
	public static final int EXPERIENCE_REWARD = 10;

	// ========================================================================

	public static EntityType<DamagedGolem> TYPE;

	public DamagedGolem(EntityType<? extends DamagedGolem> type, Level level) {
		super(type, level);
		this.xpReward = EXPERIENCE_REWARD;
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EvolvingThreats.id("damaged_golem"));
		TYPE = Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.of(DamagedGolem::new, MobCategory.MONSTER)
						.sized(1.4f, 2.7f)
						.eyeHeight(2.5f)
						.clientTrackingRange(10)
						.notInPeaceful()
						.noLootTable()
						.build(key)
		);
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return IronGolem.createAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
				.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
	}

	/**
	 * Whether Golem height 2.7 blocks. Legs on {@code feet}, underfoot hard block, body doesn't rest on anything.
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

	/** Team call. There's no chance of a night spawn. It goes straight to the target, if there is one. */
	public static @Nullable DamagedGolem spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		DamagedGolem golem = TYPE.create(level, EntitySpawnReason.COMMAND);
		if (golem == null) {
			return null;
		}
		golem.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		golem.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(BlockPos.containing(pos)),
				EntitySpawnReason.COMMAND,
				null
		);
		if (lookAt != null) {
			golem.setTarget(lookAt);
			golem.getLookControl().setLookAt(lookAt, 30.0f, 30.0f);
		}
		level.addFreshEntity(golem);
		return golem;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 32.0f));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/**
	 Zombies, skeletons, spiders and robbers look for the iron golem and go to beat it.
	 This one is also iron, so without a refusal they would stare at it.
	 He's not a target here. The usual village golem still makes them angry.
	 * The player injured by the goal does not cease to beat.
	 */
	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	/** Always looks damaged. The health strip is full until it is beaten. */
	@Override
	public Crackiness.Level getCrackiness() {
		return switch (CRACK_LEVEL) {
			case 1 -> Crackiness.Level.LOW;
			case 2 -> Crackiness.Level.MEDIUM;
			case 3 -> Crackiness.Level.HIGH;
			default -> Crackiness.Level.NONE;
		};
	}

	/*An iron bar does not repair him: he is not a village guard, but an enemy. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
			ServerLevelAccessor level,
			net.minecraft.world.DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData
	) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
		this.setHealth(this.getMaxHealth());
		return data;
	}
}
