package studio.overload.logteamer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class Team {
    private String name;
    private UUID captain;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> invites = new HashSet<>();
    private TeamType type = TeamType.CAPTAIN;

    // Transient voting state
    private ActiveVote currentVote;

    public Team(String name, UUID captain) {
        this.name = name;
        this.captain = captain;
        this.members.add(captain);
    }

    public String getName() {
        return name;
    }

    public UUID getCaptain() {
        return captain;
    }

    public void setCaptain(UUID captain) {
        this.captain = captain;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getInvites() {
        return invites;
    }

    public TeamType getType() {
        return type;
    }

    public void setType(TeamType type) {
        this.type = type;
    }

    public void addMember(UUID player) {
        members.add(player);
        invites.remove(player);
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public boolean hasInvite(UUID player) {
        return invites.contains(player);
    }

    public void addInvite(UUID player) {
        invites.add(player);
    }

    public void removeInvite(UUID player) {
        invites.remove(player);
    }

    public ActiveVote getCurrentVote() {
        return currentVote;
    }

    public void startKickVote(UUID target) {
        this.currentVote = new ActiveVote(VoteType.KICK, target.toString());
    }

    public void startTypeVote(TeamType targetType) {
        this.currentVote = new ActiveVote(VoteType.CHANGE_TYPE, targetType.name());
    }

    public void endVote() {
        this.currentVote = null;
    }

    private int color = 0xFFFFFF; // Default White
    private boolean showSuffix = false;
    private String icon = "";

    // ... existing constructor ...

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public boolean shouldShowSuffix() {
        return showSuffix;
    }

    public void setShowSuffix(boolean showSuffix) {
        this.showSuffix = showSuffix;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putUUID("Captain", captain);
        tag.putString("Type", type.name());
        tag.putInt("Color", color);
        tag.putBoolean("ShowSuffix", showSuffix);
        tag.putString("Icon", icon);

        ListTag memberList = new ListTag();
        for (UUID member : members) {
            memberList.add(StringTag.valueOf(member.toString()));
        }
        tag.put("Members", memberList);

        ListTag inviteList = new ListTag();
        for (UUID invite : invites) {
            inviteList.add(StringTag.valueOf(invite.toString()));
        }
        tag.put("Invites", inviteList);

        return tag;
    }

    public static Team load(CompoundTag tag) {
        String name = tag.getString("Name");
        UUID captain = tag.getUUID("Captain");
        Team team = new Team(name, captain);

        if (tag.contains("Type")) {
            team.setType(TeamType.valueOf(tag.getString("Type")));
        }
        if (tag.contains("Color"))
            team.setColor(tag.getInt("Color"));
        if (tag.contains("ShowSuffix"))
            team.setShowSuffix(tag.getBoolean("ShowSuffix"));
        if (tag.contains("Icon"))
            team.setIcon(tag.getString("Icon"));

        ListTag memberList = tag.getList("Members", Tag.TAG_STRING);
        team.members.clear(); // Clear default captain add
        for (Tag t : memberList) {
            team.members.add(UUID.fromString(t.getAsString()));
        }

        ListTag inviteList = tag.getList("Invites", Tag.TAG_STRING);
        for (Tag t : inviteList) {
            team.invites.add(UUID.fromString(t.getAsString()));
        }

        return team;
    }

    public enum TeamType {
        CAPTAIN,
        PARLIAMENT
    }

    public enum VoteType {
        KICK,
        CHANGE_TYPE
    }

    public static class ActiveVote {
        public final VoteType type;
        public final String target; // UUID string or TeamType name
        public final Set<UUID> yesVotes = new HashSet<>();
        public final Set<UUID> noVotes = new HashSet<>();

        public ActiveVote(VoteType type, String target) {
            this.type = type;
            this.target = target;
        }

        public boolean hasVoted(UUID voter) {
            return yesVotes.contains(voter) || noVotes.contains(voter);
        }

        public void vote(UUID voter, boolean yes) {
            if (yes)
                yesVotes.add(voter);
            else
                noVotes.add(voter);
        }

        public int getYesCount() {
            return yesVotes.size();
        }

        public int getNoCount() {
            return noVotes.size();
        }
    }
}
