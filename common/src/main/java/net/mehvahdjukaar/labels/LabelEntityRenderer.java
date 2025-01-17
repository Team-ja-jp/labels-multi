package net.mehvahdjukaar.labels;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.FrameBufferBackedDynamicTexture;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.RenderedTexturesManager;
import net.mehvahdjukaar.moonlight.api.client.util.TextUtil;
import net.mehvahdjukaar.moonlight.api.platform.ClientPlatformHelper;
import net.mehvahdjukaar.moonlight.api.resources.textures.Palette;
import net.mehvahdjukaar.moonlight.api.resources.textures.SpriteUtils;
import net.mehvahdjukaar.moonlight.api.resources.textures.TextureImage;
import net.mehvahdjukaar.moonlight.api.util.math.colors.HCLColor;
import net.mehvahdjukaar.moonlight.api.util.math.colors.RGBColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;


public class LabelEntityRenderer extends EntityRenderer<LabelEntity> {

    private final ModelBlockRenderer modelRenderer;
    private final ModelManager modelManager;

    public LabelEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        Minecraft minecraft = Minecraft.getInstance();
        this.modelRenderer = minecraft.getBlockRenderer().getModelRenderer();
        this.modelManager = minecraft.getBlockRenderer().getBlockModelShaper().getModelManager();
    }

    @Override
    public void render(LabelEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, light);

        //prevents incorrect rendering on first frame
        if (entity.tickCount == 0) return;

        poseStack.pushPose();

        poseStack.mulPose(Vector3f.YP.rotationDegrees(180 - entity.getYRot()));
        poseStack.translate(0, -0, -0.5 + 1 / 32f);
        poseStack.translate(-0.5, -0.5, -0.5);

        modelRenderer.renderModel(poseStack.last(), buffer.getBuffer(Sheets.cutoutBlockSheet()), //
                null, ClientPlatformHelper.getModel(modelManager, LabelsModClient.LABEL_MODEL), 1.0F, 1.0F, 1.0F,
                light, OverlayTexture.NO_OVERLAY);

        Item item = entity.getItem().getItem();

        if (item != Items.AIR) {

            FrameBufferBackedDynamicTexture tex = RenderedTexturesManager.requestFlatItemTexture(
                    entity.getTextureId(),
                    item,
                    ClientConfigs.TEXTURE_SIZE.get(),
                    LabelEntityRenderer::postProcess);

            if (tex.isInitialized()) {

                boolean hasText = ClientConfigs.HAS_TEXT.get();

                VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(tex.getTextureLocation()));

                Matrix4f tr = poseStack.last().pose();
                Matrix3f normal = poseStack.last().normal();
                int overlay = OverlayTexture.NO_OVERLAY;

                float z = 15.8f / 16f;

                float s = hasText ? 0.1875f : 0.25f;
                poseStack.translate(0.5, hasText ? 0.575 : 0.5, z);
                poseStack.pushPose();

                vertexConsumer.vertex(tr, -s, -s, 0).color(1f, 1f, 1f, 1f).uv(1f, 0f).overlayCoords(overlay).uv2(light).normal(normal, 0f, 0f, 1f).endVertex();
                vertexConsumer.vertex(tr, -s, s, 0).color(1f, 1f, 1f, 1f).uv(1f, 1f).overlayCoords(overlay).uv2(light).normal(normal, 0f, 0f, 1f).endVertex();

                vertexConsumer.vertex(tr, s, s, 0).color(1f, 1f, 1f, 1f).uv(0f, 1f).overlayCoords(overlay).uv2(light).normal(normal, 0f, 0f, 1f).endVertex();
                vertexConsumer.vertex(tr, s, -s, 0).color(1f, 1f, 1f, 1f).uv(0f, 0f).overlayCoords(overlay).uv2(light).normal(normal, 0f, 0f, 1f).endVertex();

                poseStack.popPose();

                if (hasText) drawLabelText(poseStack, buffer, entity, entity.getItem().getHoverName());
            }
        }

        poseStack.popPose();

    }


    //post process image
    private static void postProcess(NativeImage image) {

        //tex.getPixels().flipY();

        boolean reduceColors = ClientConfigs.REDUCE_COLORS.get();
        boolean recolor = ClientConfigs.IS_RECOLORED.get();
        boolean outline = ClientConfigs.OUTLINE.get() && recolor; //wont even attempt adding an outline if we don't grayscale first

        if (recolor || reduceColors) {
            //cleans image so we don't have similar colors
            SpriteUtils.mergeSimilarColors(image, 0.015f);
        }

        if (recolor) SpriteUtils.grayscaleImage(image);

        TextureImage originalTexture = TextureImage.of(image, null);
        TextureImage outlineTexture; //close this!
        if (outline) {
            List<Pair<Integer, Integer>> outlinePosition = new ArrayList<>();
            outlineTexture = originalTexture.makeCopy();
            //find edges position
            SpriteUtils.forEachPixel(outlineTexture.getImage(), (x, y) -> {
                var c = outlineTexture.getImage().getPixelRGBA(x, y);
                if (new RGBColor(c).alpha() != 0) {
                    if ((x == 0 || new RGBColor(image.getPixelRGBA(x - 1, y)).alpha() == 0) ||
                            (x == image.getWidth() - 1 || new RGBColor(image.getPixelRGBA(x + 1, y)).alpha() == 0) ||
                            (y == 0 || new RGBColor(image.getPixelRGBA(x, y - 1)).alpha() == 0) ||
                            (y == image.getHeight() - 1 || new RGBColor(image.getPixelRGBA(x, y + 1)).alpha() == 0)) {
                        outlinePosition.add(Pair.of(x, y));
                        //image.setPixelRGBA(x, y, dark.asRGB().mixWith(new RGBColor(c), 0.2f).toInt());
                    }
                }
            });
            SpriteUtils.forEachPixel(outlineTexture.getImage(), (x, y) -> {
                if (!outlinePosition.contains(Pair.of(x, y))) {
                    //remove inner
                    outlineTexture.getImage().setPixelRGBA(x, y, 0);
                } else {
                    //remove edges
                    originalTexture.getImage().setPixelRGBA(x, y, 0);
                }
            });


        } else {
            outlineTexture = null;
        }

        if (reduceColors) {
            //reduce main image colors
            int cutoff = 11;
            UnaryOperator<Integer> fn = i -> {
                if (i < cutoff) return i;
                else return (int) (Math.pow(i - cutoff + 1, 1 / 3f) + cutoff - 1);
            };
            //actually removes colors to have a palette around 13 (same as vanilla item textures)
            SpriteUtils.reduceColors(image, fn);
            //here we have a grayscale image with the amount of colors we want. Actual colors arent right yet

            //reduce outline colors
            if (outlineTexture != null) {
                int maxOutlineColors = 3;
                SpriteUtils.reduceColors(outlineTexture.getImage(), j -> Math.min(j, maxOutlineColors));
            }
        }

        if (recolor) {

            HCLColor dark = new RGBColor(ClientConfigs.DARK_COLOR.get()).asHCL();
            //HCLColor light = new RGBColor(196 / 255f, 155 / 255f, 88 / 255f, 1).asHCL();
            HCLColor light = new RGBColor(ClientConfigs.LIGHT_COLOR.get()).asHCL();

            //if (true) return;

            Palette old = Palette.fromImage(originalTexture, null, 0);
            int s = old.size();
            Palette newPalette;
            if (s < 3) {
                newPalette = Palette.ofColors(List.of(light, dark));
            } else {
                newPalette = Palette.fromArc(light, dark, s + (outline ? 2 : 0));
            }

            if (outline) {
                Palette newOutlinePalette;
                if (newPalette.size() > 4) {
                    //split palette to use some colors for outline
                    newOutlinePalette = Palette.ofColors(List.of());
                    newOutlinePalette.add(newPalette.remove(0));
                    var v = newPalette.remove(0);
                    newOutlinePalette.add(v);
                    newOutlinePalette.add(newPalette.getDarkest()); //they'll have 1 shared color
                    //newPalette.add(v);
                } else {
                    newOutlinePalette = newPalette.copy();
                    newOutlinePalette.add(newPalette.getDarkest().getDarkened());
                }
                fastInPlaceRecolor(outlineTexture.getImage(), Palette.fromImage(outlineTexture), newOutlinePalette);
            }
            fastInPlaceRecolor(image, old, newPalette);
        }

        if (outlineTexture != null) {
            originalTexture.applyOverlay(outlineTexture);
            outlineTexture.close();
        }
        //original isn't closed as TextureImage just wraps native image so we cant close that
    }

    //like with respriter but faster as palettes are already same size
    private static void fastInPlaceRecolor(NativeImage image, Palette old, Palette newPalette) {
        assert old.size() <= newPalette.size() : "Palettes must have same size";
        SpriteUtils.forEachPixel(image, (x, y) -> {

            int c = image.getPixelRGBA(x, y);
            //manual recolor cause faster since we are iterating anyway
            for (int i = 0; i < old.size(); i++) {
                if (old.getValues().get(i).value() == c) {
                    c = newPalette.getValues().get(i).value();
                    image.setPixelRGBA(x, y, c);
                    break;
                }
            }
        });
    }

    private void drawLabelText(PoseStack matrixStack, MultiBufferSource buffer,
                               LabelEntity entity, Component text) {
        matrixStack.scale(-1, 1, -1);

        Font font = Minecraft.getInstance().font;

        matrixStack.pushPose();
        matrixStack.translate(0, 0.25, 0);


        if (entity.needsVisualUpdate()) {
            float paperHeight = 1 - (2 * 0.45f);
            float paperWidth = 1 - (2 * 0.275f);
            var pair = TextUtil.fitLinesToBox(font, text, paperWidth, paperHeight);
            entity.setLabelText(pair.getFirst());
            entity.setLabelTextScale(pair.getSecond());
        }

        float scale = entity.getLabelTextScale();
        List<FormattedCharSequence> tempPageLines = entity.getLabelText();

        matrixStack.scale(scale, -scale, scale);
        int numberOfLines = tempPageLines.size();

        for (int lin = 0; lin < numberOfLines; ++lin) {

            FormattedCharSequence str = tempPageLines.get(lin);

            float dx = (float) (-font.width(str) / 2) + 0.5f;
            float dy = (((1f / scale) - (8 * numberOfLines)) / 2f) + 0.5f;

            font.drawInBatch(str, dx, dy + 8 * lin, 0xFF000000, false, matrixStack.last().pose(),
                    buffer, false, 0, LightTexture.FULL_BRIGHT);
        }

        matrixStack.popPose();
    }

    @Override
    public Vec3 getRenderOffset(LabelEntity entity, float partialTicks) {
        return Vec3.ZERO;
    }

    @Override
    public ResourceLocation getTextureLocation(LabelEntity p_110775_1_) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    protected boolean shouldShowName(LabelEntity p_177070_1_) {
        return false;
    }

}