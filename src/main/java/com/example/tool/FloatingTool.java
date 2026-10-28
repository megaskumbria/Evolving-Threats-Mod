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
 * Враждебный парящий инструмент: меч, топор, лопата, кирка или мотыга.
 * Летит к игроку и бьёт вблизи. В обычном мире незерита нет — набор тот же, что у зомби ({@link RandomWeapons}).
 * В аду инструмент всегда незеритовый и с Заговором огня III, сам предмет не выпадает.
 * Чары в обычном мире те же, что на оружии зомби ({@link MobEnchantments}): острота и огненный аспект бьют,
 * эффективность и удача только украшают выпавший предмет.
 * Зелья те же, что у зомби ({@link MobPotionBuffs}): от нуля до пяти, пока моб жив.
 * Невидимости нет: она прятала бы сам предмет, тела у этого моба нет.
 *
 * <p>Отдельной модели и анимации нет: на экране рисуется обычная модель предмета
 * (см. клиентский рендерер). Здесь только «тело», полёт и удар.
 * В обычном мире ночью появляется сам, на месте обычного враждебного моба.
 * Шанс — {@link #NATURAL_SPAWN_CHANCE}. В аду — {@link #NETHER_SPAWN_CHANCE}, в любое время.
 * Команда {@code /ettool} шанс не бросает: в аду она тоже даёт незерит.
 */
public class FloatingTool extends Monster {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Шанс (0.0–1.0), что вместо обычного ночного монстра появится этот инструмент.
	 * 0.05 = 5%. Общий бросок с повреждённым големом: сначала этот шанс, потом шанс голема (1%).
	 * 0.05 и 0.01 вместе = 6% особых мобов, они не занимают одно и то же появление.
	 * Обычный мир, не ад и не Энд. Ближе 24 блоков игра не ставит, как любых монстров.
	 * Команда /ettool этот шанс не бросает. 0 = в мире сам не появляется.
	 */
	public static final float NATURAL_SPAWN_CHANCE = 0.05f;

	/**
	 * Шанс (0.0–1.0), что вместо враждебного моба в аду появится незеритовый инструмент.
	 * 0.05 = 5%. ФИКС: с первого дня, от шкалы угрозы не растёт.
	 * Ад не ждёт ночи: монстры там появляются и днём. 0 = в аду их нет.
	 * Обычный мир этот шанс не использует.
	 */
	public static final float NETHER_SPAWN_CHANCE = 0.05f;

	/**
	 * Заговор огня на адском инструменте. 3 = III. У стола зачарований потолок II.
	 * Каждый уровень дольше держит игрока в огне. 2 = обычный Заговор огня II. 0 = без огня.
	 */
	public static final int NETHER_FIRE_ASPECT_LEVEL = 3;

	/**
	 * Шанс выпадения адского инструмента. 0 = не падает никогда, Добыча это не меняет:
	 * игра прибавляет Добычу только если шанс уже не ноль.
	 * 1 = падает всегда. Обычный мир смотрит на {@link #DROP_CHANCE}, не на это число.
	 */
	public static final float NETHER_DROP_CHANCE = 0.0f;

	/**
	 * Невидимое тело, по которому нужно попасть (ширина и высота в блоках).
	 * Сам инструмент рисуется отдельно и может быть крупнее этой коробки.
	 * 0.75 — примерно как блок, по нему реально попасть. Меньше — труднее.
	 */
	public static final float HITBOX_WIDTH = 0.75f;
	public static final float HITBOX_HEIGHT = 0.75f;

	/**
	 * Насколько крупно рисуется предмет. 1.0 = как брошенная на землю вещь.
	 * 1.6 = заметный парящий инструмент. На хитбокс и урон не влияет.
	 */
	public static final float VISUAL_SCALE = 1.6f;

	/**
	 * Куда смотрит картинка, пока инструмент атакует, в градусах.
	 * 0 = туда же, куда повёрнут сам моб (на игрока). Вокруг своей оси в этот момент не крутится.
	 * 90 или 180 — если лезвие смотрит вбок или назад.
	 * Когда никого не атакует, предмет снова крутится, как брошенная вещь.
	 */
	public static final float ATTACK_FACING_OFFSET = 0.0f;

	/**
	 * «Кулак» моба, в сердечках урона это половина числа (1.0 = полсердца).
	 * Урон самого инструмента прибавляется сверху: деревянный меч слабее алмазного,
	 * мотыга слабее меча. Не ставь сюда 5–10 — иначе даже мотыга будет бить как меч.
	 */
	public static final double BASE_ATTACK_DAMAGE = 1.0;

	/**
	 * Здоровье по материалу (2.0 = 1 сердце).
	 * Дерево и золото слабые, алмаз живёт дольше.
	 */
	public static final double HEALTH_WOOD_GOLD = 12.0;
	public static final double HEALTH_STONE_COPPER = 16.0;
	public static final double HEALTH_IRON = 20.0;
	public static final double HEALTH_DIAMOND = 28.0;

	/** Незерит в аду. 36 = 18 сердец. Алмазный инструмент — 28 (14 сердец). */
	public static final double HEALTH_NETHERITE = 36.0;

	/**
	 * Как быстро летит, когда никого не догоняет. Больше — резвее.
	 * Вместе с {@link #CHASE_SPEED}: 0.9 и 1.2 — догоняет бегущего игрока, но не телепортируется.
	 */
	public static final double FLYING_SPEED = 0.9;

	/** Множитель скорости именно в погоне за игроком. 1.0 = как обычный полёт, 1.6 = заметно злее. */
	public static final double CHASE_SPEED = 1.2;

	/** С какой дистанции (в блоках) замечает игрока. 24 = три чанка по горизонтали не нужно, но через двор видит. */
	public static final double FOLLOW_RANGE = 24.0;

	/**
	 * Пауза между ударами, в тиках. 20 = раз в секунду, пока инструмент вплотную.
	 * 40 = раз в две секунды, бить легче.
	 */
	public static final int ATTACK_COOLDOWN_TICKS = 20;

	/**
	 * Шанс (0.0–1.0), что инструмент будет с чарами. 0.30 = 30%, как у оружия зомби.
	 * Какие чары и какой уровень — в {@link MobEnchantments}. 0 = всегда без чар.
	 */
	public static final float ENCHANT_CHANCE = 0.30f;

	/**
	 * Шанс (0.0–1.0), что после смерти выпадет сам инструмент, вместе с чарами.
	 * 0.15 = 15%. 0 = ничего не падает. 1 = падает всегда.
	 */
	public static final float DROP_CHANCE = 0.15f;

	/**
	 * Опыт до общего множителя. 5 — как у обычного монстра.
	 * Потом {@link com.example.MobExperience} удваивает: с моба падает 10.
	 * Множитель выключен или стоит 1 — упадёт ровно это число.
	 */
	public static final int EXPERIENCE_REWARD = 5;

	/** Как часто сыпятся частицы зачарования, в тиках. 5 = четыре раза в секунду. Больше — реже. */
	public static final int PARTICLE_INTERVAL_TICKS = 5;

	/** Сколько частиц за один раз. 2 — лёгкое свечение. 8 — уже облако. */
	public static final int PARTICLE_COUNT = 2;

	/**
	 * Громкость тихого звука стола зачарований. 0.35 = негромко. 0 = выключить этот звук.
	 * Звук удара и смерти от этого числа не зависят.
	 */
	public static final float AMBIENT_VOLUME = 0.35f;

	// ========================================================================

	public static EntityType<FloatingTool> TYPE;

	/**
	 * true, пока есть цель. Клиент по этому флагу перестаёт крутить картинку.
	 * Сама цель на клиент не приходит, поэтому флаг шлётся отдельно.
	 */
	private static final EntityDataAccessor<Boolean> DATA_ATTACKING = SynchedEntityData.defineId(
			FloatingTool.class, EntityDataSerializers.BOOLEAN);

	public FloatingTool(EntityType<? extends FloatingTool> type, Level level) {
		super(type, level);
		this.xpReward = EXPERIENCE_REWARD;
		// hoversInPlace = true: когда цель потеряна, не падает на землю.
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
	 * Призыв командой. Появляется в воздухе и сразу смотрит на цель, если она передана.
	 * Предмет, чары и зелья выбираются внутри {@link #finalizeSpawn}.
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
			// Сразу, чтобы картинка не крутанулась в первый миг после /ettool.
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

	/** true — гонится за целью, картинка не крутится вокруг своей оси. */
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
		// Без гравитации висит в воздухе, даже когда никуда не летит.
		this.setNoGravity(true);
		super.aiStep();
		repairTool();
		// Свои частицы зачарования. Под невидимостью их нет, иначе зелье ничего не прячет.
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
			// Тот же случайный меч/топор/лопата/кирка/мотыга, что у зомби. Незерита в списке нет.
			RandomWeapons.equipMeleeOnly(this);
			MobEnchantments.rollHeldWeapons(this, ENCHANT_CHANCE);
			this.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);
		}
		applyHealthForTool();
		// Те же зелья, что у зомби и скелета: сколько штук и какие — в MobPotionBuffs.
		// Невидимость этому мобу не выдаётся: см. проверку в tryApply.
		MobPotionBuffs.tryApply(this);
		return data;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// Копия до удара: если прочность кончится прямо в ударе, слот опустеет и моб «исчезнет».
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

	/** Инструмент в бою не тратит прочность, иначе через пару минут моб останется без картинки. */
	private void repairTool() {
		ItemStack tool = this.getMainHandItem();
		if (!tool.isEmpty() && tool.isDamaged()) {
			tool.setDamageValue(0);
		}
	}

	/**
	 * Ад: незеритовый меч, топор, лопата, кирка или мотыга, поровну.
	 * Заговор огня III. Шанс выпадения 0 — Добыча к нулю ничего не прибавляет.
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
	 * Здоровье по имени предмета: diamond_sword, iron_pickaxe и т.д.
	 * Незнакомый материал (если добавишь его в RandomWeapons и забудешь сюда) получает здоровье дерева.
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
		// Летает, урон от падения не нужен.
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
