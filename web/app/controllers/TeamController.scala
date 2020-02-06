package controllers

import com.blackmorse.hattrick.model.enums.MatchType
import databases.ClickhouseDAO
import databases.clickhouse.{Accumulated, StatisticsCHRequest}
import hattrick.Hattrick
import javax.inject.{Inject, Singleton}
import models.clickhouse.TeamMatchInfo
import models.web.{AbstractWebDetails, Accumulate, StatisticsParameters, ViewDataFactory}
import play.api.mvc.{BaseController, Call, ControllerComponents}
import service.DefaultService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import collection.JavaConverters._

case class WebTeamMatch(teamMatchInfo: TeamMatchInfo, enemyTeamId: Long, enemyTeamName: String, teamGoals: Int, enemyGoals: Int)

case class WebTeamDetails(teamId: Long, teamName: String, leagueId: Int, leagueName: String, season: Int,
                          divisionLevel: Int, leagueUnitId: Long, leagueUnitName: String) extends AbstractWebDetails

@Singleton
class TeamController @Inject()(val controllerComponents: ControllerComponents,
                               implicit val clickhouseDAO: ClickhouseDAO,
                               val hattrick: Hattrick,
                               val defaultService: DefaultService,
                               val viewDataFactory: ViewDataFactory) extends BaseController {

  def matches(teamId: Long) = Action.async {
    val teamDetails = hattrick.api.teamDetails().teamID(teamId).execute().getTeams.asScala.filter(_.getTeamId == teamId).head

    val leagueId = teamDetails.getLeague.getLeagueId
    val divisionLevel = teamDetails.getLeagueLevelUnit.getLeagueLevel
    val season = defaultService.currentSeason
    val leagueUnitId = teamDetails.getLeagueLevelUnit.getLeagueLevelUnitId

    matchesFuture(leagueId, season, divisionLevel, leagueUnitId, teamId) map (matches => {
      val details = WebTeamDetails(teamId, teamDetails.getTeamName, leagueId, defaultService.leagueIdToCountryNameMap(leagueId).getEnglishName,
        season, divisionLevel, leagueUnitId, teamDetails.getLeagueLevelUnit.getLeagueLevelUnitName)

      Ok(views.html.team.matches(matches, details))
    })
  }

  private def matchesFuture(leagueId: Int, season: Int, divisionLevel: Int, leagueUnitId: Long, teamId: Long) = {
    val htMatchesFuture = Future(hattrick.api.matchesArchive().teamId(teamId).season(season).execute())

    val chMatchesFuture = clickhouseDAO.teamMatchesForSeason(season, leagueId, divisionLevel, leagueUnitId, teamId)

    chMatchesFuture.zipWith(htMatchesFuture) { case (chMatches, htMatches) =>
      val htMatchesMap = htMatches.getTeam.getMatchList.asScala
        .filter(matc => matc.getMatchType == MatchType.LEAGUE_MATCH)
        .map(matc => (matc.getMatchId, matc)).toMap

      chMatches.map(chMatch => {
        val htMatch = htMatchesMap(chMatch.matchId)

        val (teamGoals, enemyGoals, enemyTeamName, enemyTeamId) = if (htMatch.getHomeTeam.getHomeTeamId == chMatch.teamId) {
          (htMatch.getHomeGoals, htMatch.getAwayGoals, htMatch.getAwayTeam.getAwayTeamName, htMatch.getAwayTeam.getAwayTeamId)
        } else {
          (htMatch.getAwayGoals, htMatch.getHomeGoals, htMatch.getHomeTeam.getHomeTeamName, htMatch.getHomeTeam.getHomeTeamId)
        }

        WebTeamMatch(chMatch, enemyTeamId, enemyTeamName, teamGoals, enemyGoals)
      })
    }
  }


  def playerStats(teamId: Long, statisticsParametersOpt: Option[StatisticsParameters]) = Action.async { implicit request =>
    val statisticsParameters = statisticsParametersOpt.getOrElse(StatisticsParameters(defaultService.currentSeason, 0, Accumulate, "scored"))

    val func: StatisticsParameters => Call = sp => routes.TeamController.playerStats(teamId, Some(sp))

    val teamDetails = hattrick.api.teamDetails().teamID(teamId).execute().getTeams.asScala.filter(_.getTeamId == teamId).head

    val leagueId = teamDetails.getLeague.getLeagueId
    val divisionLevel = teamDetails.getLeagueLevelUnit.getLeagueLevel
    val leagueUnitId = teamDetails.getLeagueLevelUnit.getLeagueLevelUnitId

    val details = WebTeamDetails(teamId = teamId,
      teamName = teamDetails.getTeamName,
      leagueId = leagueId,
      leagueName = teamDetails.getLeague.getLeagueName,
      season = statisticsParameters.season,
      divisionLevel = divisionLevel,
      leagueUnitId = leagueUnitId,
      leagueUnitName = teamDetails.getLeagueLevelUnit.getLeagueLevelUnitName)

   StatisticsCHRequest.playerStatsRequest.execute(leagueId = Some(leagueId),
     divisionLevel = Some(divisionLevel),
     leagueUnitId = Some(leagueUnitId),
     teamId = Some(teamId),
     statisticsParameters = statisticsParameters)
       .map(playerStats => {
         val viewData = viewDataFactory.create(details = details,
           func = func,
           statisticsType = Accumulated,
           statisticsParameters = statisticsParameters,
           statisticsCHRequest = StatisticsCHRequest.playerStatsRequest,
           entities = playerStats)
         Ok(views.html.team.playerStats(viewData))

       })
  }
}


