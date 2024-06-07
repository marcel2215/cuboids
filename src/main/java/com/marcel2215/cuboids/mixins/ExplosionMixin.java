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
        var causingEntity = explosion.getCausingEntity();

        if (causingEntity == null) {
            return;
        }

        var world = causingEntity.getWorld();
        for (BlockPos pos : explosion.getAffectedBlocks()) {
            var block = world.getBlockState(pos).getBlock();
            if (block == Blocks.DIAMOND_BLOCK) {
                var markers = world.getEntitiesByType(EntityType.MARKER, new Box(pos).expand(1), e-> true);
                markers.stream().findFirst().ifPresent(marker -> marker.remove(Entity.RemovalReason.KILLED));
            }
        }
    }
}
