package com.blackmorse.hattrick;

import com.blackmorse.hattrick.clickhouse.ClickhouseBatcher;
import com.blackmorse.hattrick.clickhouse.ClickhouseBatcherFactory;
import com.blackmorse.hattrick.clickhouse.model.MatchDetails;
import com.blackmorse.hattrick.model.LeagueUnitId;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LastLeagueMatchLoader {
    private final HattrickService hattrickService;
    private final ClickhouseBatcher<MatchDetails> matchDetailsBatcher;

    @Autowired
    public LastLeagueMatchLoader(HattrickService hattrickService,
                                 ClickhouseBatcherFactory clickhouseBatcherFactory) {
        this.hattrickService = hattrickService;
        matchDetailsBatcher = clickhouseBatcherFactory.createMatchDetails();
    }

    public void load(List<String> countryNames) {
        List<LeagueUnitId> allLeagueUnitIdsForCountry = hattrickService.getAllLeagueUnitIdsForCountry(countryNames);

        List<List<LeagueUnitId>> allLeagueUnitIdsForCountryChunks = Lists.partition(allLeagueUnitIdsForCountry, 300);

        for (List<LeagueUnitId> allLeagueUnitIdsForCountryChunk : allLeagueUnitIdsForCountryChunks) {
            List<MatchDetails> lastMatchDetails = hattrickService.getLastMatchDetails(allLeagueUnitIdsForCountryChunk);

            matchDetailsBatcher.writeToClickhouse(lastMatchDetails);
        }
    }
}