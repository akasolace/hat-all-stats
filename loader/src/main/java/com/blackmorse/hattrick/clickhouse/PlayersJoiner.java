package com.blackmorse.hattrick.clickhouse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlayersJoiner {
    private final static String INSERT_SQL = "INSERT INTO {database}.player_stats SELECT \n" +
            "    player_info.season, \n" +
            "    player_info.league_id, \n" +
            "    player_info.division_level, \n" +
            "    player_info.league_unit_id, \n" +
            "    player_info.league_unit_name, \n" +
            "    player_info.team_id, \n" +
            "    player_info.team_name, \n" +
            "    player_info.time, \n" +
            "    player_info.dt, \n" +
            "    player_info.round, \n" +
            "    player_info.match_id, \n" +
            "    player_info.player_id, \n" +
            "    player_info.first_name, \n" +
            "    player_info.last_name, \n" +
            "    player_info.age, \n" +
            "    player_info.days, \n" +
            "    player_info.role_id, \n" +
            "    player_info.played_minutes, \n" +
            "    player_info.rating, \n" +
            "    player_info.rating_end_of_match, \n" +
            "    player_info.injury_level, \n" +
            "    player_info.tsi, \n" +
            "    player_info.salary, \n" +
            "    player_events.yellow_cards, \n" +
            "    player_events.red_cards, \n" +
            "    player_events.goals\n" +
            "FROM {database}.player_info\n" +
            "LEFT JOIN \n" +
            "(\n" +
            "    SELECT *\n" +
            "    FROM {database}.player_events\n" +
            "    WHERE (season = {season}) AND (round = {round})\n" +
            ") AS player_events ON (player_info.player_id = player_events.player_id) AND (player_info.season = player_events.season) AND (player_info.round = player_events.round)\n" +
            "WHERE (season = {season}) AND (league_id = {league_id}) AND (round = {round})\n";

    private final static String ALTER_EVENTS_SQL = "ALTER TABLE {database}.player_events DELETE WHERE (season = {season}) AND (round = {round})";

    private final static String ALTER_INFO_SQL = "ALTER TABLE {database}.player_info DELETE WHERE (season = {season}) AND (league_id = {league_id}) AND (round = {round})";

    private final JdbcTemplate jdbcTemplate;
    private final String databaseName;

    @Autowired
    public PlayersJoiner(JdbcTemplate jdbcTemplate,  @Value("${clickhouse.databaseName}") String databaseName) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseName = databaseName;
    }


    public void join(int season, int leagueId, int round) {
        log.info("Calculating player stats events for leagueId {}", leagueId);
        jdbcTemplate.update(replace(INSERT_SQL, season, leagueId, round));

        jdbcTemplate.update(replace(ALTER_EVENTS_SQL, season, leagueId, round));
        jdbcTemplate.update(replace(ALTER_INFO_SQL, season, leagueId, round));

    }

    private String replace(String string, int season, int leagueId, int round) {
        return string.replace("{season}", String.valueOf(season))
                .replace("{round}", String.valueOf(round))
                .replace("{league_id}", String.valueOf(leagueId))
                .replace("{database}", databaseName);
    }
}
