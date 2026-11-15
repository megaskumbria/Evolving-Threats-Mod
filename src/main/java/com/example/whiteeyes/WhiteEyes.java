package com.example.whiteeyes;

import com.example.EvolvingThreats;
import com.example.MobNames;
import com.example.threat.ThreatTable;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * White Eyes. A player-shaped hunter in iron armor.
 * Comes on scale night {@link ThreatTable#WHITE_EYES_DAY}, then every that many scale days.
 * Stays until killed. Sun, lava, and water do not remove him.
 * If the player leaves the area, dies, or changes dimension, he walks home and heals to full.
 */
public class WhiteEyes extends Zombie {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — he never arrives, and the test command cannot summon him. */
	public static final boolean ENABLED = true;

	/**
	 * false — he still digs and lights fires when the mobGriefing game rule is off.
	 * true — those two stop with the rule. The hunt, the teleport, and the sword stay.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// ================= DEBUG SWITCHES (temporary chase test) =================
	// Teleport, digging, the bow, and the upward throw are on for the whole fight.
	// Fire and the wither bottle start at 150 health. Burst and spiders stay off.
	// He still uses the zombie's attack goal, with the "keep following when you cannot see the player" switch on.

	/** true = teleport on again; false = never teleports. */
	public static final boolean DEBUG_TELEPORT = true;
	/** true = wither potion on again; false = no potion. */
	public static final boolean DEBUG_WITHER_POTION = false;
	/** true = cave spiders on again; false = no spiders, and old summoned spiders are removed. */
	public static final boolean DEBUG_SPIDERS = false;
	/** true = bow on again; false = holds the sword only. */
	public static final boolean DEBUG_BOW = true;
	/** true = heartbeat speed burst on again; false = normal walk speed only. */
	public static final boolean DEBUG_BURST = false;
	/** true = launching you upward on again; false = no throwing. */
	public static final boolean DEBUG_THROW = true;
	/** true = floor fire on again; false = no fire patches. */
	public static final boolean DEBUG_FIRE = false;
	/** true = Slowness/Weakness/Blindness/Hunger from the sword on again; false = damage only. */
	public static final boolean DEBUG_SWORD_EFFECTS = false;
	/** true = shield disabling on again; false = no special shield break. */
	public static final boolean DEBUG_SHIELD_BREAK = false;
	/** true = he digs walls again; false = he never stops to break a block. */
	public static final boolean DEBUG_DIG = true;
	/**
	 * true = when the path ends short of you, he walks straight ahead.
	 * false = he only follows a path, the same way a zombie does.
	 */
	public static final boolean DEBUG_WALK_STRAIGHT = false;

	/** Name above the head. Shown when the crosshair is on him, within the usual distance. */
	public static final String NAME = "White Eyes";

	/**
	 * Health. 600 = 300 hearts. The second phase is {@link #PHASE_HEALTH}, not half of this.
	 * A full Protection IV diamond set still takes about 1 heart from the sword.
	 */
	public static final float MAX_HEALTH = 600.0f;

	/** Health where the second phase starts. 150 = 75 hearts. The sword does not get stronger. */
	public static final float PHASE_HEALTH = 150.0f;

	/**
	 * Experience when a player kills him. 500.
	 * The usual double experience of this mod does not multiply this number.
	 */
	public static final int EXPERIENCE_REWARD = 500;

	/**
	 * Sword hit before armor, on normal difficulty. 10 = 5 hearts on a naked player.
	 * Easy and hard are adjusted so the hit stays 10: difficulty does not change it.
	 * The Sharpness V on the sword is the same 10. It is not added a second time.
	 */
	public static final float SWORD_DAMAGE = 10.0f;

	/**
	 * How close he must be before the sword swings, in blocks.
	 * Farther than this, the blade does not touch, so the arm stays down.
	 * Lower it and he has to walk closer. Higher and he swings sooner.
	 */
	public static final double SWORD_REACH = 2.0;

	/**
	 * Ticks between sword hits. 20 is once a second. 14 is a little faster, about three hits in two seconds.
	 * Lower it and the sword lands more often. Higher and he hits less often.
	 */
	public static final int ATTACK_INTERVAL_TICKS = 14;

	/**
	 * Walk speed before the second phase. 0.30 is close to a sprint, so running away still works.
	 * Higher = he keeps up more often. Lower = he is easier to kite.
	 */
	public static final double MOVE_SPEED = 0.30;

	/**
	 * Walk speed from the moment he drops to {@link #PHASE_HEALTH}. 0.40 is clearly faster than 0.30.
	 * This is not the short burst. Higher = the second phase is harder to outrun. Lower = closer to the first phase.
	 */
	public static final double PHASE2_MOVE_SPEED = 0.40;

	/**
	 * Added on top of {@link #MOVE_SPEED} during the burst. 0.16 makes 0.46, faster than a sprint.
	 * A sprint with a Speed potion can still match it.
	 */
	public static final double BURST_BONUS = 0.16;

	/**
	 * How far one path search looks, in blocks. This is his follow range.
	 * 112 is the same number the rotten overseer uses, and that overseer keeps walking to the player.
	 * Higher = the path can go farther around a hill. Lower = a hill stops the search sooner.
	 */
	public static final double PATH_RANGE = 112.0;

	/**
	 * Past this many blocks, teleport treats the route as too far, when teleport is on.
	 * This is not how far one path search looks. That is {@link #PATH_RANGE}.
	 * Higher = he walks longer before a teleport. Lower = he teleports sooner.
	 */
	public static final double LEASH_BLOCKS = 96.0;

	/** How far from the player he appears, in blocks. */
	public static final double SPAWN_DISTANCE = 40.0;

	/** Seconds with nobody left alive in this world before he walks home. 15. A cave does not count as leaving. */
	public static final int GIVE_UP_SECONDS = 15;

	/** Seconds to heal from empty to full once he is home. 20. */
	public static final int HEAL_SECONDS = 20;

	/** Sword effects last this many seconds. One effect per landed hit, then the next. */
	public static final int HIT_EFFECT_SECONDS = 6;

	/** Shield stays down for this many seconds after the hit that knocks it. 5, like an axe. */
	public static final float SHIELD_DISABLE_SECONDS = 5.0f;

	/** Seconds between shield-breaking hits. 8. Hits in between do not knock the shield. */
	public static final int SHIELD_GAP_SECONDS = 8;

	/** Throw: he must be at least this close, in blocks. */
	public static final double THROW_RANGE = 5.0;

	/**
	 * How high the throw sends the player, in blocks. Each throw picks a number from min to max.
	 * 50 and 60 are both included. Raise either end and that throw goes higher.
	 * No Slow Falling. The landing is a normal fall, so this still kills without feather falling.
	 */
	public static final int THROW_BLOCKS_MIN = 50;
	public static final int THROW_BLOCKS_MAX = 60;

	/**
	 * Seconds from one throw to the next. 30.
	 * Higher = he throws less often. Lower = the throw comes back sooner.
	 */
	public static final int THROW_GAP_SECONDS = 30;

	/**
	 * Seconds he will not throw after a teleport.
	 * He appears 2 blocks away, inside throw range, so without this the throw happens the moment he arrives.
	 * Higher = a longer pause after he appears. 0 throws on arrival.
	 */
	public static final int THROW_AFTER_TELEPORT_SECONDS = 6;

	/** Burst lasts this many seconds. 3. He keeps walking while it is on. */
	public static final int BURST_SECONDS = 3;

	/** Player must be at least this far for a burst, and no farther than {@link #BURST_MAX}. */
	public static final double BURST_MIN = 8.0;
	public static final double BURST_MAX = 20.0;

	/** Seconds from one burst to the next. 14 normally, 10 in the second phase. */
	public static final int BURST_GAP_SECONDS = 14;
	public static final int BURST_GAP_PHASE2_SECONDS = 10;

	/** Fire starts when the player is farther than this, standing on the ground. */
	public static final double FIRE_MIN = 4.0;

	/** The first patch is this wide. 3 = a 3×3 on the floor under the player. */
	public static final int FIRE_PATCH = 3;

	/**
	 * Seconds the first patch is forced to stay lit, even on stone.
	 * Wood keeps burning after this, and the fire keeps spreading.
	 */
	public static final int FIRE_HOLD_SECONDS = 5;

	/**
	 * Seconds he keeps pushing the fire outward after he lights it. 15.
	 * He will not light a new patch until this and {@link #FIRE_HOLD_SECONDS} are both over,
	 * and then he still waits {@link #FIRE_GAP_SECONDS}.
	 */
	public static final int FIRE_SPREAD_SECONDS = 15;

	/** How far scripted fire may travel from the first patch, in blocks. Vanilla fire can go farther. */
	public static final int FIRE_SPREAD_RADIUS = 8;

	/** Most fire blocks he will place for one patch, so one cast cannot eat a whole forest at once. */
	public static final int FIRE_CAP = 80;

	/** Seconds from one fire to the next. 14 normally, 9 in the second phase. */
	public static final int FIRE_GAP_SECONDS = 14;
	public static final int FIRE_GAP_PHASE2_SECONDS = 9;

	/** Wither splash when the player is between these distances, in blocks. */
	public static final double POTION_MIN = 6.0;
	public static final double POTION_MAX = 18.0;

	/** Wither I length, in seconds. About 2 hearts through armor if you stand in it the whole time. */
	public static final int WITHER_SECONDS = 8;

	/** Seconds from one wither bottle to the next. 30 in both phases. Higher = the bottle comes back later. */
	public static final int POTION_GAP_SECONDS = 30;
	public static final int POTION_GAP_PHASE2_SECONDS = 30;

	/** Cave spiders per cast, and the most that stay alive. */
	public static final int SPIDER_COUNT = 2;
	public static final int SPIDER_CAP = 4;

	/** Player must be at least this close for spiders. Seconds between casts. */
	public static final double SPIDER_RANGE = 24.0;
	public static final int SPIDER_GAP_SECONDS = 25;

	/**
	 * How long he has no complete route before teleporting behind the player, in seconds.
	 * 5 normally, 3 in the second phase. Higher = a longer wait; lower = he catches up sooner.
	 * He waits until an Elytra flight ends. A wall on the same floor is dug, not skipped.
	 */
	public static final int TELEPORT_WAIT_SECONDS = 5;
	public static final int TELEPORT_WAIT_PHASE2_SECONDS = 3;

	/**
	 * If the path does not reach you and you are farther than this, in blocks, he teleports.
	 * Lower = he teleports sooner; higher = he may mine a deep cave for longer. 8 still lets him dig a house wall.
	 * For an unreachable Elytra target, the teleport stays pending until the player stops gliding.
	 */
	public static final double TELEPORT_INSTEAD_OF_DIG = 8.0;

	/** Seconds before he can teleport again. 8. */
	public static final int TELEPORT_GAP_SECONDS = 8;

	/**
	 * Seconds from one bow shot to the next. Each shot waits a random number from min to max.
	 * 14 and 16 are both included. Higher = the bow comes out less often.
	 */
	public static final int BOW_GAP_MIN_SECONDS = 14;
	public static final int BOW_GAP_MAX_SECONDS = 16;

	/**
	 * How long the bow stays in his hand before he fires, in seconds. 1.
	 * He keeps walking. 0 fires the moment he pulls it out. Higher and he aims longer.
	 */
	public static final int BOW_DRAW_SECONDS = 1;

	/**
	 * Seconds between bow shots while he is in water or lava.
	 * 0 means he draws again as soon as the last arrow leaves, so he keeps shooting the whole time he is in the fluid.
	 * On dry ground the wait stays {@link #BOW_GAP_MIN_SECONDS} to {@link #BOW_GAP_MAX_SECONDS}.
	 * Higher = slower shooting in water and lava.
	 */
	public static final int FLUID_BOW_GAP_SECONDS = 0;

	/**
	 * Arrow damage before armor. 12 is about 2 hearts through a full netherite set with no Protection
	 * (20 armor, 12 toughness). Protection on that armor makes the hit smaller.
	 * A naked player takes 6 hearts. Easy and hard are adjusted so the hit stays 12.
	 * The Power number on the bow follows this and {@link #BOW_SPEED}.
	 * Higher = the arrow hurts more. Lower = it is closer to a sword hit.
	 */
	public static final float BOW_DAMAGE = 12.0f;

	/**
	 * How fast the arrow flies. 3 is a fully drawn bow. Lower = a slower arrow, same damage.
	 * The Power on the bow is whatever level matches this speed and {@link #BOW_DAMAGE}.
	 */
	public static final float BOW_SPEED = 3.0f;

	/** Punch on the bow. 5 throws the player a long way. 2 is a normal Punch II bow. */
	public static final int BOW_PUNCH = 5;

	/** He teleports this many blocks behind where the player is looking. */
	public static final double TELEPORT_BEHIND = 2.0;

	/**
	 * He breaks the glass above his head and the floor you stand on only when you are at least this far above him.
	 * 1 is a doorway he is stuck under. 0 would make him dig every rise in the road.
	 */
	public static final double DIG_UP_MIN = 1.0;

	/**
	 * He breaks that ceiling only when you are this close, in blocks.
	 * 5 is standing in the doorway. Higher and he digs a hill from far away.
	 */
	public static final double DIG_UP_RANGE = 5.0;

	/**
	 * How much he avoids stepping into water. 0 sends the path through the river, and he spins there.
	 * 4 prefers the bank. Once he is in, he swims straight. Higher and he refuses a river he could cross.
	 */
	public static final float WATER_PATH_COST = 4.0f;

	/**
	 * How many blocks above him counts as a floor he cannot walk onto.
	 * 3 is one storey, such as a church loft.
	 * Higher and a low second floor does not make him teleport. Lower and a single step does.
	 */
	public static final double PLATFORM_BLOCKS = 3.0;

	/** After a teleport he does not swing for this many ticks. 10 = half a second. */
	public static final int TELEPORT_SWING_DELAY_TICKS = 10;

	/**
	 * Dirt and wood, in ticks. 4 = a fifth of a second.
	 * Stone is {@link #STONE_BREAK_TICKS} (20 = 1 second). Obsidian is {@link #OBSIDIAN_BREAK_TICKS} (160 = 8 seconds).
	 */
	public static final int DIRT_BREAK_TICKS = 4;
	public static final int STONE_BREAK_TICKS = 20;
	public static final int OBSIDIAN_BREAK_TICKS = 160;

	/** Hardness of stone and obsidian. Used only to place other blocks between those two speeds. */
	private static final float STONE_HARDNESS = 1.5f;
	private static final float OBSIDIAN_HARDNESS = 50.0f;

	/** Blocks he never breaks. A chest on the path still breaks, and its items fall. */
	private static final Set<Block> UNBREAKABLE = Set.of(
			Blocks.BEDROCK,
			Blocks.NETHER_PORTAL,
			Blocks.END_PORTAL,
			Blocks.END_PORTAL_FRAME,
			Blocks.END_GATEWAY,
			Blocks.BARRIER
	);

	// ========================================================================

	private static final Identifier BURST_ID = EvolvingThreats.id("white_eyes_burst");
	private static final AttributeModifier BURST_MODIFIER = new AttributeModifier(
			BURST_ID, BURST_BONUS, AttributeModifier.Operation.ADD_VALUE);
	private static final double GRAVITY_PER_TICK = 0.08;
	private static final double VERTICAL_DRAG = 0.98;

	public static EntityType<WhiteEyes> TYPE;

	private boolean anchorSet;
	private BlockPos anchor = BlockPos.ZERO;
	private boolean phase2;
	private boolean resetting;
	private boolean healing;
	private boolean holdStill;
	private boolean reflecting;

	private int absenceTicks;
	private int returnTicks;
	private int throwCooldown;
	private int throwWindup;
	private int burstCooldown;
	private int burstWindup;
	private int burstTicks;
	private int fireCooldown;
	private int fireHold;
	private int fireSpread;
	private int potionCooldown;
	private int spiderCooldown;
	private int teleportCooldown;
	private int bowCooldown;
	private int bowDraw;
	private int shieldCooldown;
	private int stuckTicks;
	private int noMeleeTicks;
	private int effectIndex;
	private int rangedShots;
	private int mirrorEvery = 2;
	private boolean pathReaches;
	/** A path with at least one step to walk, even if it does not reach the player yet. He walks that instead of stopping to dig. */
	private boolean pathWalkable;

	private ItemStack swordItem = ItemStack.EMPTY;
	private ItemStack bowItem = ItemStack.EMPTY;

	private @Nullable BlockPos digPos;
	private float digProgress;
	private @Nullable BlockPos fireOrigin;
	private final List<BlockPos> fires = new ArrayList<>();
	/** Cells the player put out. He does not light those again until the next patch. */
	private final Set<BlockPos> extinguished = new HashSet<>();
	private final List<UUID> spiders = new ArrayList<>();

	public WhiteEyes(EntityType<? extends WhiteEyes> type, Level level) {
		super(type, level);
		this.xpReward = EXPERIENCE_REWARD;
		this.setCanPickUpLoot(false);
		this.setPathfindingMalus(PathType.WATER, WATER_PATH_COST);
		this.setPathfindingMalus(PathType.WATER_BORDER, 0.0f);
		this.setPathfindingMalus(PathType.LAVA, 0.0f);
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EvolvingThreats.id("white_eyes"));
		TYPE = Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.of(WhiteEyes::new, MobCategory.MONSTER)
						.sized(0.6f, 1.8f)
						.eyeHeight(1.62f)
						.clientTrackingRange(10)
						.notInPeaceful()
						.noLootTable()
						.build(key)
		);
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
				.add(Attributes.ATTACK_DAMAGE, SWORD_DAMAGE)
				.add(Attributes.FOLLOW_RANGE, PATH_RANGE)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	public static boolean hasRoom(ServerLevel level, BlockPos feet) {
		if (TYPE == null) {
			return false;
		}
		BlockPos below = feet.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				&& level.getBlockState(below).getFluidState().isEmpty()) {
			return false;
		}
		return level.noCollision(TYPE.getSpawnAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
	}

	/** Test command and the visit night. Null when the mod is off or the entity cannot be created. */
	public static @Nullable WhiteEyes spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED || TYPE == null) {
			return null;
		}
		WhiteEyes eyes = TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (eyes == null) {
			return null;
		}
		eyes.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		eyes.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(BlockPos.containing(pos)),
				EntitySpawnReason.MOB_SUMMONED,
				null
		);
		eyes.setAnchor(BlockPos.containing(pos));
		if (lookAt != null) {
			eyes.setTarget(lookAt);
			eyes.getLookControl().setLookAt(lookAt, 30.0f, 30.0f);
		}
		level.addFreshEntity(eyes);
		return eyes;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Target goals would drop a creative player and switch to a villager. The player is chosen in serverTick instead.
		this.targetSelector.removeAllGoals(goal -> true);
		// The copied attack goal gives up when he cannot see you, then the stroll walks him to a point of his own.
		// Both go. WhiteEyesAttackGoal is that same attack goal with the follow-without-sight switch turned on.
		this.goalSelector.removeAllGoals(goal -> {
			String name = goal.getClass().getSimpleName();
			return name.equals("ZombieAttackGoal")
					|| name.equals("WaterAvoidingRandomStrollGoal")
					|| name.equals("MoveThroughVillageGoal")
					|| name.equals("SpearUseGoal")
					|| name.equals("ZombieAttackTurtleEggGoal");
		});
		this.goalSelector.addGoal(2, new WhiteEyesAttackGoal(this));
		// Priority 1, above the attack. While a trick holds him still, this one goal owns walking. Otherwise it stays off.
		this.goalSelector.addGoal(1, new HoldStillGoal());
	}

	@Override
	public SpawnGroupData finalizeSpawn(
			ServerLevelAccessor level,
			net.minecraft.world.DifficultyInstance difficulty,
			EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.setBaby(false);
		this.setCanPickUpLoot(false);
		this.setPersistenceRequired();
		this.setLeftHanded(false);
		if (level instanceof ServerLevel server) {
			this.equip(server);
		}
		this.applyAttributes(true);
		this.nameSelf();
		this.mirrorEvery = this.random.nextBoolean() ? 2 : 4;
		this.throwCooldown = 40;
		this.burstCooldown = 60;
		this.fireCooldown = 80;
		this.potionCooldown = 100;
		this.spiderCooldown = 120;
		this.bowCooldown = this.bowGap(false);
		return data;
	}

	@Override
	public void aiStep() {
		if (this.level() instanceof ServerLevel server && this.isAlive()) {
			this.serverTick(server);
		}
		super.aiStep();
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	/**
	 * The game drops a creative player inside setTarget and getTarget.
	 * His chase then has nobody to follow, so he stops where he stands.
	 * Creative still counts. Spectators and the dead do not.
	 */
	@Override
	protected LivingEntity asValidTarget(LivingEntity entity) {
		if (entity instanceof Player player && player.isSpectator()) {
			return null;
		}
		if (entity != null && !entity.isAlive()) {
			return null;
		}
		return entity;
	}

	@Override
	public void playAmbientSound() {
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLAZE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(state.getSoundType().getStepSound(), 0.15f, 1.0f);
	}

	@Override
	public void playAttackSound() {
		this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
	}

	/**
	 * A raised shield is knocked for {@link #SHIELD_DISABLE_SECONDS}, then not again
	 * until {@link #SHIELD_GAP_SECONDS} have passed. The game asks only when a block connected.
	 */
	@Override
	public float getSecondsToDisableBlocking() {
		if (!DEBUG_SHIELD_BREAK || this.shieldCooldown > 0) {
			return 0.0f;
		}
		this.shieldCooldown = Math.max(1, SHIELD_GAP_SECONDS) * 20;
		return Math.max(0.0f, SHIELD_DISABLE_SECONDS);
	}

	@Override
	public boolean isWithinMeleeAttackRange(net.minecraft.world.entity.LivingEntity target) {
		return this.distanceToSqr(target) <= SWORD_REACH * SWORD_REACH;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (this.noMeleeTicks > 0) {
			return false;
		}
		float damage = swordDamage(level);
		if (damage <= 0.0f) {
			return false;
		}
		boolean landed = target.hurtServer(level, this.damageSources().mobAttack(this), damage);
		if (landed) {
			this.setLastHurtMob(target);
			this.playAttackSound();
			if (DEBUG_SWORD_EFFECTS && target instanceof Player player) {
				this.applyHitEffect(player);
			}
		}
		return landed;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean landed = super.hurtServer(level, source, damage);
		if (landed) {
			this.noteRangedHit(level, source, damage);
			if (!this.phase2 && this.getHealth() <= PHASE_HEALTH) {
				this.enterPhase2(level);
			}
		}
		return landed;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!this.level().isClientSide()
				&& reason != RemovalReason.UNLOADED_TO_CHUNK
				&& reason != RemovalReason.CHANGED_DIMENSION) {
			this.discardSpiders();
		}
		super.remove(reason);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		// Take the bow away before the game rolls what he drops. A zero drop chance is not enough
		// if the bow is still in his hand when that roll happens.
		this.bowDraw = 0;
		this.removeBow();
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, this.nameTagName());
		this.spawnAtLocation(level, tag);
		this.spawnAtLocation(level, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("WhiteEyesAnchor", this.anchorSet);
		output.putInt("WhiteEyesAX", this.anchor.getX());
		output.putInt("WhiteEyesAY", this.anchor.getY());
		output.putInt("WhiteEyesAZ", this.anchor.getZ());
		output.putBoolean("WhiteEyesPhase2", this.phase2);
		output.putBoolean("WhiteEyesResetting", this.resetting);
		output.putInt("WhiteEyesShots", this.rangedShots);
		output.putInt("WhiteEyesMirrorEvery", this.mirrorEvery);
		output.putInt("WhiteEyesEffect", this.effectIndex);
		output.putInt("WhiteEyesBowWait", this.bowCooldown);
		output.putInt("WhiteEyesBowDraw", this.bowDraw);
		StringBuilder ids = new StringBuilder();
		for (UUID id : this.spiders) {
			if (!ids.isEmpty()) {
				ids.append(',');
			}
			ids.append(id);
		}
		output.putString("WhiteEyesSpiders", ids.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.anchorSet = input.getBooleanOr("WhiteEyesAnchor", false);
		this.anchor = new BlockPos(
				input.getIntOr("WhiteEyesAX", this.getBlockX()),
				input.getIntOr("WhiteEyesAY", this.getBlockY()),
				input.getIntOr("WhiteEyesAZ", this.getBlockZ())
		);
		this.phase2 = input.getBooleanOr("WhiteEyesPhase2", false);
		this.resetting = input.getBooleanOr("WhiteEyesResetting", false);
		this.rangedShots = input.getIntOr("WhiteEyesShots", 0);
		this.mirrorEvery = input.getIntOr("WhiteEyesMirrorEvery", 2);
		this.effectIndex = input.getIntOr("WhiteEyesEffect", 0);
		this.bowCooldown = input.getIntOr("WhiteEyesBowWait", 0);
		this.bowDraw = input.getIntOr("WhiteEyesBowDraw", 0);
		this.spiders.clear();
		input.getString("WhiteEyesSpiders").ifPresent(text -> {
			if (text.isBlank()) {
				return;
			}
			for (String part : text.split(",")) {
				try {
					this.spiders.add(UUID.fromString(part));
				} catch (IllegalArgumentException ignored) {
					// A broken id from an old file is dropped.
				}
			}
		});
		this.applyAttributes(false);
		this.nameSelf();
		if (this.level() instanceof ServerLevel server) {
			boolean holdingBow = this.getMainHandItem().is(Items.BOW);
			this.equip(server);
			if (holdingBow) {
				this.holdBow();
			}
		}
	}

	private void serverTick(ServerLevel level) {
		if (!this.anchorSet) {
			this.setAnchor(this.blockPosition());
		}
		this.tickDown();
		this.maintainFire(level);
		this.keepSpiders(level);
		if (this.resetting) {
			this.tickReset(level);
			return;
		}
		ServerPlayer player = this.findPrey(level);
		if (player == null) {
			this.absenceTicks++;
			this.stuckTicks = 0;
			this.setTarget(null);
			this.holdStill = false;
			if (this.bowDraw > 0) {
				this.bowDraw = 0;
				this.holdSword();
			}
			if (this.absenceTicks >= GIVE_UP_SECONDS * 20) {
				this.beginReset(level);
			}
			return;
		}
		if (this.getTarget() != player) {
			this.stuckTicks = 0;
		}
		this.absenceTicks = 0;
		this.setTarget(player);
		this.getLookControl().setLookAt(player, 30.0f, 30.0f);
		if (this.tickCount <= 1 || this.tickCount % 10 == 0) {
			this.refreshRoute(player);
		}
		if (!this.phase2 && this.getHealth() <= PHASE_HEALTH) {
			this.enterPhase2(level);
		}
		boolean busy = false;
		if (!DEBUG_SPIDERS) {
			this.discardSpiders();
		}
		if (!DEBUG_BOW && (this.bowDraw > 0 || this.getMainHandItem().is(Items.BOW))) {
			this.bowDraw = 0;
			this.holdSword();
		}
		if (!DEBUG_BURST && this.burstTicks > 0) {
			this.burstTicks = 0;
			this.setBurst(false);
		}
		if (this.phase2 || DEBUG_THROW || DEBUG_BURST || DEBUG_WITHER_POTION || DEBUG_FIRE || DEBUG_SPIDERS) {
			this.tryStartSpecial(level, player);
		}
		if (DEBUG_DIG) {
			this.tickDig(level, player, busy);
		} else {
			this.clearDig(level);
		}
		if (DEBUG_TELEPORT) {
			this.tickTeleport(level, player);
		}
		if (DEBUG_BOW) {
			this.tickBow(level, player);
		}
		this.holdStill = busy || this.digPos != null;
		if (this.holdStill) {
			this.getNavigation().stop();
		}
	}

	private void tickDown() {
		this.throwCooldown = down(this.throwCooldown);
		this.burstCooldown = down(this.burstCooldown);
		this.fireCooldown = down(this.fireCooldown);
		this.potionCooldown = down(this.potionCooldown);
		this.spiderCooldown = down(this.spiderCooldown);
		this.teleportCooldown = down(this.teleportCooldown);
		this.bowCooldown = down(this.bowCooldown);
		this.shieldCooldown = down(this.shieldCooldown);
		this.noMeleeTicks = down(this.noMeleeTicks);
		if (this.burstTicks > 0) {
			this.burstTicks--;
			if (this.burstTicks == 0) {
				this.setBurst(false);
			}
		}
	}

	private void tryStartSpecial(ServerLevel level, ServerPlayer player) {
		double distance = this.distanceTo(player);
		if (this.throwCooldown <= 0 && DEBUG_THROW && distance <= THROW_RANGE) {
			this.throwCooldown = THROW_GAP_SECONDS * 20;
			this.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
			this.launch(level, player);
			return;
		}
		if (this.burstCooldown <= 0 && DEBUG_BURST && distance >= BURST_MIN && distance <= BURST_MAX) {
			this.burstCooldown = (this.phase2 ? BURST_GAP_PHASE2_SECONDS : BURST_GAP_SECONDS) * 20;
			this.playSound(SoundEvents.WARDEN_HEARTBEAT, 1.2f, 1.0f);
			this.burstTicks = BURST_SECONDS * 20;
			this.setBurst(true);
			return;
		}
		if (this.potionCooldown <= 0 && (this.phase2 || DEBUG_WITHER_POTION) && distance >= POTION_MIN && distance <= POTION_MAX) {
			this.potionCooldown = (this.phase2 ? POTION_GAP_PHASE2_SECONDS : POTION_GAP_SECONDS) * 20;
			this.throwWither(level, player);
			return;
		}
		if ((this.phase2 || DEBUG_FIRE) && this.fireCooldown <= 0 && this.fireHold <= 0 && this.fireSpread <= 0
				&& distance > FIRE_MIN && player.onGround() && !player.isInWater()
				&& this.lightFloor(level, player)) {
			this.fireCooldown = (this.phase2 ? FIRE_GAP_PHASE2_SECONDS : FIRE_GAP_SECONDS) * 20;
			return;
		}
		if (DEBUG_SPIDERS && this.spiderCooldown <= 0 && distance <= SPIDER_RANGE) {
			this.spiderCooldown = SPIDER_GAP_SECONDS * 20;
			this.summonSpiders(level, player);
		}
	}

	private void refreshRoute(ServerPlayer player) {
		// Read the path he is already walking. A second search here reused the same nodes
		// and wiped that path, so he stopped where he stood.
		Path path = this.getNavigation().getPath();
		this.pathReaches = path != null && path.canReach();
		this.pathWalkable = path != null && !path.isDone() && path.getNodeCount() > 1;
	}

	private void tickDig(ServerLevel level, ServerPlayer player, boolean busy) {
		if (busy || !this.canGrief(level) || this.isWithinMeleeAttackRange(player) || this.pathReaches) {
			this.clearDig(level);
			return;
		}
		// Past his pathfinding range he waits for the catch-up teleport instead of digging a distant wall.
		if (this.distanceTo(player) > LEASH_BLOCKS) {
			this.clearDig(level);
			return;
		}
		// A long gap with no path at all is a cave. He teleports instead of mining it.
		// Partial paths with steps left around hills are followed. A route that only reaches his own square is no route.
		if (!this.pathWalkable && this.distanceTo(player) > TELEPORT_INSTEAD_OF_DIG) {
			this.clearDig(level);
			return;
		}
		// Open ground and a single step are not dug. Only a wall that fills his legs and his head.
		BlockPos next = this.pickDig(level, player);
		if (next == null) {
			this.clearDig(level);
			return;
		}
		if (!next.equals(this.digPos)) {
			this.clearDig(level);
			this.digPos = next.immutable();
			this.digProgress = 0.0f;
		}
		BlockState state = level.getBlockState(this.digPos);
		if (!this.canBreak(level, this.digPos, state)) {
			this.clearDig(level);
			return;
		}
		float hardness = state.getDestroySpeed(level, this.digPos);
		int ticks = breakTicks(hardness);
		this.digProgress += 1.0f / Math.max(1, ticks);
		int crack = Math.min(9, (int) (this.digProgress * 10.0f));
		level.destroyBlockProgress(this.getId(), this.digPos, crack);
		if (this.tickCount % 4 == 0) {
			this.playSound(state.getSoundType().getHitSound(), 0.4f, 0.8f);
		}
		if (this.digProgress >= 1.0f) {
			level.destroyBlockProgress(this.getId(), this.digPos, -1);
			level.destroyBlock(this.digPos, true, this, 512);
			this.digPos = null;
			this.digProgress = 0.0f;
		}
	}

	private void tickTeleport(ServerLevel level, ServerPlayer player) {
		double dist = this.distanceTo(player);
		boolean far = dist > TELEPORT_INSTEAD_OF_DIG;
		boolean upstairs = player.getY() >= this.getY() + PLATFORM_BLOCKS;
		// A partial path with steps left is followed. A route that goes nowhere, or any route past
		// LEASH_BLOCKS, counts as no route: he walks straight at the player and teleports after the wait.
		boolean noWay = !this.pathReaches && (!this.pathWalkable || dist > LEASH_BLOCKS);
		boolean longDig = far && noWay;
		boolean cantReachUp = upstairs && !this.pathReaches;
		boolean noRoute = noWay && this.digPos == null;
		boolean stuck = longDig || cantReachUp || noRoute;
		if (!stuck || this.throwWindup > 0 || this.burstWindup > 0) {
			if (!stuck) {
				this.stuckTicks = 0;
			}
			return;
		}
		this.stuckTicks++;
		if (this.stuckTicks == 1) {
			this.playSound(SoundEvents.ENDERMAN_STARE, 1.0f, 0.8f);
		}
		int need = (this.phase2 ? TELEPORT_WAIT_PHASE2_SECONDS : TELEPORT_WAIT_SECONDS) * 20;
		// Not while the player glides. The wait keeps counting, so the teleport comes right after landing.
		if (this.stuckTicks >= need && this.teleportCooldown <= 0 && !player.isFallFlying()) {
			if (this.teleportBehind(level, player)) {
				this.stuckTicks = 0;
			}
		}
	}

	private void tickReset(ServerLevel level) {
		this.setTarget(null);
		this.holdStill = false;
		this.clearDig(level);
		double dist = Math.sqrt(this.distanceToSqr(this.anchor.getX() + 0.5, this.anchor.getY(), this.anchor.getZ() + 0.5));
		this.returnTicks++;
		if (!this.healing && dist > 2.5 && this.returnTicks > 100) {
			this.snapTo(this.anchor.getX() + 0.5, this.anchor.getY(), this.anchor.getZ() + 0.5, this.getYRot(), 0.0f);
			this.healing = true;
		} else if (!this.healing && dist > 2.5) {
			this.getNavigation().moveTo(this.anchor.getX() + 0.5, this.anchor.getY(), this.anchor.getZ() + 0.5, 1.0);
		} else {
			this.healing = true;
			this.getNavigation().stop();
		}
		if (this.healing) {
			float step = MAX_HEALTH / Math.max(1, HEAL_SECONDS * 20);
			this.setHealth(Math.min(MAX_HEALTH, this.getHealth() + step));
			if (this.getHealth() >= MAX_HEALTH - 0.01f) {
				this.setHealth(MAX_HEALTH);
				this.resetting = false;
				this.healing = false;
				this.returnTicks = 0;
				this.absenceTicks = 0;
			}
		}
	}

	private void beginReset(ServerLevel level) {
		this.resetting = true;
		this.healing = false;
		this.returnTicks = 0;
		this.setTarget(null);
		this.clearDig(level);
		this.discardSpiders();
		this.setBurst(false);
		this.burstTicks = 0;
		this.burstWindup = 0;
		this.throwWindup = 0;
		this.bowDraw = 0;
		this.holdSword();
		this.holdStill = false;
	}

	private @Nullable ServerPlayer findPrey(ServerLevel level) {
		// Every tick, the closest living player in this world. Creative counts. Spectators and the dead do not.
		// Another dimension is a different list, so he does not see you there. The chase does not clear this target.
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			double dist = this.distanceToSqr(player);
			if (dist < bestDist) {
				best = player;
				bestDist = dist;
			}
		}
		return best;
	}

	private void launch(ServerLevel level, ServerPlayer player) {
		int blocks = THROW_BLOCKS_MIN;
		if (THROW_BLOCKS_MAX > THROW_BLOCKS_MIN) {
			blocks += this.random.nextInt(THROW_BLOCKS_MAX - THROW_BLOCKS_MIN + 1);
		}
		double vy = velocityForHeight(blocks);
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, vy, motion.z);
		player.resetFallDistance();
		player.syncVelocity = true;
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 20, 0.3, 0.2, 0.3, 0.02);
	}

	private void throwWither(ServerLevel level, ServerPlayer player) {
		Vec3 motion = player.getDeltaMovement();
		double xd = player.getX() + motion.x - this.getX();
		double yd = player.getEyeY() - 1.1 - this.getY();
		double zd = player.getZ() + motion.z - this.getZ();
		double dist = Math.sqrt(xd * xd + zd * zd);
		ItemStack bottle = new ItemStack(Items.SPLASH_POTION);
		bottle.set(
				net.minecraft.core.component.DataComponents.POTION_CONTENTS,
				PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.WITHER, WITHER_SECONDS * 20, 0))
		);
		Projectile.spawnProjectileUsingShoot(
				net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion::new,
				level,
				bottle,
				this,
				xd,
				yd + dist * 0.2,
				zd,
				dist <= 2.0 ? 0.45f : 0.75f,
				1.0f
		);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0f, 0.8f);
	}

	private boolean lightFloor(ServerLevel level, ServerPlayer player) {
		if (!this.canGrief(level)) {
			return false;
		}
		BlockPos feet = player.blockPosition();
		int half = FIRE_PATCH / 2;
		this.fires.clear();
		this.extinguished.clear();
		this.fireOrigin = feet.immutable();
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				this.placeFire(level, feet.offset(dx, 0, dz), true);
			}
		}
		if (this.fires.isEmpty()) {
			this.fireOrigin = null;
			return false;
		}
		this.fireHold = FIRE_HOLD_SECONDS * 20;
		this.fireSpread = FIRE_SPREAD_SECONDS * 20;
		level.playSound(null, feet, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0f, 0.8f);
		return true;
	}

	private void maintainFire(ServerLevel level) {
		if (!this.phase2 && !DEBUG_FIRE) {
			this.fireHold = 0;
			this.fireSpread = 0;
			this.fires.clear();
			return;
		}
		if (this.fireHold > 0) {
			this.fireHold--;
			if (this.fireOrigin != null && this.canGrief(level)) {
				int half = FIRE_PATCH / 2;
				for (int dx = -half; dx <= half; dx++) {
					for (int dz = -half; dz <= half; dz++) {
						BlockPos cell = this.fireOrigin.offset(dx, 0, dz);
						// Wood keeps a flame by itself. If that flame is gone, the player put it out.
						if (this.alreadyLit(cell) && level.getBlockState(cell).isAir()
								&& level.getBlockState(cell.below()).ignitedByLava()) {
							this.forgetFire(cell);
							continue;
						}
						this.placeFire(level, cell, false);
					}
				}
			}
		}
		if (this.fireSpread > 0) {
			this.fireSpread--;
			if (this.fireSpread % 20 == 0 && this.canGrief(level)) {
				this.spreadFire(level);
			}
		}
	}

	private void spreadFire(ServerLevel level) {
		List<BlockPos> edge = new ArrayList<>(this.fires);
		List<BlockPos> added = new ArrayList<>();
		for (BlockPos pos : edge) {
			if (this.fires.size() + added.size() >= FIRE_CAP) {
				break;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				this.offerFire(level, pos.relative(dir), added, true);
			}
		}
		this.fires.addAll(added);
	}

	/** Puts fire on a solid floor. Spread cells only stick if wood is there to catch. */
	private void offerFire(ServerLevel level, BlockPos pos, List<BlockPos> added, boolean spread) {
		if (this.isExtinguished(pos)) {
			return;
		}
		if (this.fires.size() + added.size() >= FIRE_CAP) {
			return;
		}
		if (this.fireOrigin != null && this.fireOrigin.distSqr(pos) > (double) FIRE_SPREAD_RADIUS * FIRE_SPREAD_RADIUS) {
			return;
		}
		if (!this.canLight(level, pos)) {
			return;
		}
		if (spread && !this.originalPatch(pos) && !this.touchesFuel(level, pos)) {
			return;
		}
		if (this.alreadyLit(pos) || contains(added, pos)) {
			return;
		}
		level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
		added.add(pos.immutable());
	}

	private void placeFire(ServerLevel level, BlockPos pos, boolean remember) {
		if (this.isExtinguished(pos) || !this.canLight(level, pos)) {
			return;
		}
		level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
		if (remember && !this.alreadyLit(pos)) {
			this.fires.add(pos.immutable());
		}
	}

	private boolean isExtinguished(BlockPos pos) {
		return this.extinguished.contains(pos);
	}

	/** A punched flame stays out for the rest of this patch. */
	public void forgetFire(BlockPos pos) {
		BlockPos key = pos.immutable();
		this.extinguished.add(key);
		this.fires.removeIf(has -> has.equals(key));
	}

	public static void onFirePunched(ServerLevel level, BlockPos pos) {
		// Any distance. The old 48-block box missed him, so he lit that cell again.
		for (WhiteEyes eyes : level.getEntities(WhiteEyes.TYPE, Entity::isAlive)) {
			eyes.forgetFire(pos);
		}
	}

	private boolean canLight(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
			return false;
		}
		BlockState floor = level.getBlockState(pos.below());
		return !floor.isAir() && floor.getFluidState().isEmpty();
	}

	private boolean originalPatch(BlockPos pos) {
		if (this.fireOrigin == null || pos.getY() != this.fireOrigin.getY()) {
			return false;
		}
		int half = FIRE_PATCH / 2;
		return Math.abs(pos.getX() - this.fireOrigin.getX()) <= half
				&& Math.abs(pos.getZ() - this.fireOrigin.getZ()) <= half;
	}

	private boolean touchesFuel(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos.below()).ignitedByLava()) {
			return true;
		}
		for (Direction dir : Direction.values()) {
			if (level.getBlockState(pos.relative(dir)).ignitedByLava()) {
				return true;
			}
		}
		return false;
	}

	private boolean alreadyLit(BlockPos pos) {
		return contains(this.fires, pos);
	}

	private static boolean contains(List<BlockPos> list, BlockPos pos) {
		for (BlockPos has : list) {
			if (has.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	private void summonSpiders(ServerLevel level, ServerPlayer player) {
		this.keepSpiders(level);
		int room = SPIDER_CAP - this.spiders.size();
		int count = Math.min(SPIDER_COUNT, room);
		for (int i = 0; i < count; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double dist = 1.5 + this.random.nextDouble();
			int x = this.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
			int z = this.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
			int y = this.getBlockY();
			BlockPos feet = new BlockPos(x, y, z);
			if (!level.getBlockState(feet).isAir()) {
				feet = feet.above();
			}
			if (!level.getBlockState(feet).isAir()) {
				continue;
			}
			CaveSpider spider = EntityTypes.CAVE_SPIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (spider == null) {
				continue;
			}
			spider.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, this.random.nextFloat() * 360.0f, 0.0f);
			spider.setPersistenceRequired();
			spider.setTarget(player);
			level.addFreshEntity(spider);
			this.spiders.add(spider.getUUID());
		}
	}

	private void keepSpiders(ServerLevel level) {
		this.spiders.removeIf(id -> {
			Entity entity = level.getEntity(id);
			if (!(entity instanceof CaveSpider spider) || !spider.isAlive()) {
				return true;
			}
			if (spider.getTarget() == this) {
				LivingEntity prey = this.getTarget();
				spider.setTarget(prey instanceof Player ? prey : null);
			}
			return false;
		});
	}

	private void discardSpiders() {
		if (this.level() instanceof ServerLevel level) {
			for (UUID id : this.spiders) {
				Entity entity = level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		}
		this.spiders.clear();
	}

	private boolean teleportBehind(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		double x = player.getX() - look.x * TELEPORT_BEHIND;
		double z = player.getZ() - look.z * TELEPORT_BEHIND;
		double sideX = -look.z;
		double sideZ = look.x;
		double[][] spots = {
				{x, player.getY(), z},
				{x + sideX, player.getY(), z + sideZ},
				{x - sideX, player.getY(), z - sideZ},
				{player.getX() - look.x, player.getY(), player.getZ() - look.z},
		};
		for (double[] spot : spots) {
			BlockPos feet = BlockPos.containing(spot[0], spot[1], spot[2]);
			if (!hasRoom(level, feet)) {
				feet = feet.above();
				if (!hasRoom(level, feet)) {
					continue;
				}
			}
			double fromX = this.getX();
			double fromY = this.getY();
			double fromZ = this.getZ();
			this.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			this.getNavigation().stop();
			this.clearDig(level);
			this.noMeleeTicks = TELEPORT_SWING_DELAY_TICKS;
			this.teleportCooldown = TELEPORT_GAP_SECONDS * 20;
			this.throwCooldown = Math.max(this.throwCooldown, THROW_AFTER_TELEPORT_SECONDS * 20);
			if (this.bowDraw > 0) {
				this.bowDraw = 0;
				this.holdSword();
				this.bowCooldown = Math.max(this.bowCooldown, BOW_GAP_MIN_SECONDS * 20);
			}
			level.sendParticles(ParticleTypes.PORTAL, fromX, fromY + 1.0, fromZ, 40, 0.3, 0.5, 0.3, 0.2);
			level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.3, 0.5, 0.3, 0.2);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
			this.getLookControl().setLookAt(player, 30.0f, 30.0f);
			return true;
		}
		return false;
	}

	private void noteRangedHit(ServerLevel level, DamageSource source, float damage) {
		if (this.reflecting || damage <= 0.0f) {
			return;
		}
		if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) {
			return;
		}
		if (!(arrow.getOwner() instanceof Player player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return;
		}
		this.rangedShots++;
		int every = this.mirrorEvery == 4 ? 4 : 2;
		if (this.rangedShots < every) {
			return;
		}
		this.rangedShots = 0;
		this.mirrorEvery = this.random.nextBoolean() ? 2 : 4;
		float reflected = reflectedAmount(level, damage);
		if (reflected <= 0.0f) {
			return;
		}
		this.reflecting = true;
		try {
			player.hurtServer(level, this.damageSources().mobAttack(this), reflected);
		} finally {
			this.reflecting = false;
		}
	}

	private void enterPhase2(ServerLevel level) {
		this.phase2 = true;
		this.setBase(Attributes.MOVEMENT_SPEED, PHASE2_MOVE_SPEED);
		this.playSound(SoundEvents.BLAZE_SHOOT, 1.2f, 0.7f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
	}

	private void applyHitEffect(Player player) {
		int index = Math.floorMod(this.effectIndex, 4);
		this.effectIndex++;
		int ticks = HIT_EFFECT_SECONDS * 20;
		MobEffectInstance effect = switch (index) {
			case 0 -> new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1);
			case 1 -> new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1);
			case 2 -> new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0);
			default -> new MobEffectInstance(MobEffects.HUNGER, ticks, 2);
		};
		player.addEffect(effect, this);
	}

	/**
	 * The path is finished and the sword still cannot reach. He walks straight at the player's ground position
	 * instead of standing still, including into a wall. His own height is kept, so he does not jump at a player in the air.
	 * He stops inside sword reach.
	 */
	private void walkStraightAt(LivingEntity target) {
		double dx = target.getX() - this.getX();
		double dz = target.getZ() - this.getZ();
		if (dx * dx + dz * dz <= SWORD_REACH * SWORD_REACH) {
			return;
		}
		this.getMoveControl().setWantedPosition(target.getX(), this.getY(), target.getZ(), 1.0);
	}

	/** In water a ground path turns him in circles. He swims straight at the player and up to the surface. */
	private void swimToward(LivingEntity target) {
		this.getNavigation().stop();
		double sx = target.getX() - this.getX();
		double sz = target.getZ() - this.getZ();
		double len = Math.sqrt(sx * sx + sz * sz);
		if (len > 0.05) {
			sx = sx / len * 0.10;
			sz = sz / len * 0.10;
		} else {
			sx = 0.0;
			sz = 0.0;
		}
		double sy = this.getDeltaMovement().y;
		if (this.getFluidHeight(FluidTags.WATER) > 0.4) {
			sy = Math.max(sy, 0.06);
		}
		this.setDeltaMovement(sx, sy, sz);
		if (len > 0.05) {
			float yaw = (float) (Mth.atan2(sz, sx) * (180.0 / Math.PI)) - 90.0f;
			this.setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
		}
	}

	/**
	 * A wall in front of him, not the floor and not a single step.
	 * One solid block at foot level is a step he walks onto. A wall is solid at the legs and the head.
	 * The floor under his feet is never chosen, even when his position dips into that block.
	 */
	private @Nullable BlockPos pickDig(ServerLevel level, ServerPlayer player) {
		BlockPos ceiling = this.pickCeiling(level, player);
		if (ceiling != null) {
			return ceiling;
		}
		int floorY = Mth.floor(this.getY() - 0.05);
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		int stepX = dx == 0.0 ? 0 : (dx > 0.0 ? 1 : -1);
		int stepZ = dz == 0.0 ? 0 : (dz > 0.0 ? 1 : -1);
		if (Math.abs(dx) >= Math.abs(dz)) {
			stepZ = 0;
		} else {
			stepX = 0;
		}
		if (stepX == 0 && stepZ == 0) {
			return null;
		}
		int x = Mth.floor(this.getX()) + stepX;
		int z = Mth.floor(this.getZ()) + stepZ;
		BlockPos leg = new BlockPos(x, floorY + 1, z);
		BlockPos head = new BlockPos(x, floorY + 2, z);
		boolean legWall = this.isWall(level, leg);
		boolean headWall = this.isWall(level, head);
		if (!headWall) {
			return null;
		}
		if (legWall) {
			return leg;
		}
		return head;
	}

	/**
	 * Glass above his head, and the floor the player is standing on.
	 * Only when the player is above him and close. The block under his own feet is never returned.
	 */
	private @Nullable BlockPos pickCeiling(ServerLevel level, ServerPlayer player) {
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double dy = player.getY() - this.getY();
		if (dy < DIG_UP_MIN || dx * dx + dz * dz > DIG_UP_RANGE * DIG_UP_RANGE) {
			return null;
		}
		int floorY = Mth.floor(this.getY() - 0.05);
		int x0 = Mth.floor(this.getX());
		int z0 = Mth.floor(this.getZ());
		int stepX = dx == 0.0 ? 0 : (dx > 0.0 ? 1 : -1);
		int stepZ = dz == 0.0 ? 0 : (dz > 0.0 ? 1 : -1);
		if (Math.abs(dx) >= Math.abs(dz)) {
			stepZ = 0;
		} else {
			stepX = 0;
		}
		int stand = Mth.floor(player.getY());
		BlockPos[] spots = {
				new BlockPos(x0, floorY + 2, z0),
				new BlockPos(x0, floorY + 3, z0),
				new BlockPos(x0 + stepX, stand - 1, z0 + stepZ),
				new BlockPos(Mth.floor(player.getX()), stand - 1, Mth.floor(player.getZ()))
		};
		BlockPos best = null;
		for (BlockPos spot : spots) {
			if (spot.getY() <= floorY || !this.isWall(level, spot)) {
				continue;
			}
			if (best == null || spot.getY() < best.getY() || (spot.getY() == best.getY() && spot.distSqr(this.blockPosition()) < best.distSqr(this.blockPosition()))) {
				best = spot;
			}
		}
		return best;
	}

	private boolean isWall(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!this.canBreak(level, pos, state)) {
			return false;
		}
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	private boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (UNBREAKABLE.contains(state.getBlock())) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0f;
	}

	private void clearDig(ServerLevel level) {
		if (this.digPos != null) {
			level.destroyBlockProgress(this.getId(), this.digPos, -1);
		}
		this.digPos = null;
		this.digProgress = 0.0f;
	}

	private boolean canGrief(ServerLevel level) {
		return !RESPECT_MOB_GRIEFING || Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_GRIEFING));
	}

	private void setBurst(boolean on) {
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(BURST_ID);
		if (on) {
			speed.addTransientModifier(BURST_MODIFIER);
		}
	}

	private void setAnchor(BlockPos pos) {
		this.anchor = pos.immutable();
		this.anchorSet = true;
	}

	private void equip(ServerLevel level) {
		var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> protection = registry.getOrThrow(Enchantments.PROTECTION);
		Holder<Enchantment> unbreaking = registry.getOrThrow(Enchantments.UNBREAKING);
		Holder<Enchantment> sharpness = registry.getOrThrow(Enchantments.SHARPNESS);
		Holder<Enchantment> power = registry.getOrThrow(Enchantments.POWER);
		Holder<Enchantment> punch = registry.getOrThrow(Enchantments.PUNCH);
		this.swordItem = enchanted(new ItemStack(Items.DIAMOND_SWORD), sharpness, 5, unbreaking, 3);
		this.bowItem = enchanted(new ItemStack(Items.BOW), power, bowPowerLevel(), punch, BOW_PUNCH);
		this.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(Items.IRON_HELMET), protection, 2, unbreaking, 3));
		this.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.IRON_CHESTPLATE), protection, 2, unbreaking, 3));
		this.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(Items.IRON_LEGGINGS), protection, 2, unbreaking, 3));
		this.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(Items.IRON_BOOTS), protection, 2, unbreaking, 3));
		this.holdSword();
		this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0f);
		}
	}

	/** Power level that matches {@link #BOW_DAMAGE} at {@link #BOW_SPEED}. A full draw adds 0.5 damage per level. */
	private static int bowPowerLevel() {
		double base = BOW_DAMAGE / Math.max(0.01f, BOW_SPEED);
		return Math.max(1, (int) Math.round((base - 2.5) / 0.5));
	}

	private void holdSword() {
		this.stopUsingItem();
		if (this.swordItem.isEmpty()) {
			return;
		}
		this.setItemSlot(EquipmentSlot.MAINHAND, this.swordItem.copy());
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
	}

	private void holdBow() {
		if (this.bowItem.isEmpty()) {
			return;
		}
		this.setItemSlot(EquipmentSlot.MAINHAND, this.bowItem.copy());
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		this.startUsingItem(InteractionHand.MAIN_HAND);
	}

	/** The bow is never loot. His hand goes back to the sword, or empty if the sword is missing. */
	private void removeBow() {
		this.stopUsingItem();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!this.getItemBySlot(slot).is(Items.BOW)) {
				continue;
			}
			if (slot == EquipmentSlot.MAINHAND && !this.swordItem.isEmpty()) {
				this.setItemSlot(slot, this.swordItem.copy());
			} else {
				this.setItemSlot(slot, ItemStack.EMPTY);
			}
			this.setDropChance(slot, 0.0f);
		}
	}

	private void tickBow(ServerLevel level, ServerPlayer player) {
		if (this.swordItem.isEmpty() || this.bowItem.isEmpty()) {
			this.equip(level);
		}
		boolean fluid = this.isInWater() || this.isInLava();
		if (fluid && this.bowCooldown > FLUID_BOW_GAP_SECONDS * 20) {
			this.bowCooldown = FLUID_BOW_GAP_SECONDS * 20;
		}
		if (this.bowDraw > 0) {
			this.bowDraw--;
			this.getLookControl().setLookAt(player, 30.0f, 30.0f);
			if (this.bowDraw == 0) {
				this.shootBow(level, player);
				this.holdSword();
				this.bowCooldown = this.bowGap(fluid);
			}
			return;
		}
		if (this.bowCooldown > 0 || !this.getSensing().hasLineOfSight(player)) {
			return;
		}
		this.holdBow();
		if (BOW_DRAW_SECONDS <= 0) {
			this.shootBow(level, player);
			this.holdSword();
			this.bowCooldown = this.bowGap(fluid);
			return;
		}
		this.bowDraw = BOW_DRAW_SECONDS * 20;
	}

	private int bowGap(boolean fluid) {
		if (fluid) {
			return FLUID_BOW_GAP_SECONDS * 20;
		}
		int min = BOW_GAP_MIN_SECONDS * 20;
		int max = Math.max(min, BOW_GAP_MAX_SECONDS * 20);
		if (max == min) {
			return min;
		}
		return min + this.random.nextInt(max - min + 1);
	}

	private void shootBow(ServerLevel level, ServerPlayer player) {
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, 1.0f, this.getMainHandItem());
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double dy = this.bowAimY(player, dx, dz);
		Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, dy, dz, BOW_SPEED, 0.0f);
		// Set after the shot is created, so Power on the bow is not added a second time.
		// Punch stays on the bow and throws the player when the arrow lands.
		arrow.setBaseDamage(bowDamage(level) / BOW_SPEED);
		this.playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
	}

	/**
	 * Vertical aim so a fast arrow still meets the player. Drag and gravity are the arrow's own numbers.
	 */
	private double bowAimY(ServerPlayer player, double dx, double dz) {
		double distance = Math.sqrt(dx * dx + dz * dz);
		double startY = this.getEyeY() - 0.1;
		double wantY = player.getY(0.5);
		if (distance < 0.5) {
			return wantY - startY;
		}
		double low = -distance * 2.0;
		double high = distance * 2.0;
		for (int i = 0; i < 24; i++) {
			double mid = (low + high) * 0.5;
			if (arrowHeight(distance, mid, startY) > wantY) {
				high = mid;
			} else {
				low = mid;
			}
		}
		return (low + high) * 0.5;
	}

	private static double arrowHeight(double distance, double directionY, double startY) {
		double speed = Math.max(0.01f, BOW_SPEED);
		double length = Math.sqrt(distance * distance + directionY * directionY);
		double horizontal = distance / length * speed;
		double vertical = directionY / length * speed;
		double traveled = 0.0;
		double height = startY;
		for (int tick = 0; tick < 400; tick++) {
			if (horizontal <= 0.0001) {
				return height;
			}
			if (traveled + horizontal >= distance) {
				double fraction = (distance - traveled) / horizontal;
				return height + vertical * fraction;
			}
			traveled += horizontal;
			height += vertical;
			horizontal *= 0.99;
			vertical = vertical * 0.99 - 0.05;
		}
		return height;
	}

	/** Same undo as the sword, so the arrow stays {@link #BOW_DAMAGE} before armor on every difficulty. */
	private static float bowDamage(ServerLevel level) {
		Difficulty difficulty = level.getDifficulty();
		if (difficulty == Difficulty.PEACEFUL) {
			return 0.0f;
		}
		if (difficulty == Difficulty.EASY) {
			return (BOW_DAMAGE - 1.0f) * 2.0f;
		}
		if (difficulty == Difficulty.HARD) {
			return BOW_DAMAGE * (2.0f / 3.0f);
		}
		return BOW_DAMAGE;
	}

	private static ItemStack enchanted(ItemStack stack, Holder<Enchantment> first, int firstLevel, Holder<Enchantment> second, int secondLevel) {
		EnchantmentHelper.updateEnchantments(stack, mutable -> {
			mutable.set(first, firstLevel);
			mutable.set(second, secondLevel);
		});
		return stack;
	}

	private void applyAttributes(boolean fillHealth) {
		this.setBase(Attributes.MAX_HEALTH, MAX_HEALTH);
		this.setBase(Attributes.MOVEMENT_SPEED, this.phase2 ? PHASE2_MOVE_SPEED : MOVE_SPEED);
		this.setBase(Attributes.FOLLOW_RANGE, PATH_RANGE);
		this.setBase(Attributes.KNOCKBACK_RESISTANCE, 1.0);
		this.setBase(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
		if (fillHealth) {
			this.setHealth(MAX_HEALTH);
		}
	}

	private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private void nameSelf() {
		this.setCustomName(Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false)));
		MobNames.showOnlyWhenLookedAt(this);
	}

	private Component nameTagName() {
		return Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false));
	}

	/**
	 * Easy and hard change mob hits. This undoes that, so the sword stays {@link #SWORD_DAMAGE}
	 * before armor on every difficulty.
	 */
	private static float swordDamage(ServerLevel level) {
		Difficulty difficulty = level.getDifficulty();
		if (difficulty == Difficulty.PEACEFUL) {
			return 0.0f;
		}
		if (difficulty == Difficulty.EASY) {
			return (SWORD_DAMAGE - 1.0f) * 2.0f;
		}
		if (difficulty == Difficulty.HARD) {
			return SWORD_DAMAGE * (2.0f / 3.0f);
		}
		return SWORD_DAMAGE;
	}

	/** Same undo, so a returned arrow is the hit he took, before the player's armor. */
	private static float reflectedAmount(ServerLevel level, float damage) {
		Difficulty difficulty = level.getDifficulty();
		if (difficulty == Difficulty.EASY) {
			return Math.max(0.0f, (damage - 1.0f) * 2.0f);
		}
		if (difficulty == Difficulty.HARD) {
			return damage * (2.0f / 3.0f);
		}
		return damage;
	}

	private static int breakTicks(float hardness) {
		if (hardness <= 0.8f) {
			return DIRT_BREAK_TICKS;
		}
		float span = Math.max(0.01f, OBSIDIAN_HARDNESS - STONE_HARDNESS);
		float along = Math.min(1.0f, Math.max(0.0f, (hardness - STONE_HARDNESS) / span));
		return Math.round(STONE_BREAK_TICKS + along * (OBSIDIAN_BREAK_TICKS - STONE_BREAK_TICKS));
	}

	private static double velocityForHeight(double blocks) {
		if (blocks <= 0.0) {
			return 0.0;
		}
		double low = 0.0;
		double high = 1.0;
		while (peakHeight(high) < blocks && high < 40.0) {
			high *= 2.0;
		}
		for (int i = 0; i < 24; i++) {
			double mid = (low + high) * 0.5;
			if (peakHeight(mid) < blocks) {
				low = mid;
			} else {
				high = mid;
			}
		}
		return (low + high) * 0.5;
	}

	private static double peakHeight(double vy) {
		double y = 0.0;
		for (int i = 0; i < 200 && vy > 0.0; i++) {
			y += vy;
			vy = (vy - GRAVITY_PER_TICK) * VERTICAL_DRAG;
		}
		return y;
	}

	private static int down(int ticks) {
		return ticks > 0 ? ticks - 1 : 0;
	}

	/**
	 * The zombie's own attack goal. The last argument {@code true} is the switch a normal zombie leaves off:
	 * keep building a path after the player leaves sight. Off, he walks to the last point on the path and stands there.
	 * Creative players stay a target too. The normal goal drops them.
	 */
	private static final class WhiteEyesAttackGoal extends ZombieAttackGoal {
		/** Ticks to wait before another search while a finished path leaves him standing. 10 = half a second. */
		private int retry = 10;

		private WhiteEyesAttackGoal(WhiteEyes eyes) {
			super(eyes, 1.0, true);
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.mob.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean canContinueToUse() {
			// Ending this goal is what makes him turn away and stand. He keeps it while the player is alive.
			LivingEntity target = this.mob.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void start() {
			super.start();
			// super.start walks a path saved by the normal canUse. This goal does not use that check,
			// so that path is empty and would clear the chase. Search for the player directly.
			LivingEntity target = this.mob.getTarget();
			if (target != null) {
				this.mob.getNavigation().moveTo(target, 0, 1.0);
			}
			this.retry = 10;
		}

		@Override
		public void tick() {
			super.tick();
			LivingEntity target = this.mob.getTarget();
			if (target == null) {
				return;
			}
			if (this.mob.isWithinMeleeAttackRange(target) && this.mob.getSensing().hasLineOfSight(target)) {
				return;
			}
			// The zombie goal does not search again while you stand still, so a finished path leaves him there.
			if (!this.mob.getNavigation().isDone()) {
				return;
			}
			if (this.retry-- > 0) {
				return;
			}
			this.retry = 10;
			this.mob.getNavigation().moveTo(target, 0, 1.0);
		}

		@Override
		public void stop() {
			this.mob.setAggressive(false);
			this.mob.getNavigation().stop();
		}
	}

	private final class HoldStillGoal extends Goal {
		private HoldStillGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return WhiteEyes.this.holdStill;
		}

		@Override
		public boolean canContinueToUse() {
			return WhiteEyes.this.holdStill;
		}

		@Override
		public void tick() {
			WhiteEyes.this.getNavigation().stop();
		}
	}
}
