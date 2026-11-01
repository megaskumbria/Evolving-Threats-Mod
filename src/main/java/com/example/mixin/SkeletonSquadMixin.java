package com.example.mixin;

import com.example.MobNames;
import com.example.entity.SkeletonSquadMember;
import com.example.skeleton.SkeletonCaptain;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 An ordinary skeleton remembers whether he is a captain or an assistant, and who else is in the squad.
 Record the world here, because the preservation is in Skeleton, not a common ancestor.
 */
@Mixin(Skeleton.class)
public class SkeletonSquadMixin implements SkeletonSquadMember {

	@Unique
	private int evolvingthreats$squadRole;

	@Unique
	@Nullable
	private UUID evolvingthreats$squadId;

	@Unique
	private final List<UUID> evolvingthreats$squadMembers = new ArrayList<>();

	@Unique
	@Nullable
	private UUID evolvingthreats$huntTargetId;

	@Override
	public int evolvingthreats$getSquadRole() {
		return evolvingthreats$squadRole;
	}

	@Override
	public void evolvingthreats$setSquadRole(int role) {
		this.evolvingthreats$squadRole = role;
	}

	@Override
	public @Nullable UUID evolvingthreats$getSquadId() {
		return evolvingthreats$squadId;
	}

	@Override
	public void evolvingthreats$setSquadId(@Nullable UUID id) {
		this.evolvingthreats$squadId = id;
	}

	@Override
	public List<UUID> evolvingthreats$getSquadMembers() {
		return evolvingthreats$squadMembers;
	}

	@Override
	public void evolvingthreats$setSquadMembers(List<UUID> members) {
		evolvingthreats$squadMembers.clear();
		evolvingthreats$squadMembers.addAll(members);
	}

	@Override
	public @Nullable UUID evolvingthreats$getHuntTargetId() {
		return evolvingthreats$huntTargetId;
	}

	@Override
	public void evolvingthreats$setHuntTargetId(@Nullable UUID id) {
		this.evolvingthreats$huntTargetId = id;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveSquad(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$squadRole == 0) {
			return;
		}
		output.putInt("EvolvingThreatsSquadRole", evolvingthreats$squadRole);
		if (evolvingthreats$squadId != null) {
			output.putString("EvolvingThreatsSquadId", evolvingthreats$squadId.toString());
		}
		if (!evolvingthreats$squadMembers.isEmpty()) {
			StringBuilder packed = new StringBuilder();
			for (UUID id : evolvingthreats$squadMembers) {
				if (!packed.isEmpty()) {
					packed.append(',');
				}
				packed.append(id);
			}
			output.putString("EvolvingThreatsSquadMembers", packed.toString());
		}
		if (evolvingthreats$huntTargetId != null) {
			output.putString("EvolvingThreatsHuntTarget", evolvingthreats$huntTargetId.toString());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadSquad(ValueInput input, CallbackInfo ci) {
		evolvingthreats$squadRole = input.getIntOr("EvolvingThreatsSquadRole", 0);
		evolvingthreats$squadId = null;
		input.getString("EvolvingThreatsSquadId").ifPresent(raw -> evolvingthreats$squadId = UUID.fromString(raw));
		evolvingthreats$huntTargetId = null;
		input.getString("EvolvingThreatsHuntTarget").ifPresent(raw -> evolvingthreats$huntTargetId = UUID.fromString(raw));
		evolvingthreats$squadMembers.clear();
		input.getString("EvolvingThreatsSquadMembers").ifPresent(raw -> {
			for (String part : raw.split(",")) {
				if (!part.isBlank()) {
					evolvingthreats$squadMembers.add(UUID.fromString(part));
				}
			}
		});
		if (evolvingthreats$squadRole == SkeletonCaptain.ROLE_CAPTAIN) {
			MobNames.showOnlyWhenLookedAt((Skeleton) (Object) this);
		}
	}
}
