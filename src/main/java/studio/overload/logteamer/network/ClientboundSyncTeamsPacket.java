package studio.overload.logteamer.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import studio.overload.logteamer.Team;
import studio.overload.logteamer.client.ClientTeamManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ClientboundSyncTeamsPacket {
    private final List<Team> teams;

    public ClientboundSyncTeamsPacket(List<Team> teams) {
        this.teams = teams;
    }

    public static void encode(ClientboundSyncTeamsPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.teams.size());
        for (Team team : packet.teams) {
            buf.writeNbt(team.save());
        }
    }

    public static ClientboundSyncTeamsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Team> teams = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                teams.add(Team.load(tag));
            }
        }
        return new ClientboundSyncTeamsPacket(teams);
    }

    public static void handle(ClientboundSyncTeamsPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ClientTeamManager.getInstance().updateTeams(packet.teams);
        });
        context.get().setPacketHandled(true);
    }
}
