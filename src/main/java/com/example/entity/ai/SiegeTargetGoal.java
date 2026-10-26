package com.example.entity.ai;

import com.example.siege.SiegeNight;
import com.example.siege.SiegeWaves;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Держит игрока целью у моба осады.
 *
 * Вешается на выбор цели (targetSelector), приоритет 0 — выше, чем «ближайший игрок» самой игры.
 * Иначе игра каждый тик забывает игрока: его не видно с 50–70 блоков, и моб стоит на месте.
 * Пока цель держится здесь, обычная драка и стрельба сами включаются, когда моб подойдёт близко.
 *
 * На обычных мобов не действует: срабатывает только с меткой осады и только пока идёт осада.
 */
public class SiegeTargetGoal extends Goal {

	private final Mob mob;

	public SiegeTargetGoal(Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (!SiegeWaves.isSiegeMob(mob) || !SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		return SiegeWaves.nearestHuntTarget(mob) != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		if (player != null && mob.getTarget() != player) {
			mob.setTarget(player);
		}
	}

	@Override
	public void stop() {
		if (mob.getTarget() instanceof ServerPlayer) {
			mob.setTarget(null);
		}
	}
}
