package br.com.dnafutsal.backend.sports.domain;

import java.util.List;

public interface SportsDataGateway {

    List<CatalogItemView> catalogDivisions(
            int season
    );

    List<CatalogCategoryView> catalogCategories(
            int season,
            long divisionId
    );

    List<SportsEventView> searchEvents(
            SportsEventSearch search
    );

    SportsEventView event(
            long eventId
    );

    List<TeamView> teams(
            long eventId
    );

    SportsTeamDetailsView teamDetails(
            long eventId,
            long teamId
    );

    SportsSnapshot snapshot(
            long eventId
    );

    default List<CatalogItemView> catalogDivisions(
            int season,
            String title
    ) {
        return catalogDivisions(
                season
        );
    }

    default List<CatalogCategoryView> catalogCategories(
            int season,
            String title,
            long divisionId
    ) {
        return catalogCategories(
                season,
                divisionId
        );
    }
}