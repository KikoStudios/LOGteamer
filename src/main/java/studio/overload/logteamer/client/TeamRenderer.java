package studio.overload.logteamer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import studio.overload.logteamer.Logteamer;
import studio.overload.logteamer.Team;

@Mod.EventBusSubscriber(modid = Logteamer.MODID, value = Dist.CLIENT)
public class TeamRenderer {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        Team team = ClientTeamManager.getInstance().getTeam(player.getUUID());
        if (team == null)
            return;

        // Apply Color and Suffix logic
        Component original = event.getContent();
        int color = team.getColor();

        if (team.shouldShowSuffix()) {
            original = Component.literal(original.getString() + " | " + team.getName())
                    .withStyle(style -> style.withColor(color));
        } else {
            original = original.copy().withStyle(style -> style.withColor(color));
        }
        event.setContent(original);
package studio.overload.logteamer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import studio.overload.logteamer.Logteamer;
import studio.overload.logteamer.Team;

@Mod.EventBusSubscriber(modid = Logteamer.MODID, value = Dist.CLIENT)
public class TeamRenderer {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        Team team = ClientTeamManager.getInstance().getTeam(player.getUUID());
        if (team == null)
            return;

        // Apply Color and Suffix logic
        Component original = event.getContent();
        int color = team.getColor();

        if (team.shouldShowSuffix()) {
            original = Component.literal(original.getString() + " | " + team.getName())
                    .withStyle(style -> style.withColor(color));
        } else {
            original = original.copy().withStyle(style -> style.withColor(color));
        }
        event.setContent(original);

        // Icon Rendering
        ResourceLocation iconConfig = IconCache.getInstance().getIcon(team.getIcon());
        if (iconConfig != null) {
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();

            // 1. Enter "Nameplate Space" (Pixel coordinates)
            // Vanilla EntityRenderer uses this scale for nameplates
            float scale = 0.025F;
            poseStack.scale(-scale, -scale, scale);

            // 2. Calculate text width to determine offset
            // We are now in pixel space.
            int textWidth = Minecraft.getInstance().font.width(original);
            float halfWidth = textWidth / 2.0F;

            // 3. Icon Dimensions
            int iconSize = 10; // 10x10 pixels
            int padding = 2;

            // 4. Translate to Left of text
            // Center is 0,0. Left of text is -halfWidth.
            // Icon Position = -halfWidth - iconSize - padding
            poseStack.translate(-(halfWidth + iconSize + padding), -((float) iconSize / 2), 0); // Center vertically?
                                                                                                // Text is ~10 high.

            // Adjust vertical centering relative to text
            // Text baseline is a bit weird. Usually -10 puts it "above" origin?
            // Let's align roughly with the text visual center.
            // Vanilla text renders roughly from -5 to 5 (centered) vertically around the
            // pivot?
            // Actually it renders at: font.drawInBatch(..., -width/2, ...)
            // The y offset is 0 usually in the event context relative to the pivot?
            // Let's stick to simple centering.
            poseStack.translate(0, -2, 0); // Slight vertical adjustment to match text baseline

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, iconConfig);
            RenderSystem.enableDepthTest();

            // 5. Apply Team Color Tint
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(r, g, b, 1.0F);

            // 6. Draw Quad
            Matrix4f matrix = poseStack.last().pose();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuilder();

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(matrix, 0, iconSize, 0).uv(0, 1).endVertex();
            buffer.vertex(matrix, iconSize, iconSize, 0).uv(1, 1).endVertex();
            buffer.vertex(matrix, iconSize, 0, 0).uv(1, 0).endVertex();
            buffer.vertex(matrix, 0, 0, 0).uv(0, 0).endVertex();

            tesselator.end();

            // Reset color
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            poseStack.popPose();
        }
    }
}
