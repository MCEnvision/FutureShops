package com.enviouse.futureshops.client.shop;

import com.enviouse.futureshops.block.ShopBlock;
import com.enviouse.futureshops.block.ShopBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ShopBlockGeoRenderer implements BlockEntityRenderer<ShopBlockEntity> {
    /** How long each listing stays on-screen before rotating to the next one. */
    private static final long CYCLE_MS = 5_000L;
    /** Full revolution period for the spin animation (ms). */
    private static final long SPIN_PERIOD_MS = 4_000L;
    /** Vertical-bob period for the spinning item (ms). */
    private static final long BOB_PERIOD_MS = 3_200L;
    /** Height of the selection hitbox top (y=9 voxel / 16). */
    private static final float HITBOX_TOP_Y = 9.0F / 16.0F;

    private final GeoBlockRenderer<ShopBlockEntity> inner;

    public ShopBlockGeoRenderer(BlockEntityRendererProvider.Context context) {
        this.inner = new GeoBlockRenderer<>(new ShopBlockGeoModel());
    }

    @Override
    public void render(ShopBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        inner.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        renderOwnerHeadDecal(be, poseStack, bufferSource, packedLight, packedOverlay);
        renderListingPreviewAndNameplate(be, poseStack, bufferSource, packedLight, packedOverlay);
    }

    // --- Spinning listing preview + floating nameplate ---------------------

    private void renderListingPreviewAndNameplate(ShopBlockEntity be, PoseStack poseStack,
                                                  MultiBufferSource bufferSource,
                                                  int packedLight, int packedOverlay) {
        // Stacks are pre-built (with listing NBT applied) in
        // ShopBlockEntity.handleUpdateTag, so tag-dependent models like TacZ
        // guns resolve correctly and no registry work happens per frame.
        List<ItemStack> stacks = be.getClientTopStacks();
        ItemStack stack = ItemStack.EMPTY;
        if (!stacks.isEmpty()) {
            long nowCycle = System.currentTimeMillis();
            int idx = (int) Math.floorMod(nowCycle / CYCLE_MS, stacks.size());
            stack = stacks.get(idx);
        }

        long now = System.currentTimeMillis();
        float bob = (float) Math.sin((now % BOB_PERIOD_MS) / (double) BOB_PERIOD_MS * Math.PI * 2.0) * 0.03F;
        float spinDeg = ((now % SPIN_PERIOD_MS) / (float) SPIN_PERIOD_MS) * 360.0F;
        float itemCenterY = HITBOX_TOP_Y + 0.25F + bob + be.getDisplayYOffset();
        float userScale = be.getDisplayScale();

        if (!stack.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5D, itemCenterY, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(spinDeg));
            // FIXED context reads like a "display block" rather than a ground drop.
            // 0.625 ≈ the default size; userScale is the owner-tunable multiplier.
            float s = 0.625F * userScale;
            poseStack.scale(s, s, s);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    be.getLevel(),
                    (int) be.getBlockPos().asLong());
            poseStack.popPose();
        }

        String label = be.getDisplayLabel();
        if (!label.isBlank() && !be.isNameplateHidden() && isPlayerLookingAt(be)) {
            float nameY = itemCenterY + 0.5F;
            renderFloatingNameplate(Component.literal(label), poseStack, bufferSource, packedLight, 0.5D, nameY, 0.5D);
        }
    }

    /** True when the client's crosshair is currently resting on this exact block. */
    private static boolean isPlayerLookingAt(ShopBlockEntity be) {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(be.getBlockPos());
    }

    /**
     * Draws a nameplate-style label that billboards to the camera. Unlike a
     * vanilla entity nametag we don't go through the entity system at all —
     * just one immediate-mode text draw per tick, no server state.
     */
    private void renderFloatingNameplate(Component label, PoseStack poseStack,
                                         MultiBufferSource bufferSource, int packedLight,
                                         double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Camera camera = mc.gameRenderer.getMainCamera();

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        // Negative X/Y flip puts the text right-side-up relative to the camera.
        poseStack.scale(-0.02F, -0.02F, 0.02F);
        Matrix4f matrix = poseStack.last().pose();
        float halfWidth = -font.width(label) / 2.0F;
        int bg = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        // First draw: transparent, behind blocks — gives a subtle halo.
        font.drawInBatch(label, halfWidth, 0, 0x20FFFFFF, false, matrix, bufferSource,
                Font.DisplayMode.SEE_THROUGH, bg, packedLight);
        // Second draw: solid foreground text over everything.
        font.drawInBatch(label, halfWidth, 0, 0xFFFFFFFF, false, matrix, bufferSource,
                Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    // --- Owner head decal on the block front -------------------------------

    private void renderOwnerHeadDecal(ShopBlockEntity be, PoseStack poseStack,
                                      MultiBufferSource bufferSource,
                                      int packedLight, int packedOverlay) {
        UUID ownerUuid = be.getOwnerUuid();
        if (ownerUuid == null) return;

        ResourceLocation skin = resolveSkinTexture(ownerUuid, be.getOwnerName());
        if (skin == null) return;

        Direction facing = be.getBlockState().hasProperty(ShopBlock.FACING)
                ? be.getBlockState().getValue(ShopBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        // Block centre (all rotations happen around block centre).
        poseStack.translate(0.5D, 0.0D, 0.5D);
        // GeckoLib renders the model facing NORTH by default and rotates the
        // pose via `rotateBlock` before drawing. We need to apply the same
        // rotation so the decal sits on the model's front face regardless of
        // which direction the block was placed toward. GeoBlockRenderer's
        // rotateBlock uses: NORTH=0°, EAST=270°, SOUTH=180°, WEST=90° about Y.
        float yaw = switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

        // Model-local coords: front face = -Z. Place the head decal on the
        // horizontal crossbeam (geo cube at x=[-1,1], y=[2,4], z=-6..6).
        // Quad is 2.5×2.5 model-units (0.15625 world) centred at (0, 3, -6 - ε).
        final float half = 0.078125F;                      // 2.5 / 16 / 2
        final float yCenter = 3.0F / 16.0F;                // halfway up the beam
        final float y0 = yCenter - half;
        final float y1 = yCenter + half;
        // Draw just in front of the beam's front face (z=-6/16 = -0.375).
        final float zFront = -0.375F - 0.001F;

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(skin));
        Matrix4f pose = poseStack.last().pose();
        // Player skin face UV on a 64x64 skin: x=[8,16], y=[8,16] → [0.125, 0.25].
        // Force fullbright so the head icon reads clearly even when the block
        // is in shadow — it's UI-ish info, not something we want the lighting
        // pass to dim.
        int fullBright = LightTexture.FULL_BRIGHT;
        drawFaceQuad(vc, pose, -half, y0, zFront, half, y1, 8, 8, 16, 16, fullBright, packedOverlay);
        // Tiny forward offset for the hat so it doesn't z-fight with the face.
        drawFaceQuad(vc, pose, -half, y0, zFront - 0.001F, half, y1, 40, 8, 48, 16, fullBright, packedOverlay);

        poseStack.popPose();
    }

    /** Draws one 2D textured quad facing -Z using the given pixel UV region of a 64x64 skin. */
    private static void drawFaceQuad(VertexConsumer vc, Matrix4f pose,
                                     float x0, float y0, float z,
                                     float x1, float y1,
                                     int uPx0, int vPx0, int uPx1, int vPx1,
                                     int packedLight, int packedOverlay) {
        float u0 = uPx0 / 64.0F;
        float u1 = uPx1 / 64.0F;
        float v0 = vPx0 / 64.0F;
        float v1 = vPx1 / 64.0F;
        // Four corners of the quad, CCW when viewed from -Z (front).
        vertex(vc, pose, x0, y0, z, u0, v1, packedLight, packedOverlay);
        vertex(vc, pose, x1, y0, z, u1, v1, packedLight, packedOverlay);
        vertex(vc, pose, x1, y1, z, u1, v0, packedLight, packedOverlay);
        vertex(vc, pose, x0, y1, z, u0, v0, packedLight, packedOverlay);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z,
                               float u, float v, int packedLight, int packedOverlay) {
        vc.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(0.0F, 0.0F, -1.0F)
                .endVertex();
    }

    // Per-client cache of the owner's real skin texture, keyed by UUID. Fetched
    // once per UUID via SkullBlockEntity's background session-service call so
    // we don't hit Mojang's API on every block render. Until the real skin
    // lands, we fall back to the default (Steve/Alex) skin so the block never
    // shows a random placeholder.
    private static final ConcurrentMap<UUID, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> SKIN_FETCHED = ConcurrentHashMap.newKeySet();

    private static ResourceLocation resolveSkinTexture(UUID uuid, String name) {
        if (uuid == null) return null;
        ResourceLocation cached = SKIN_CACHE.get(uuid);
        if (cached != null) return cached;

        if (SKIN_FETCHED.add(uuid)) {
            startSkinFetch(uuid, name);
        }
        // Placeholder until the real skin arrives.
        return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    private static void startSkinFetch(UUID uuid, String name) {
        try {
            GameProfile initial = new GameProfile(uuid, name == null || name.isBlank() ? "" : name);
            // SkullBlockEntity.updateGameprofile runs on its own executor and
            // returns a profile populated with texture URLs. We then marshal
            // back to the main thread to hand it to SkinManager, which does
            // the actual texture download + registration.
            net.minecraft.world.level.block.entity.SkullBlockEntity.updateGameprofile(initial, filled -> {
                if (filled == null) return;
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.getSkinManager().registerSkins(filled, (type, loc, tex) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN && loc != null) {
                        SKIN_CACHE.put(uuid, loc);
                    }
                }, false));
            });
        } catch (Throwable t) {
            // If the session service is unavailable (offline dev env, rate
            // limits, network error) just keep using the default skin.
            SKIN_FETCHED.remove(uuid);
        }
    }
}
