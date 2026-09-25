package br.com.dnafutsal.backend.notification.community.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class CommunityScoreResolver {

    private final SportsCatalogService catalog;
    private final AppProperties app;

    public CommunityScoreResolver(
            SportsCatalogService catalog,
            AppProperties app
    ) {
        this.catalog = catalog;
        this.app = app;
    }

    public ResolvedCommunityScore resolve(
            CommunityScoreParser.ParsedCommunityScore score,
            Instant reportedAt
    ) {
        int season =
                reportedAt
                        .atZone(app.zoneId())
                        .getYear();

        CatalogItemView division =
                uniqueDivision(
                        season,
                        score.division()
                );

        CatalogCategoryView category =
                uniqueCategory(
                        season,
                        division.id(),
                        score.category()
                );

        List<TeamView> teams =
                catalog.teams(
                        category.eventId()
                );

        TeamView home =
                uniqueTeam(
                        teams,
                        score.homeTeam()
                );

        TeamView away =
                uniqueTeam(
                        teams,
                        score.awayTeam()
                );

        if (home.id().equals(away.id())) {
            throw Errors.badRequest(
                    "COMMUNITY_TEAMS_INVALID",
                    "Os times da partida devem ser diferentes."
            );
        }

        return new ResolvedCommunityScore(
                season,

                division.id(),
                division.name(),

                category.id(),
                category.name(),

                category.eventId(),

                home,
                away,

                score.homeScore(),
                score.awayScore()
        );
    }

    private CatalogItemView uniqueDivision(
            int season,
            String value
    ) {
        List<CatalogItemView> matches =
                catalog.divisions(season)
                        .stream()
                        .filter(item ->
                                same(
                                        item.name(),
                                        value
                                )
                        )
                        .toList();

        if (matches.size() != 1) {
            throw Errors.badRequest(
                    "COMMUNITY_DIVISION_NOT_FOUND",
                    "A divisão informada não foi reconhecida."
            );
        }

        return matches.get(0);
    }

    private CatalogCategoryView uniqueCategory(
            int season,
            long divisionId,
            String value
    ) {
        List<CatalogCategoryView> matches =
                catalog.categories(
                                season,
                                divisionId
                        )
                        .stream()
                        .filter(item ->
                                same(
                                        item.name(),
                                        value
                                )
                        )
                        .toList();

        if (matches.size() != 1) {
            throw Errors.badRequest(
                    "COMMUNITY_CATEGORY_NOT_FOUND",
                    "A categoria informada não foi reconhecida."
            );
        }

        return matches.get(0);
    }

    private TeamView uniqueTeam(
            List<TeamView> teams,
            String value
    ) {
        List<TeamView> matches =
                teams.stream()
                        .filter(team ->
                                same(
                                        team.name(),
                                        value
                                )
                                        ||
                                        same(
                                                team.shortName(),
                                                value
                                        )
                        )
                        .toList();

        if (matches.size() != 1) {
            throw Errors.badRequest(
                    "COMMUNITY_TEAM_NOT_FOUND",
                    "Um dos times informados não foi reconhecido."
            );
        }

        return matches.get(0);
    }

    private boolean same(
            String first,
            String second
    ) {
        return normalize(first)
                .equals(
                        normalize(second)
                );
    }

    private String normalize(
            String value
    ) {
        String text =
                value == null
                        ? ""
                        : value;

        return Normalizer
                .normalize(
                        text,
                        Normalizer.Form.NFD
                )
                .replaceAll(
                        "\\p{M}",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^\\p{L}\\p{N}]+",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    public record ResolvedCommunityScore(
            int season,

            long divisionId,
            String divisionName,

            long categoryId,
            String categoryName,

            long eventId,

            TeamView homeTeam,
            TeamView awayTeam,

            int homeScore,
            int awayScore
    ) {
    }
}