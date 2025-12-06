
package studio.overload.logteamer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public class TeamerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("teamer")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(TeamerCommand::createTeam)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(TeamerCommand::invitePlayer)))
                .then(Commands.literal("accept")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(TeamerCommand::acceptInvite)))
                .then(Commands.literal("reject")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(TeamerCommand::rejectInvite)))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(TeamerCommand::kickPlayer)))
                .then(Commands.literal("leave")
                        .executes(TeamerCommand::leaveTeam))
                .then(Commands.literal("type")
                        .then(Commands.literal("captain").executes(ctx -> setType(ctx, Team.TeamType.CAPTAIN)))
                        .then(Commands.literal("parliament").executes(ctx -> setType(ctx, Team.TeamType.PARLIAMENT))))
                .then(Commands.literal("captain")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(TeamerCommand::transferCaptain)))
                .then(Commands.literal("vote")
                        .then(Commands.literal("yes").executes(ctx -> vote(ctx, true)))
                        .then(Commands.literal("no").executes(ctx -> vote(ctx, false))))
                .then(Commands.literal("style")
                        .then(Commands.literal("color")
                                .then(Commands.argument("hex", StringArgumentType.word())
                                        .executes(TeamerCommand::setStyleColor)))
                        .then(Commands.literal("tname")
                                .then(Commands.argument("show", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(TeamerCommand::setStyleShowName)))
                        .then(Commands.literal("icon")
                                .then(Commands.argument("url_or_preset", StringArgumentType.greedyString())
                                        .executes(TeamerCommand::setStyleIcon)))));
    }

    private static TeamManager getManager(CommandSourceStack source) {
        return source.getServer().overworld().getDataStorage().computeIfAbsent(TeamManager::load, TeamManager::new,
                "logteamer_teams");
    }

    private static int createTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        TeamManager manager = getManager(context.getSource());

        if (manager.getTeam(player) != null) {
            context.getSource().sendFailure(Component.literal("You are already in a team."));
            return 0;
        }

        Team team = manager.createTeam(name, player);
        if (team == null) {
            context.getSource().sendFailure(Component.literal("Team with that name already exists."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Team " + name + " created successfully!"), true);
        return 1;
    }

    private static int invitePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);

        if (team == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team."));
            return 0;
        }

        if (!team.getMembers().contains(player.getUUID()))
            return 0; // Should be covered by getTeam check

        // Check permissions (Captain or Parliament? Assume any member can invite for
        // now unless specified otherwise, but usually Captain/Officers)
        // User prompt didn't specify invite perms. Assuming Captain only for Captain
        // type?
        // Prompt: "/teamer invite ... to not be like captian has all power you can do
        // /teamer type [capitan led / parliment led]"
        // This implies captain led = captain power. Parliament = voting?
        // Let's assume for invites: Captain led -> Captain only. Parliament -> Anyone?
        // Or Captain only?
        // Simpler: Captain only invites in Captain mode. Parliament... maybe voting to
        // invite?
        // User only specified voting for KICKING. I will restrict Invite to Captain for
        // 'Captain' mode, and maybe allow all for Parliament or keep it
        // Captain/Creator?
        // I'll stick to: Only Captain can invite in Captain mode. In Parliament mode...
        // let's say anyone can invite for now to avoid blocking, or restrict to Captain
        // (if one exists).
        // Wait, Parliament usually means democratic.
        // Let's check logic: if (team.getType() == Team.TeamType.CAPTAIN &&
        // !team.getCaptain().equals(player.getUUID())) fail.

        if (team.getType() == Team.TeamType.CAPTAIN && !team.getCaptain().equals(player.getUUID())) {
            context.getSource()
                    .sendFailure(Component.literal("Only the captain can invite players in a Captain-led team."));
            return 0;
        }

        manager.invitePlayer(team, player, target);
        return 1;
    }

    private static int acceptInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String teamName = StringArgumentType.getString(context, "team");
        TeamManager manager = getManager(context.getSource());

        if (manager.acceptInvite(player, teamName)) {
            context.getSource().sendSuccess(() -> Component.literal("You joined team " + teamName + "!"), true);
            // Notify team members?
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Could not accept invite (expired or already in team)."));
            return 0;
        }
    }

    private static int rejectInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String teamName = StringArgumentType.getString(context, "team");
        TeamManager manager = getManager(context.getSource());

        if (manager.rejectInvite(player, teamName)) {
            context.getSource().sendSuccess(() -> Component.literal("You rejected the invite to " + teamName + "."),
                    false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("No invite found."));
            return 0;
        }
    }

    private static int kickPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);

        if (team == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team."));
            return 0;
        }

        if (!team.getMembers().contains(target.getUUID())) {
            context.getSource()
                    .sendFailure(Component.literal("Player " + target.getName().getString() + " is not in your team."));
            return 0;
        }
        // Cannot kick self this way, use leave
        if (player.getUUID().equals(target.getUUID())) {
            context.getSource().sendFailure(Component.literal("Use /teamer leave to leave the team."));
            return 0;
        }

        if (team.getType() == Team.TeamType.CAPTAIN) {
            if (!team.getCaptain().equals(player.getUUID())) {
                context.getSource().sendFailure(Component.literal("Only the captain can kick players."));
                return 0;
            }
            if (manager.kickPlayer(team, target)) {
                context.getSource().sendSuccess(() -> Component.literal("Kicked " + target.getName().getString() + "."),
                        true);
                target.sendSystemMessage(Component.literal("You were kicked from the team."));
                return 1;
            }
        } else {
            // Parliament
            if (team.getCurrentVote() != null) {
                context.getSource().sendFailure(Component.literal("A vote is already in progress."));
                return 0;
            }
            manager.startKickVote(team, target, () -> {
                sendVoteMessage(context, team, "Vote started to kick " + target.getName().getString());
            });
            context.getSource().sendSuccess(
                    () -> Component.literal("Kick vote started for " + target.getName().getString()), true);
            return 1;
        }
        return 0;
    }

    private static int vote(CommandContext<CommandSourceStack> context, boolean yes) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);

        if (team == null || team.getCurrentVote() == null) {
            context.getSource().sendFailure(Component.literal("No active vote."));
            return 0;
        }

        manager.handleVote(team, player, yes,
                () -> {
                    // Passed
                    broadcast(context, team, "THE VOTE HAS BEEN PASSED");
                },
                () -> {
                    // Update - do nothing, maybe silent? Or broadcast?
                    // If failed (implied by null check inside handleVote logic? active vote would
                    // be gone if failed)
                    if (team.getCurrentVote() == null) {
                        broadcast(context, team, "Vote failed.");
                    }
                });
        return 1;
    }

    private static int leaveTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);

        if (team == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team."));
            return 0;
        }

        if (team.getCaptain().equals(player.getUUID())) {
            if (team.getMembers().size() > 1) {
                context.getSource().sendFailure(Component
                        .literal("Captain cannot leave without transferring captaincy or being the last member."));
                return 0;
            }
            // Disband
            manager.removeTeam(team.getName());
            context.getSource().sendSuccess(() -> Component.literal("Team disbanded."), true);
        } else {
            manager.kickPlayer(team, player); // Reuse kick logic for removal
            context.getSource().sendSuccess(() -> Component.literal("You left the team."), true);
        }
        return 1;
    }

    private static int setType(CommandContext<CommandSourceStack> context, Team.TeamType type)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);

        if (team == null)
            return 0;

        if (team.getType() == type) {
            context.getSource().sendFailure(Component.literal("Team is already " + type));
            return 0;
        }

        if (team.getType() == Team.TeamType.CAPTAIN) {
            if (!team.getCaptain().equals(player.getUUID())) {
                context.getSource().sendFailure(Component.literal("Only captain can change team type."));
                return 0;
            }
            team.setType(type);
            manager.setDirty();
            context.getSource().sendSuccess(() -> Component.literal("Team type set to " + type.name()), true);
            return 1;
        } else {
            // Parliament -> Vote to change logic
            if (team.getCurrentVote() != null) {
                context.getSource().sendFailure(Component.literal("A vote is already in progress."));
                return 0;
            }
            manager.startTypeVote(team, type, () -> {
                sendVoteMessage(context, team, "Vote started to change type to " + type.name());
            });
            context.getSource().sendSuccess(() -> Component.literal("Vote started to change team type."), true);
            return 1;
        }
    }

    private static int transferCaptain(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);

        if (team == null)
            return 0;
        if (!team.getCaptain().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Only captain can transfer captaincy."));
            return 0;
        }

        if (!team.getMembers().contains(target.getUUID())) {
            context.getSource().sendFailure(Component.literal("Target is not in the team."));
            return 0;
        }

        team.setCaptain(target.getUUID());
        manager.setDirty();
        context.getSource()
                .sendSuccess(() -> Component.literal("Captaincy transferred to " + target.getName().getString()), true);
        return 1;
    }

    private static void sendVoteMessage(CommandContext<CommandSourceStack> ctx, Team team, String title) {
        MutableComponent msg = Component.literal(title + " ");
        MutableComponent yesBtn = Component.literal("[YES]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teamer vote yes")));
        MutableComponent noBtn = Component.literal(" [NO]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teamer vote no")));

        msg.append(yesBtn).append(noBtn);

        for (java.util.UUID id : team.getMembers()) {
            ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(id);
            if (p != null)
                p.sendSystemMessage(msg);
        }
    }

    private static int setStyleColor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);
        if (team == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team."));
            return 0;
        }
        if (team.getType() == Team.TeamType.CAPTAIN && !team.getCaptain().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Only captain can change style."));
            return 0;
        }

        String hex = StringArgumentType.getString(context, "hex");
        try {
            int color = Integer.decode(hex.startsWith("#") ? hex : "#" + hex);
            team.setColor(color);
            manager.setDirty();
            context.getSource().sendSuccess(() -> Component.literal("Team color updated to " + hex), true);
            return 1;
        } catch (NumberFormatException e) {
            context.getSource().sendFailure(Component.literal("Invalid hex color code."));
            return 0;
        }
    }

    private static int setStyleShowName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);
        if (team == null)
            return 0;

        if (team.getType() == Team.TeamType.CAPTAIN && !team.getCaptain().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Only captain can change style."));
            return 0;
        }

        boolean show = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "show");
        team.setShowSuffix(show);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Team name visibility set to " + show), true);
        return 1;
    }

    private static int setStyleIcon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager manager = getManager(context.getSource());
        Team team = manager.getTeam(player);
        if (team == null)
            return 0;

        if (team.getType() == Team.TeamType.CAPTAIN && !team.getCaptain().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Only captain can change style."));
            return 0;
        }

        String url = StringArgumentType.getString(context, "url_or_preset");
        team.setIcon(url);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Team icon updated."), true);
        return 1;
    }

    private static void broadcast(CommandContext<CommandSourceStack> ctx, Team team, String msg) {
        for (java.util.UUID id : team.getMembers()) {
            ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(id);
            if (p != null)
                p.sendSystemMessage(Component.literal(msg));
        }
    }
}
