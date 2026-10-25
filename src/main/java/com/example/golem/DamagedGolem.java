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
 * Железный голем, который сразу враждебен к игроку.
 * Выглядит потресканным и с чёрными глазами, но здоровье полное.
 * Глаза нарисованы в текстуре {@code textures/entity/damaged_golem.png}.
 * Сам появляется ночью в обычном мире, шанс — {@link #NATURAL_SPAWN_CHANCE}.
 */
public class DamagedGolem extends IronGolem implements Enemy {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Шанс (0.0–1.0), что вместо обычного ночного монстра появится этот голем.
	 * 0.05 = 5%. Бросок общий с парящим инструментом: сначала шанс инструмента, потом этот.
	 * Если в этом месте голему не встать (низкий потолок), появится обычный моб, попытка не пропадает.
	 * Обычный мир, не ад и не Энд. Ближе 24 блоков игра не ставит.
	 * Команда /etgolem этот шанс не бросает. 0 = в мире сам не появляется.
	 */
	public static final float NATURAL_SPAWN_CHANCE = 0.05f;

	/**
	 * Здоровье. 100 = 50 сердец, как у обычного железного голема.
	 * Трещины от этого числа не зависят: он выглядит повреждённым даже на полном здоровье.
	 */
	public static final double MAX_HEALTH = 100.0;

	/**
	 * Сила удара, как у обычного голема. Сам удар ещё кидает случайное число,
	 * поэтому по игроку прилетает примерно от половины этого значения до чуть больше него.
	 */
	public static final double ATTACK_DAMAGE = 15.0;

	/** С какой дистанции (в блоках) замечает игрока. 24 — через двор уже идёт. */
	public static final double FOLLOW_RANGE = 24.0;

	/**
	 * Насколько потрескан с виду. На здоровье не влияет.
	 * 3 = сильно, как будто почти развалился. 2 = средне. 1 = чуть-чуть. 0 = целый.
	 */
	public static final int CRACK_LEVEL = 3;

	/** Опыт за убийство. 10 — больше, чем у зомби: голем толстый. */
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
	 * Влезет ли голем ростом 2.7 блока. Ноги на {@code feet}, под ногами твёрдый блок, тело ни во что не упирается.
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

	/** Призыв командой. Шанс ночного спавна не бросается. Сразу идёт на цель, если она есть. */
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
	 * Зомби, скелеты, пауки и разбойники ищут железного голема и идут его бить.
	 * Этот тоже железный, поэтому без отказа они агрились бы и на него.
	 * Здесь он для них не цель. Обычный деревенский голем по-прежнему их злит.
	 * Сам повреждённый голем игрока бить не перестаёт.
	 */
	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	/** Всегда выглядит повреждённым. Полоска здоровья при этом полная, пока его не побили. */
	@Override
	public Crackiness.Level getCrackiness() {
		return switch (CRACK_LEVEL) {
			case 1 -> Crackiness.Level.LOW;
			case 2 -> Crackiness.Level.MEDIUM;
			case 3 -> Crackiness.Level.HIGH;
			default -> Crackiness.Level.NONE;
		};
	}

	/** Железный слиток его не чинит: он не деревенский страж, а враг. */
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
