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
