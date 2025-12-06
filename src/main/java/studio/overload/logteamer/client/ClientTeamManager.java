package studio.overload.logteamer.client;

import studio.overload.logteamer.Team;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientTeamManager {
    private static final ClientTeamManager INSTANCE = new ClientTeamManager();
    private final Map<String, Team> teamsByName = new HashMap<>();

    public static ClientTeamManager getInstance() {
        return INSTANCE;
    }

    public void updateTeams(Collection<Team> teams) {
        teamsByName.clear();
        for (Team team : teams) {
            teamsByName.put(team.getName(), team);
        }
    }

    public Team getTeam(String name) {
        return teamsByName.get(name);
    }

    public Team getTeam(UUID player) {
        for (Team team : teamsByName.values()) {
            if (team.getMembers().contains(player)) {
                return team;
            }
        }
        return null;
    }
}
