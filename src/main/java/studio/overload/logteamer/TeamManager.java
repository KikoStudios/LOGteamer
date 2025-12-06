package studio.overload.logteamer;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.network.PacketDistributor;
import studio.overload.logteamer.network.ClientboundSyncTeamsPacket;
import studio.overload.logteamer.network.NetworkHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamManager extends SavedData {
    private final Map<String, Team> teamsByName = new HashMap<>();

    public void sync() {
        if (teamsByName.isEmpty())
            return;
        ClientboundSyncTeamsPacket packet = new ClientboundSyncTeamsPacket(new ArrayList<>(teamsByName.values()));
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    @Override
    public void setDirty() {
        super.setDirty();
        sync();
    }

    public Team createTeam(String name, ServerPlayer captain) {
        if (teamsByName.containsKey(name)) {
            return null; // Team already exists
        }
        Team team = new Team(name, captain.getUUID());
        teamsByName.put(name, team);
        setDirty();
        return team;
    }

    @Nullable
    public Team getTeam(String name) {
        return teamsByName.get(name);
    }

    @Nullable
    public Team getTeam(ServerPlayer player) {
        for (Team team : teamsByName.values()) {
            if (team.getMembers().contains(player.getUUID())) {
                return team;
            }
        }
        return null; // Player is not in a team
    }

    public void removeTeam(String name) {
        teamsByName.remove(name);
        setDirty();
    }

    public void invitePlayer(Team team, ServerPlayer inviter, ServerPlayer target) {
        if (team.hasInvite(target.getUUID()) || team.getMembers().contains(target.getUUID())) {
            inviter.sendSystemMessage(Component.literal("Player is already invited or in the team."));
            return;
        }

        team.addInvite(target.getUUID());
        setDirty();

        inviter.sendSystemMessage(Component.literal("Invited " + target.getName().getString() + " to the team."));

        MutableComponent message = Component.literal("You have been invited to join team " + team.getName() + ". ");

        MutableComponent acceptBtn = Component.literal("[ACCEPT]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teamer accept " + team.getName())));

        MutableComponent rejectBtn = Component.literal(" [REJECT]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teamer reject " + team.getName())));

        target.sendSystemMessage(message.append(acceptBtn).append(rejectBtn));
    }

    public boolean acceptInvite(ServerPlayer player, String teamName) {
        Team team = getTeam(teamName);
        if (team == null || !team.hasInvite(player.getUUID())) {
            return false;
        }

        // Remove from other teams if necessary, or check logic? Assumed one team per
        // player.
        Team currentTeam = getTeam(player);
        if (currentTeam != null) {
            player.sendSystemMessage(Component.literal("You are already in a team. Leave it first."));
            return false;
        }

        team.addMember(player.getUUID());
        setDirty();
        return true;
    }

    public boolean rejectInvite(ServerPlayer player, String teamName) {
        Team team = getTeam(teamName);
        if (team == null || !team.hasInvite(player.getUUID())) {
            return false;
        }
        team.removeInvite(player.getUUID());
        setDirty();
        return true;
    }

    public boolean kickPlayer(Team team, ServerPlayer target) {
        if (!team.getMembers().contains(target.getUUID()))
            return false;
        team.removeMember(target.getUUID());
        setDirty();
        return true;
    }

    public void startKickVote(Team team, ServerPlayer target, Runnable onStart) {
        team.startKickVote(target.getUUID());
        setDirty();
        if (onStart != null)
            onStart.run();
    }

    public void startTypeVote(Team team, Team.TeamType type, Runnable onStart) {
        team.startTypeVote(type);
        setDirty();
        if (onStart != null)
            onStart.run();
    }

    public void handleVote(Team team, ServerPlayer voter, boolean yes, Runnable onPass, Runnable onVoteUpdate) {
        Team.ActiveVote vote = team.getCurrentVote();
        if (vote == null)
            return;
        if (vote.hasVoted(voter.getUUID())) {
            voter.sendSystemMessage(Component.literal("You have already voted."));
            return;
        }

        vote.vote(voter.getUUID(), yes);

        voter.sendSystemMessage(Component.literal("YOU VOTED " + (yes ? "YES" : "NO"))
                .withStyle(yes ? ChatFormatting.GREEN : ChatFormatting.RED));

        int totalMembers = team.getMembers().size();
        // If kick vote, target cannot vote? Or just count them? Usually target can't
        // vote to save themselves in some systems, but here let's replicate the viewed
        // snippet which excluded target for KICK.
        boolean targetIsMember = false;
        if (vote.type == Team.VoteType.KICK) {
            try {
                if (team.getMembers().contains(UUID.fromString(vote.target))) {
                    targetIsMember = true;
                }
            } catch (Exception e) {
            }
        }

        int eligibleVoters = totalMembers - (targetIsMember ? 1 : 0);
        if (eligibleVoters < 1)
            eligibleVoters = 1; // Safety

        // Simple majority > 50%
        if (vote.getYesCount() > eligibleVoters / 2) {
            // Vote passed
            if (vote.type == Team.VoteType.KICK) {
                try {
                    team.removeMember(UUID.fromString(vote.target));
                } catch (Exception ignored) {
                }
            } else if (vote.type == Team.VoteType.CHANGE_TYPE) {
                team.setType(Team.TeamType.valueOf(vote.target));
            }

            team.endVote();
            setDirty();
            if (onPass != null)
                onPass.run();
        } else if (vote.getNoCount() >= (eligibleVoters / 2.0)) {
            // Vote failed logic: needed votes impossible to reach?
            // Actually if No >= 50% (inclusive or exclusive? usually > 50% fails, or even
            // tie blocks? logic says > half wins. so tie fails.)
            // If eligible 4. Half is 2. >2 is 3. 3 Yes needed.
            // If 2 No. Remaining 2. Max Yes 2. 2 is not > 2. So failed.
            // So if No >= eligible/2.0 ... 2 >= 2.0 -> True. Correct.

            team.endVote();
            setDirty();
            if (onVoteUpdate != null)
                onVoteUpdate.run();
        } else {
            setDirty(); // Save vote progress
            if (onVoteUpdate != null)
                onVoteUpdate.run();
        }
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        ListTag list = new ListTag();
        for (Team team : teamsByName.values()) {
            list.add(team.save());
        }
        compound.put("Teams", list);
        return compound;
    }

    public static TeamManager load(CompoundTag compound) {
        TeamManager data = new TeamManager();
        if (compound.contains("Teams")) {
            ListTag list = compound.getList("Teams", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                Team team = Team.load((CompoundTag) t);
                data.teamsByName.put(team.getName(), team);
            }
        }
        return data;
    }
}
