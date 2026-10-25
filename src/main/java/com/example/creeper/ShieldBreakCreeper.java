package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import com.example.entity.ShieldBreakCreeperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Редкий крипер, который ведёт себя как обычный: подбегает и взрывается с обычным запалом и силой.
 * Если игрок закрылся щитом и взрыв в щит попал — щит уничтожается целиком, урон по игроку не проходит.
 * Прочность и зачарование «Прочность» щит не спасают: ломается всегда с одного такого взрыва.
 * Выглядит как ванильный крипер.
 */
public final class ShieldBreakCreeper {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие криперы не появляются, команда тоже не делает ломателя. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) при спавне обычного крипера стать ломателем щита. 0.05 = 5%.
	 * Как у подрывника и скелета с тыквой-маяком. Если уже выпал подрывник или метатель — этот шанс не бросается.
	 * Заряженный крипер ломателем щита быть может.
	 * Команда спавна шанс не использует.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	// ========================================================================

	private ShieldBreakCreeper() {
	}

	public static boolean isShieldBreak(Creeper creeper) {
		return ENABLED
				&& creeper instanceof ShieldBreakCreeperEntity data
				&& data.evolvingthreats$isShieldBreakCreeper();
	}

	/** Вызвать из finalizeSpawn. Командный спавн ставит флаг заранее. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof ShieldBreakCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isShieldBreakCreeper()) {
			return;
		}
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		apply(creeper);
	}

	/** Команда: ломатель щита перед игроком. */
	public static Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create shield-break creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof ShieldBreakCreeperEntity data) {
			data.evolvingthreats$setShieldBreakCreeper(true);
		}
		creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(creeper.blockPosition()), EntitySpawnReason.COMMAND, null);
		apply(creeper);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static void apply(Creeper creeper) {
		if (creeper instanceof ShieldBreakCreeperEntity data) {
			data.evolvingthreats$setShieldBreakCreeper(true);
		}
	}

	/**
	 * Уничтожить щит в руке, которой игрок закрылся.
	 * Урон взрыва сюда не добавляется: игра уже посчитала его заблокированным.
	 * Если закрылся не щитом или взрыв в щит не попал — этот метод не вызывается.
	 */
	public static void breakBlockingShield(Player player) {
		if (!(player.level() instanceof ServerLevel)) {
			return;
		}
		InteractionHand hand = player.getUsedItemHand();
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.SHIELD)) {
			return;
		}
		ItemStack broken = stack.copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		player.onEquippedItemBroken(broken, hand.asEquipmentSlot());
		player.stopUsingItem();
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
