package com.marcel2215.cuboids.mixins;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public class ExplosionMixin {
    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void onAffectWorld(boolean bl, CallbackInfo ci) {
        var explosion = (Explosion) (Object) this;
        var entity = explosion.getEntity();

        if (entity == null) {
            return;
        }

        var world = entity.getWorld();
        for (BlockPos pos : explosion.getAffectedBlocks()) {
            var serverMarkers = world.getEntitiesByType(EntityType.MARKER, new Box(pos).expand(100), e -> {
                var tags = e.getCommandTags();
                return tags.contains("__type__cuboid") && tags.contains("__server_owned");
            });

            if (!serverMarkers.isEmpty()) {
                explosion.clearAffectedBlocks();
                return;
            }

            var block = world.getBlockState(pos).getBlock();
            if (block == Blocks.DIAMOND_BLOCK) {
                var markers = world.getEntitiesByType(EntityType.MARKER, new Box(pos).expand(2), e -> {
                    var tags = e.getCommandTags();
                    return tags.contains("__type__cuboid");
                });

                for (var marker : markers) {
                    marker.remove(Entity.RemovalReason.KILLED);
                }
            }
        }
    }
}
