package com.upo.createeggproduction.content.block_entities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import java.util.ArrayList;
import java.util.List;

public class IncubatorBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int INVENTORY_SLOTS = 16;
    public static final int HATCH_TIME_TICKS = 300;
    public static final TagKey<Item> EGG_TAG = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "eggs"));

    protected final ItemStackHandler itemHandler = createItemHandler();
    protected final List<Integer> hatchingTimers = new ArrayList<>(INVENTORY_SLOTS);

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INCUBATOR_BE.get(), pos, state);
        for (int i = 0; i < INVENTORY_SLOTS; i++) hatchingTimers.add(-1);
    }
    @NotNull
    private ItemStackHandler createItemHandler() {
        return new ItemStackHandler(INVENTORY_SLOTS) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return stack.is(EGG_TAG);
            }
            @Override
            protected void onContentsChanged(int slot) {
                if (level != null && !level.isClientSide()) {
                    setChanged();
                    BlockState currentState = getBlockState();
                    level.sendBlockUpdated(worldPosition, currentState, currentState, Block.UPDATE_CLIENTS);
                }
            }
            @Override
            public int getSlotLimit(int slot) { return 1; }
        };
    }

    public ItemStackHandler getInventory() { return itemHandler; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (level.isClientSide()) return;
        boolean changed = false;
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            ItemStack stack = be.itemHandler.getStackInSlot(i);
            int timer = be.hatchingTimers.get(i);
            if (stack.is(EGG_TAG)) {
                BlockState belowState = level.getBlockState(pos.below());
                HeatLevel heat = BlazeBurnerBlock.getHeatLevelOf(belowState);
                boolean canHatch = heat != HeatLevel.NONE;
                if (timer == -1) {
                    if (canHatch) {
                        be.hatchingTimers.set(i, HATCH_TIME_TICKS);
                        changed = true;
                    }
                } else if (timer > 0) {
                    if (canHatch) {
                        timer--;
                        be.hatchingTimers.set(i, timer);
                        if (timer == 0) {
                            HeatLevel finalHeat = BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(pos.below()));
                            BlockPos spawnPos = pos.relative(state.getValue(HorizontalDirectionalBlock.FACING));
                            be.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                            if (finalHeat == HeatLevel.SEETHING) {
                                spawnItemEntity(level, spawnPos, new ItemStack(Items.CHARCOAL));
                                spawnItemEntity(level, spawnPos, new ItemStack(Items.BONE, level.random.nextInt(0, 2)));
                                level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.5F, 1.5F);
                            } else if (finalHeat.isAtLeast(HeatLevel.KINDLED)) {
                                spawnItemEntity(level, spawnPos, new ItemStack(Items.COOKED_CHICKEN));
                                spawnItemEntity(level, spawnPos, new ItemStack(Items.FEATHER, level.random.nextInt(1, 3)));
                                level.playSound(null, pos, SoundEvents.CHICKEN_HURT, SoundSource.BLOCKS, 0.5F, 1.0F);
                            } else {
                                Chicken chicken = EntityType.CHICKEN.create(level);
                                if (chicken != null) {
                                    chicken.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                                    level.addFreshEntity(chicken);
                                    level.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.5F, 1.0F);
                                }
                            }
                            be.hatchingTimers.set(i, -1);
                            changed = true;
                            level.updateNeighbourForOutputSignal(pos, state.getBlock());
                        }
                    }
                }
            } else if (timer != -1) {
                be.hatchingTimers.set(i, -1);
                changed = true;
            }
        }
        if (changed) {
            be.setChanged();
        }
    }
    private static void spawnItemEntity(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || level == null) return;
        double x = pos.getX() + 0.5;
        double y = pos.getY() - 0.1;
        double z = pos.getZ() + 0.5;
        double motionX = (level.random.nextDouble() - 0.5) * 0.05;
        double motionY = 0.1 + level.random.nextDouble() * 0.05;
        double motionZ = (level.random.nextDouble() - 0.5) * 0.05;
        ItemEntity itemEntity = new ItemEntity(level, x, y, z, stack.copy());
        itemEntity.setDeltaMovement(motionX, motionY, motionZ);
        level.addFreshEntity(itemEntity);
    }
    public ItemHandlerWrapper getItemHandlerCapability(@Nullable Direction side) {
        return new ItemHandlerWrapper(itemHandler, side != Direction.DOWN, false);
    }
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", itemHandler.serializeNBT(registries));
        tag.put("Timers", new IntArrayTag(hatchingTimers.stream().mapToInt(Integer::intValue).toArray()));
    }
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        int hashBefore = itemHandler.hashCode();

        if (tag.contains("Inventory", CompoundTag.TAG_COMPOUND)) {
            this.itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        }

        hatchingTimers.clear();
        if (tag.contains("Timers", CompoundTag.TAG_INT_ARRAY)) {
            int[] loadedTimers = tag.getIntArray("Timers");
            for (int i = 0; i < INVENTORY_SLOTS; i++) hatchingTimers.add(i < loadedTimers.length ? loadedTimers[i] : -1);
        } else {
            for (int i = 0; i < INVENTORY_SLOTS; i++) hatchingTimers.add(-1);
        }
        int hashAfter = itemHandler.hashCode();
        StringBuilder invState = new StringBuilder("[");
        if(invState.length() > 1) invState.delete(invState.length()-2, invState.length());
        else invState.append("EMPTY");
        invState.append("]");
    }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag == null) return;
        loadAdditional(tag, registries);
        if (level != null && level.isClientSide()) {
            LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
            levelRenderer.setSectionDirty(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        }
    }
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            Minecraft.getInstance().levelRenderer.setSectionDirty(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        }
    }
    @Override
    public void setRemoved() {
        super.setRemoved();
    }
}
class ItemHandlerWrapper implements IItemHandler {
    private final IItemHandler internal;
    private final boolean allowInsert;
    private final boolean allowExtract;

    public ItemHandlerWrapper(IItemHandler internal, boolean allowInsert, boolean allowExtract) {
        this.internal = internal;
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
    }
    @Override public int getSlots() { return internal.getSlots(); }
    @Override public @NotNull ItemStack getStackInSlot(int slot) { return internal.getStackInSlot(slot); }
    @Override public int getSlotLimit(int slot) { return internal.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return internal.isItemValid(slot, stack); }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (!allowInsert) return stack;
        return internal.insertItem(slot, stack, simulate);
    }
    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!allowExtract) return ItemStack.EMPTY;
        return internal.extractItem(slot, amount, simulate);
    }
}
