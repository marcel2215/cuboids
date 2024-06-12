package com.marcel2215.cuboids;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class Cuboids implements ModInitializer {
    private final int CUBOID_RANGE = 30;

    private ServerBossBar _unauthorizedBossBar;
    private ServerBossBar _serverOwnedBossBar;
    private ServerBossBar _authorizedBossBar;


    @Override
    public void onInitialize() {
        _unauthorizedBossBar = new ServerBossBar(Text.literal("Cuboid").setStyle(Style.EMPTY.withColor(TextColor.fromFormatting(Formatting.RED)).withBold(true)), BossBar.Color.RED, BossBar.Style.PROGRESS);
        _serverOwnedBossBar = new ServerBossBar(Text.literal("Cuboid").setStyle(Style.EMPTY.withColor(TextColor.fromFormatting(Formatting.YELLOW)).withBold(true)), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        _authorizedBossBar = new ServerBossBar(Text.literal("Cuboid").setStyle(Style.EMPTY.withColor(TextColor.fromFormatting(Formatting.GREEN)).withBold(true)), BossBar.Color.GREEN, BossBar.Style.PROGRESS);

        ServerTickEvents.END_WORLD_TICK.register(this::onWorldTick);
        AttackEntityCallback.EVENT.register(this::onPlayerAttack);
        UseItemCallback.EVENT.register(this::onItemUse);
        UseBlockCallback.EVENT.register(this::onBlockUse);
        PlayerBlockBreakEvents.BEFORE.register(this::onBlockBreak);
    }

    private void onWorldTick(World world) {
        if (world.isClient()) {
            return;
        }

        for (PlayerEntity player : world.getPlayers()) {
            var pos = player.getBlockPos();
            var status = getCuboidStatus(world, pos, player, CUBOID_RANGE);

            var currentGameMode = ((ServerPlayerEntity) player).interactionManager.getGameMode();
            if (currentGameMode != GameMode.CREATIVE && currentGameMode != GameMode.SPECTATOR) {
                if (status == CuboidStatus.SERVER_OWNED || status == CuboidStatus.UNAUTHORIZED) {
                    ((ServerPlayerEntity) player).changeGameMode(GameMode.ADVENTURE);
                } else {
                    ((ServerPlayerEntity) player).changeGameMode(GameMode.SURVIVAL);
                }
            }

            if (status == CuboidStatus.SERVER_OWNED) {
                _unauthorizedBossBar.removePlayer((ServerPlayerEntity) player);
                _authorizedBossBar.removePlayer((ServerPlayerEntity) player);
                _serverOwnedBossBar.addPlayer((ServerPlayerEntity) player);

                ((ServerPlayerEntity) player).changeGameMode(GameMode.ADVENTURE);
            } else if (status == CuboidStatus.UNAUTHORIZED) {
                _serverOwnedBossBar.removePlayer((ServerPlayerEntity) player);
                _authorizedBossBar.removePlayer((ServerPlayerEntity) player);
                _unauthorizedBossBar.addPlayer((ServerPlayerEntity) player);
            } else if (status == CuboidStatus.AUTHORIZED) {
                _serverOwnedBossBar.removePlayer((ServerPlayerEntity) player);
                _unauthorizedBossBar.removePlayer((ServerPlayerEntity) player);
                _authorizedBossBar.addPlayer((ServerPlayerEntity) player);
            } else {
                _serverOwnedBossBar.removePlayer((ServerPlayerEntity) player);
                _unauthorizedBossBar.removePlayer((ServerPlayerEntity) player);
                _authorizedBossBar.removePlayer((ServerPlayerEntity) player);
            }
        }
    }

    private ActionResult onPlayerAttack(PlayerEntity player, World world, Hand ignoredHand, Entity entity, EntityHitResult ignoredHitResult) {
        if (player.isSpectator()) return ActionResult.PASS;
        if (entity instanceof PlayerEntity targetPlayer) {
            var pos = targetPlayer.getBlockPos();
            var status = getCuboidStatus(world, pos, player, CUBOID_RANGE);

            if (status == CuboidStatus.SERVER_OWNED) {
                player.sendMessage(Text.of("PVP Disabled Here"), true);
                return ActionResult.FAIL;
            }
        }

        return ActionResult.PASS;
    }

    private TypedActionResult<ItemStack> onItemUse(PlayerEntity playerEntity, World world, Hand hand) {
        var itemStack = playerEntity.getStackInHand(hand);
        var item = itemStack.getItem();

        if (item == Items.BUCKET || item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            var blockHitResult = (BlockHitResult) playerEntity.raycast(5.0D, 1.0F, true);
            if (blockHitResult.getType() == BlockHitResult.Type.BLOCK) {
                var status = getCuboidStatus(world, blockHitResult.getBlockPos(), playerEntity, CUBOID_RANGE);
                if (status == CuboidStatus.SERVER_OWNED || status == CuboidStatus.UNAUTHORIZED) {
                    playerEntity.sendMessage(Text.of("Protected by Cuboid"), true);
                    return new TypedActionResult<>(ActionResult.FAIL, itemStack);
                }
            }
        }

        return new TypedActionResult<>(ActionResult.PASS, itemStack);
    }

    private ActionResult onBlockUse(PlayerEntity playerEntity, World world, Hand hand, BlockHitResult blockHitResult) {
        var block = world.getBlockState(blockHitResult.getBlockPos()).getBlock();
        if (block != Blocks.DIAMOND_BLOCK) {
            var stackInHand = playerEntity.getStackInHand(hand);
            if ((stackInHand.isEmpty() || !playerEntity.isSneaking()) && isFunctionalBlock(block) && !isPrivateBlock(block)) {
                return ActionResult.PASS;
            }

            var status = getCuboidStatus(world, blockHitResult.getBlockPos(), playerEntity, CUBOID_RANGE);
            if (status == CuboidStatus.SERVER_OWNED || status == CuboidStatus.UNAUTHORIZED) {
                if ((stackInHand.isEmpty() || !playerEntity.isSneaking()) && isFunctionalBlock(block) && status == CuboidStatus.SERVER_OWNED) {
                    return ActionResult.PASS;
                }

                if (stackInHand.isOf(Items.TNT) && status != CuboidStatus.SERVER_OWNED) {
                    var tnt = new TntEntity(EntityType.TNT, world);
                    var side = blockHitResult.getSide();
                    var wherePlaced = blockHitResult.getBlockPos().offset(side);

                    tnt.updatePosition(wherePlaced.getX() + 0.5, wherePlaced.getY(), wherePlaced.getZ() + 0.5);
                    world.spawnEntity(tnt);

                    if (!playerEntity.isCreative()) {
                        stackInHand.decrement(1);
                        playerEntity.currentScreenHandler.syncState();
                    }

                    return ActionResult.SUCCESS;
                }

                if (!playerEntity.isCreative()) {
                    playerEntity.setStackInHand(hand, stackInHand);
                    playerEntity.currentScreenHandler.syncState();
                }

                playerEntity.sendMessage(Text.of("Protected by Cuboid"), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        }

        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        var markers = world.getEntitiesByType(EntityType.MARKER, new Box(blockHitResult.getBlockPos()).expand(1), e -> true);
        var marker = markers.stream().findFirst().orElse(null);

        if (marker == null) {
            var status = getCuboidStatus(world, blockHitResult.getBlockPos(), playerEntity, CUBOID_RANGE * 2);
            if (status == CuboidStatus.SERVER_OWNED || status == CuboidStatus.UNAUTHORIZED) {
                playerEntity.sendMessage(Text.of("Too Close to Unauthorized Cuboid"), true);
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
            var markers = getCuboidMarkers(world, pos, 1);
            for (var marker : markers) {
                marker.remove(Entity.RemovalReason.KILLED);
            }

            if (!markers.isEmpty()) {
                player.sendMessage(Text.of("Cuboid Removed"), true);
                return true;
            }
        }

        var status = getCuboidStatus(world, pos, player, CUBOID_RANGE);
        if (status == CuboidStatus.SERVER_OWNED || status == CuboidStatus.UNAUTHORIZED) {
            player.sendMessage(Text.of("Protected by Cuboid"), true);
            return false;
        }

        return true;
    }

    private List<MarkerEntity> getCuboidMarkers(World world, BlockPos pos, int range) {
        return world.getEntitiesByType(EntityType.MARKER, new Box(pos).expand(range), e -> {
            var tags = e.getCommandTags();
            return tags.contains("__type__cuboid");
        });
    }

    private CuboidStatus getCuboidStatus(World world, BlockPos pos, PlayerEntity player, int range) {
        var markers = getCuboidMarkers(world, pos, range);

        var isAuthorized = false;
        var isUnauthorized = false;

        for (var marker : markers) {
            var tags = marker.getCommandTags();
            var playerUUID = player.getUuidAsString();

            if (tags.contains("__authorized__" + playerUUID)) {
                isAuthorized = true;
            } else if (tags.contains("__server_owned")) {
                return CuboidStatus.SERVER_OWNED;
            } else {
                isUnauthorized = true;
            }
        }

        if (isUnauthorized) {
            return CuboidStatus.UNAUTHORIZED;
        } else if (isAuthorized) {
            return CuboidStatus.AUTHORIZED;
        }

        return CuboidStatus.NONE;
    }

    private boolean isFunctionalBlock(Block block) {
        if (isPrivateBlock(block)) {
            return true;
        }

        var functionalBlocks = new ArrayList<Block>();

        functionalBlocks.add(Blocks.ACACIA_BUTTON);
        functionalBlocks.add(Blocks.ANVIL);
        functionalBlocks.add(Blocks.BAMBOO_BUTTON);
        functionalBlocks.add(Blocks.BELL);
        functionalBlocks.add(Blocks.BIRCH_BUTTON);
        functionalBlocks.add(Blocks.BLACK_BED);
        functionalBlocks.add(Blocks.BLUE_BED);
        functionalBlocks.add(Blocks.BROWN_BED);
        functionalBlocks.add(Blocks.CAMPFIRE);
        functionalBlocks.add(Blocks.CARTOGRAPHY_TABLE);
        functionalBlocks.add(Blocks.CHERRY_BUTTON);
        functionalBlocks.add(Blocks.CHIPPED_ANVIL);
        functionalBlocks.add(Blocks.CRAFTING_TABLE);
        functionalBlocks.add(Blocks.CRIMSON_BUTTON);
        functionalBlocks.add(Blocks.CYAN_BED);
        functionalBlocks.add(Blocks.DAMAGED_ANVIL);
        functionalBlocks.add(Blocks.DARK_OAK_BUTTON);
        functionalBlocks.add(Blocks.ENCHANTING_TABLE);
        functionalBlocks.add(Blocks.ENDER_CHEST);
        functionalBlocks.add(Blocks.GRAY_BED);
        functionalBlocks.add(Blocks.GREEN_BED);
        functionalBlocks.add(Blocks.GRINDSTONE);
        functionalBlocks.add(Blocks.JUNGLE_BUTTON);
        functionalBlocks.add(Blocks.LIGHT_BLUE_BED);
        functionalBlocks.add(Blocks.LIGHT_GRAY_BED);
        functionalBlocks.add(Blocks.LIME_BED);
        functionalBlocks.add(Blocks.LODESTONE);
        functionalBlocks.add(Blocks.LOOM);
        functionalBlocks.add(Blocks.MAGENTA_BED);
        functionalBlocks.add(Blocks.MANGROVE_BUTTON);
        functionalBlocks.add(Blocks.OAK_BUTTON);
        functionalBlocks.add(Blocks.ORANGE_BED);
        functionalBlocks.add(Blocks.PINK_BED);
        functionalBlocks.add(Blocks.PURPLE_BED);
        functionalBlocks.add(Blocks.RED_BED);
        functionalBlocks.add(Blocks.SMITHING_TABLE);
        functionalBlocks.add(Blocks.SPRUCE_BUTTON);
        functionalBlocks.add(Blocks.STONECUTTER);
        functionalBlocks.add(Blocks.TRAPPED_CHEST);
        functionalBlocks.add(Blocks.WARPED_BUTTON);
        functionalBlocks.add(Blocks.WHITE_BED);
        functionalBlocks.add(Blocks.YELLOW_BED);

        return functionalBlocks.contains(block);
    }

    private boolean isPrivateBlock(Block block) {
        var isPrivateBlock = new ArrayList<Block>();

        isPrivateBlock.add(Blocks.BARREL);
        isPrivateBlock.add(Blocks.BEACON);
        isPrivateBlock.add(Blocks.BLAST_FURNACE);
        isPrivateBlock.add(Blocks.BREWING_STAND);
        isPrivateBlock.add(Blocks.CHEST);
        isPrivateBlock.add(Blocks.COMPARATOR);
        isPrivateBlock.add(Blocks.DAYLIGHT_DETECTOR);
        isPrivateBlock.add(Blocks.DAYLIGHT_DETECTOR);
        isPrivateBlock.add(Blocks.DISPENSER);
        isPrivateBlock.add(Blocks.DROPPER);
        isPrivateBlock.add(Blocks.FURNACE);
        isPrivateBlock.add(Blocks.HOPPER);
        isPrivateBlock.add(Blocks.LECTERN);
        isPrivateBlock.add(Blocks.LEVER);
        isPrivateBlock.add(Blocks.NOTE_BLOCK);
        isPrivateBlock.add(Blocks.REPEATER);
        isPrivateBlock.add(Blocks.SMOKER);
        isPrivateBlock.add(Blocks.STONE_BUTTON);
        isPrivateBlock.add(Blocks.TRAPPED_CHEST);

        return isPrivateBlock.contains(block);
    }
}
