package studio.overload.logteamer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
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

        // Apply Color and Suffix
        Component original = event.getContent();
        int color = team.getColor();

        // Suffix logic
        if (team.shouldShowSuffix()) {
            original = Component.literal(original.getString() + " | " + team.getName())
                    .withStyle(style -> style.withColor(color));
        } else {
            original = original.copy().withStyle(style -> style.withColor(color));
        }

        event.setContent(original);

        // Icon Rendering (Simple implementation: Render a colored quad or text for now
        // to prove concept)
        // Calculating position is tricky without replacing the renderer.
        // For now, let's just make sure text updates work.
        // Full icon rendering from URL requires a complex TextureManager setup.
    }
}
