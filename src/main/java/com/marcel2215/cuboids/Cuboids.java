package com.marcel2215.cuboids;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;

public class Cuboids implements ModInitializer {
    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register(this::onBlockUse);
        PlayerBlockBreakEvents.BEFORE.register(this::onBlockBreak);
        UseItemCallback.EVENT.register(this::onItemUse);
    }

    private ActionResult onBlockUse(PlayerEntity playerEntity, World world, Hand hand, BlockHitResult blockHitResult) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        var block = world.getBlockState(blockHitResult.getBlockPos()).getBlock();
        if (block != Blocks.DIAMOND_BLOCK) {
            var stackInHand = playerEntity.getStackInHand(hand);
            if ((stackInHand.isEmpty() || !playerEntity.isSneaking()) && isUsableByEveryone(block)) {
                return ActionResult.PASS;
            }

            if (isBuildingBlocked(world, blockHitResult.getBlockPos(), playerEntity, 30)) {
                if (!playerEntity.isCreative()) {
                    playerEntity.setStackInHand(hand, stackInHand);
                    playerEntity.currentScreenHandler.syncState();
                }

                playerEntity.sendMessage(Text.of("Protected by Cuboid"), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        }

        var markers = world.getEntitiesByType(EntityType.MARKER, new Box(blockHitResult.getBlockPos()).expand(1), e -> true);
        var marker = markers.stream().findFirst().orElse(null);

        if (marker == null) {
            if (isBuildingBlocked(world, blockHitResult.getBlockPos(), playerEntity, 60)) {
                playerEntity.sendMessage(Text.of("Too Close to Enemy Cuboid"), true);
                return ActionResult.FAIL;
            }

            marker = new MarkerEntity(EntityType.MARKER, world);
            marker.updatePosition(blockHitResult.getBlockPos().getX() + 0.5, blockHitResult.getBlockPos().getY(), blockHitResult.getBlockPos().getZ() + 0.5);
            marker.addCommandTag("__type__cuboid");
            world.spawnEntity(marker);
        }

        var tags = marker.getCommandTags();
        var playerUUID = playerEntity.getUuidAsString();

        if (tags.contains("__authorized__" + playerUUID)) {
            marker.removeCommandTag("__authorized__" + playerUUID);
            playerEntity.sendMessage(Text.of("Deauthorized"), true);
        } else {
            marker.addCommandTag("__authorized__" + playerUUID);
            playerEntity.sendMessage(Text.of("Authorized"), true);
        }

        return ActionResult.SUCCESS;
    }

    private boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (state.getBlock() == Blocks.DIAMOND_BLOCK) {
            var markers = world.getEntitiesByType(EntityType.MARKER, new Box(pos).expand(1), e -> {
                var tags = e.getCommandTags();
                return tags.contains("__type__cuboid");
            });

            var marker = markers.stream().findFirst().orElse(null);
            if (marker != null) {
                marker.remove(Entity.RemovalReason.KILLED);
                player.sendMessage(Text.of("Cuboid Removed"), true);

                return true;
            }
        }

        if (isBuildingBlocked(world, pos, player, 30)) {
            player.sendMessage(Text.of("Protected by Cuboid"), true);
            return false;
        }

        return true;
    }

    private boolean isBuildingBlocked(World world, BlockPos pos, PlayerEntity player, int range) {
        var markers = world.getEntitiesByType(EntityType.MARKER, new Box(pos).expand(range), e -> {
            var tags = e.getCommandTags();
            return tags.contains("__type__cuboid");
        });

        for (var marker : markers) {
            var tags = marker.getCommandTags();
            var playerUUID = player.getUuidAsString();

            if (!tags.contains("__authorized__" + playerUUID)) {
                return true;
            }
        }

        return false;
    }

    private TypedActionResult<ItemStack> onItemUse(PlayerEntity playerEntity, World world, Hand hand) {
        var itemStack = playerEntity.getStackInHand(hand);
        var item = itemStack.getItem();

        if (item == Items.BUCKET || item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            var blockHitResult = (BlockHitResult) playerEntity.raycast(5.0D, 1.0F, true);
            if (blockHitResult.getType() == BlockHitResult.Type.BLOCK) {
                if (isBuildingBlocked(world, blockHitResult.getBlockPos(), playerEntity, 30)) {
                    playerEntity.sendMessage(Text.of("Protected by Cuboid"), true);
                    return new TypedActionResult<>(ActionResult.FAIL, itemStack);
                }
            }
        }

        return new TypedActionResult<>(ActionResult.PASS, itemStack);
    }

    private boolean isUsableByEveryone(Block block) {
        var publicBlocks = new ArrayList<Block>();

        publicBlocks.add(Blocks.CRAFTING_TABLE);
        publicBlocks.add(Blocks.ENCHANTING_TABLE);
        publicBlocks.add(Blocks.ANVIL);
        publicBlocks.add(Blocks.CHIPPED_ANVIL);
        publicBlocks.add(Blocks.DAMAGED_ANVIL);
        publicBlocks.add(Blocks.LOOM);
        publicBlocks.add(Blocks.LODESTONE);
        publicBlocks.add(Blocks.GRINDSTONE);
        publicBlocks.add(Blocks.SMITHING_TABLE);
        publicBlocks.add(Blocks.CARTOGRAPHY_TABLE);
        publicBlocks.add(Blocks.STONECUTTER);
        publicBlocks.add(Blocks.CAMPFIRE);
        publicBlocks.add(Blocks.ENDER_CHEST);
        publicBlocks.add(Blocks.BELL);
        publicBlocks.add(Blocks.BLACK_BED);
        publicBlocks.add(Blocks.BLUE_BED);
        publicBlocks.add(Blocks.BROWN_BED);
        publicBlocks.add(Blocks.CYAN_BED);
        publicBlocks.add(Blocks.GRAY_BED);
        publicBlocks.add(Blocks.GREEN_BED);
        publicBlocks.add(Blocks.LIGHT_BLUE_BED);
        publicBlocks.add(Blocks.LIGHT_GRAY_BED);
        publicBlocks.add(Blocks.LIME_BED);
        publicBlocks.add(Blocks.MAGENTA_BED);
        publicBlocks.add(Blocks.ORANGE_BED);
        publicBlocks.add(Blocks.PINK_BED);
        publicBlocks.add(Blocks.PURPLE_BED);
        publicBlocks.add(Blocks.RED_BED);
        publicBlocks.add(Blocks.WHITE_BED);
        publicBlocks.add(Blocks.YELLOW_BED);

        return publicBlocks.contains(block);
    }
}
