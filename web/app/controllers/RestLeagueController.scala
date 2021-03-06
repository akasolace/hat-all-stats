package controllers

import com.google.inject.{Inject, Singleton}
import databases.ClickhouseDAO
import databases.clickhouse.StatisticsCHRequest
import hattrick.Hattrick
import io.swagger.annotations.{Api, ApiModelProperty, ApiOperation, ApiParam}
import io.swagger.models.parameters.Parameter
import models.web.{RestStatisticsParameters, RestTableData, StatisticsParameters, ViewDataFactory}
import play.api.libs.json.Json
import play.api.mvc.{BaseController, ControllerComponents}
import service.LeagueInfoService
import utils.Romans

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class RestLeagueData(leagueId: Int,
                          leagueName: String,
                          divisionLevels: Seq[String],
                          currentRound: Int,
                          rounds: Seq[Int],
                          currentSeason: Int,
                          seasons: Seq[Int])

object RestLeagueData {
  implicit val writes = Json.writes[RestLeagueData]
}

@Singleton
@Api(produces = "application/json")
class RestLeagueController @Inject() (val controllerComponents: ControllerComponents,
                                  implicit val clickhouseDAO: ClickhouseDAO,
                                  val leagueInfoService: LeagueInfoService,
                                  val viewDataFactory: ViewDataFactory,
                                  val hattrick: Hattrick) extends BaseController  {

    def getLeagueData(leagueId: Int) = Action.async {implicit request => 
      val leagueName = leagueInfoService.leagueInfo(leagueId).league.getEnglishName
      val numberOfDivisions = leagueInfoService.leagueInfo(leagueId).league.getNumberOfLevels
      val divisionLevels = (1 to numberOfDivisions).map(Romans(_))
      val currentRound = leagueInfoService.leagueInfo.currentRound(leagueId)
      val rounds = leagueInfoService.leagueInfo.rounds(leagueId, leagueInfoService.leagueInfo.currentSeason(leagueId))
      val currentSeason = leagueInfoService.leagueInfo.currentSeason(leagueId)
      val seasons = leagueInfoService.leagueInfo.seasons(leagueId)

      Future(Ok(Json.toJson(RestLeagueData(leagueId, leagueName, divisionLevels, currentRound, rounds, currentSeason, seasons))))
    }


    def teamHatstats(leagueId: Int, restStatisticsParameters: RestStatisticsParameters) = Action.async { implicit request =>
      val statisticsParameters =
          StatisticsParameters(season = restStatisticsParameters.season,
            page = restStatisticsParameters.page,
            statsType = restStatisticsParameters.statsType,
            sortBy = restStatisticsParameters.sortBy,
            pageSize = restStatisticsParameters.pageSize,
            sortingDirection = restStatisticsParameters.sortingDirection
          )

      StatisticsCHRequest.bestHatstatsTeamRequest.execute(
        leagueId = Some(leagueId),
        statisticsParameters = statisticsParameters)
      .map(teamRatings => {
        val isLastPage = teamRatings.size <= statisticsParameters.pageSize

        val entities = if(!isLastPage) teamRatings.dropRight(1) else teamRatings
        val restTableData = RestTableData(entities, isLastPage)
        Ok(Json.toJson(restTableData))
      })        
    }

    def leagueUnits(leagueId: Int, restStatisticsParameters: RestStatisticsParameters) = Action.async { implicit request =>
      val statisticsParameters =
        StatisticsParameters(season = restStatisticsParameters.season,
          page = restStatisticsParameters.page,
          statsType = restStatisticsParameters.statsType,
          sortBy = restStatisticsParameters.sortBy,
          pageSize = restStatisticsParameters.pageSize,
          sortingDirection = restStatisticsParameters.sortingDirection
        )

      StatisticsCHRequest.bestHatstatsLeagueRequest.execute(
        leagueId = Some(leagueId),
        statisticsParameters = statisticsParameters
      ).map(leagueUnits => {

        val isLastPage = leagueUnits.size <= statisticsParameters.pageSize

        val entities = if(!isLastPage) leagueUnits.dropRight(1) else leagueUnits
        val restTableData = RestTableData(entities, isLastPage)
        Ok(Json.toJson(restTableData))
      })
    }
}

