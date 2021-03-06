package databases

import akka.actor.ActorSystem
import databases.clickhouse.StatisticsCHRequest
import javax.inject.{Singleton, Inject}
import models.clickhouse.{HistoryInfo, Promotion, TeamMatchInfo, TeamRankings}
import models.web.{Accumulate, Desc, MultiplyRoundsType, Round, SortingDirection, StatsType}
import play.api.db.DBApi
import play.api.libs.concurrent.CustomExecutionContext
import service.DefaultService

import scala.concurrent.Future

@Singleton
class ClickhouseDAO @Inject()(dbApi: DBApi)(implicit ec: DatabaseExecutionContext) {
  private val db = dbApi.database("default")

  def execute[T](request: StatisticsCHRequest[T],
                 leagueId: Option[Int] = None,
                 season: Option[Int] = None,
                 divisionLevel: Option[Int] = None,
                 leagueUnitId: Option[Long] = None,
                 teamId: Option[Long] = None,
                 page: Int = 0,
                 pageSize: Int = DefaultService.PAGE_SIZE,
                 statsType: StatsType,
                 sortBy: String,
                 sortingDirection: SortingDirection = Desc) = Future {
    db.withConnection { implicit connection =>

      if (!request.sortingColumns.map(_._1).contains(sortBy)) throw new Exception("Looks like SQL Injection")

      val sql = statsType match {
        case MultiplyRoundsType(func) => request.aggregateSql.replace("__func__", func).replace("__sortBy__", sortBy)
        case Accumulate => request.aggregateSql.replace("__sortBy__", sortBy)
        case Round(round) => request.oneRoundSql.replace("__round__", round.toString).replace("__sortBy__", sortBy)
      }

      val builder = SqlBuilder(sql)

      leagueId.foreach(builder.leagueId)
      season.foreach(builder.season)
      divisionLevel.foreach(builder.divisionLevel)
      leagueUnitId.foreach(builder.leagueUnitId)
      teamId.foreach(builder.teamId)
      builder.page(page)
      builder.pageSize(pageSize)
      builder.sortingDirection(sortingDirection)

      builder.build.as(request.parser.*)
    }
  }

  def teamMatchesForSeason(season: Int, leagueId: Int, teamId: Long) = Future {
    db.withConnection { implicit connection =>
      val teamSql = SqlBuilder(
        """SELECT
          |    round,
          |    match_id,
          |    team_id,
          |    team_name,
          |    rating_midfield,
          |    rating_right_def,
          |    rating_left_def,
          |    rating_mid_def,
          |    rating_right_att,
          |    rating_mid_att,
          |    rating_left_att,
          |    toInt32(((((((rating_midfield * 3) + rating_right_def) + rating_left_def) + rating_mid_def) + rating_right_att) + rating_mid_att) + rating_left_att) AS hatstats,
          |    formation,
          |    dt
          |FROM hattrick.match_details
          | __where__
          |ORDER BY round ASC
        """.stripMargin)
        .season(season).leagueId(leagueId).teamId(teamId)
        .build

      teamSql.as(TeamMatchInfo.teamMatchInfoMapper.*)
    }
  }

  def promotions(season: Int, leagueId: Int) = Future(
    db.withConnection { implicit connection =>
      val promotionsSql = SqlBuilder(
        """SELECT
          |season,
          |league_id,
          |up_division_level,
          |promotion_type,
          |`going_down_teams.team_id`,
          |`going_down_teams.team_name`,
          |`going_down_teams.division_level`,
          |`going_down_teams.league_unit_id`,
          |`going_down_teams.league_unit_name`,
          |`going_down_teams.position`,
          |`going_down_teams.points`,
          |`going_down_teams.diff`,
          |`going_down_teams.scored`,
          |`going_up_teams.team_id`,
          |`going_up_teams.team_name`,
          |`going_up_teams.division_level`,
          |`going_up_teams.league_unit_id`,
          |`going_up_teams.league_unit_name`,
          |`going_up_teams.position`,
          |`going_up_teams.points`,
          |`going_up_teams.diff`,
          |`going_up_teams.scored`
          |FROM hattrick.promotions
          |__where__""".stripMargin)
        .season(season)
        .leagueId(leagueId)
        .build

      promotionsSql.as(Promotion.promotionMapper.*)
    }
  )

  def teamRankings(season: Int, leagueId: Int, divisionLevel: Int,
                   leagueUnitId: Long, teamId: Long) = Future(
    db.withConnection { implicit  connection =>
      val teamRankingsSql = SqlBuilder("""SELECT
        |    team_id,
        |    team_name,
        |    round,
        |    rank_type,
        |    match_id,
        |    hatstats,
        |    hatstats_position,
        |    attack,
        |    attack_position,
        |    midfield,
        |    midfield_position,
        |    defense,
        |    defense_position,
        |    tsi,
        |    tsi_position,
        |    salary,
        |    salary_position,
        |    rating,
        |    rating_position,
        |    rating_end_of_match,
        |    rating_end_of_match_position,
        |    toInt32(age) as age,
        |    age_position,
        |    injury,
        |    injury_position,
        |    injury_count,
        |    injury_count_position
        |FROM hattrick.team_rankings
        | __where__
        |ORDER BY
        |    rank_type ASC,
        |    round ASC
        |    """.stripMargin)
        .season(season).leagueId(leagueId).divisionLevel(divisionLevel).leagueUnitId(leagueUnitId).teamId(teamId)
        .build

        teamRankingsSql.as(TeamRankings.teamRankingsMapper.*)
  } )

  def historyInfo(leagueId: Option[Int], season: Option[Int], round: Option[Int]): List[HistoryInfo] = {
    db.withConnection { implicit connection =>
      val builder = SqlBuilder( """SELECT
                      |    season,
                      |    league_id,
                      |    division_level,
                      |    round,
                      |    count() AS cnt
                      |FROM hattrick.match_details
                      | __where__
                      |GROUP BY
                      |    season,
                      |    league_id,
                      |    division_level,
                      |    round
                      |ORDER BY
                      |    season ASC,
                      |    league_id ASC,
                      |    division_level ASC,
                      |    round ASC
                      |""".stripMargin)

        leagueId.foreach(builder.leagueId)
        season.foreach(builder.season)
        round.foreach(builder.round)

        builder.build.as(HistoryInfo.mapper.*)
    }
  }

}

@Singleton
class DatabaseExecutionContext @Inject()(system: ActorSystem) extends CustomExecutionContext(system, "database.clickhouse")

