package com.upo.createeggproduction.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.upo.createeggproduction.content.block_entities.IncubatorBlockEntity;

public class IncubatorRenderer implements BlockEntityRenderer<IncubatorBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ItemRenderer itemRenderer;
    public IncubatorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        LOGGER.debug("IncubatorRenderer initialized.");
    }

    @Override
    public void render(IncubatorBlockEntity blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        IItemHandler itemHandler = blockEntity.getInventory();
        if (level == null || itemHandler == null) {
            return;
        }
        if (level.isClientSide()) {
            StringBuilder sb = new StringBuilder("IncubatorRenderer - Inventory State @ Render: [");
            boolean empty = true;
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                ItemStack stack = itemHandler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    if (!empty) sb.append(", ");
                    sb.append(slot).append(":").append(stack.getCount());
                    empty = false;
                }
            }
            if (empty) sb.append("EMPTY");
            sb.append("]");
        }
        BlockState blockState = blockEntity.getBlockState();
        Direction facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        float yRot = facing.toYRot();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.translate(-0.5, -0.5, -0.5);
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack itemStack = itemHandler.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;
            poseStack.pushPose();
            int row = i / 4;
            int col = i % 4;
            float startX_norm = 2.0f / 16.0f;
            float endX_norm = 14.0f / 16.0f;
            float spanX_norm = endX_norm - startX_norm;
            float startZ_norm = 12.0f / 16.0f;
            float endZ_norm = 3.0f / 16.0f;
            float spanZ_norm = endZ_norm - startZ_norm;
            float localX = startX_norm + (col + 0.5f) * (spanX_norm / 4.0f);
            float localZ = startZ_norm + (row + 0.5f) * (spanZ_norm / 4.0f);
            float localY = 10.5f / 16.0f;
            poseStack.translate(localX, localY, localZ);
            float scale = 0.2f;
            poseStack.scale(scale, scale, scale);
             poseStack.mulPose(Axis.YP.rotationDegrees(22.5f * (col % 2))); 
            try {
                BakedModel itemBakedModel = this.itemRenderer.getModel(itemStack, level, null, (int) blockEntity.getBlockPos().asLong() + i);
                RenderType itemRenderType = ItemBlockRenderTypes.getRenderType(itemStack, true);
                VertexConsumer itemConsumer = bufferSource.getBuffer(itemRenderType);
                this.itemRenderer.render(
                        itemStack,
                        ItemDisplayContext.FIXED,
                        false,
                        poseStack,
                        bufferSource,
                        packedLight,
                        packedOverlay,
                        itemBakedModel
                );
            } catch (Exception e) {
                LOGGER.error("Error rendering item in Incubator slot {}: {}", i, itemStack, e);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}






