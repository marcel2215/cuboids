package com.marcel2215.cuboids.mixins;

import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonHandler.class)
public class PistonHandlerMixin {

    @Mutable @Final @Shadow private World world;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void captureWorldReference(World world, BlockPos pos, Direction dir, boolean retracted, CallbackInfo ci) {
        this.world = world;
    }

    @Inject(method = "tryMove", at = @At(value = "HEAD"), cancellable = true)
    private void onTryMove(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        var block = world.getBlockState(pos).getBlock();
        if (block == Blocks.DIAMOND_BLOCK) {
            var markers = world.getEntitiesByType(EntityType.MARKER, new Box(pos).expand(1), e-> true);
            markers.stream().findFirst().ifPresent(marker -> cir.setReturnValue(false));
        }
    }
}
