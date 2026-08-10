package br.com.dnafutsal.backend.sports.domain;

import java.util.List;

public interface SportsDataGateway {
    List<CatalogItem> categories();

    List<CatalogItem> divisions(String categoryId);

    List<TeamView> teams(String categoryId, String divisionId);

    List<MatchView> playedMatches(SportsFilter filter);

    List<MatchView> upcomingMatches(SportsFilter filter);

    List<StandingView> standings(SportsFilter filter);

    List<TopScorerView> topScorers(SportsFilter filter);
}
