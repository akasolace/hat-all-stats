import LeagueUnits from '../common/tables/LeagueUnits'
import StatisticsParameters from '../rest/StatisticsParameters'
import LeagueRequest from '../rest/models/request/LeagueRequest'
import { getLeagueUnits } from '../rest/Client'
import ModelTableLeagueProps from './ModelTableLeagueProps'

class LeagueLeagueUnits extends LeagueUnits<ModelTableLeagueProps> {
    fetchEntities(tableProps: ModelTableLeagueProps, statisticsParameters: StatisticsParameters, callback: (restTableData: import("../rest/RestTableData").default<import("../rest/models/LeagueUnitRating").default>) => void): void {
        const leagueRequest: LeagueRequest = {type: 'LeagueRequest', leagueId: tableProps.leagueId()}
        getLeagueUnits(leagueRequest, statisticsParameters, callback)
    }
}

export default LeagueLeagueUnits