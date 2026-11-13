package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.SportsDefaultsProperties;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import br.com.dnafutsal.backend.sports.infrastructure.SportsSnapshotArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Year;
import java.util.Locale;

@Component
public class SportsDefaultEventResolver {

    private static final Logger log =
            LoggerFactory.getLogger(
                    SportsDefaultEventResolver.class
            );

    private final SportsCatalogService catalog;
    private final SportsDefaultsProperties defaults;
    private final AppProperties appProperties;
    private final Clock clock;
    private final SportsSnapshotArchive archive;

    public SportsDefaultEventResolver(
            SportsCatalogService catalog,
            SportsDefaultsProperties defaults,
            AppProperties appProperties,
            Clock clock,
            SportsSnapshotArchive archive
    ) {
        this.catalog = catalog;
        this.defaults = defaults;
        this.appProperties = appProperties;
        this.clock = clock;
        this.archive = archive;
    }

    public long resolveCurrentEventId() {
        int season = currentSeason();
        try {
            return resolveFromCatalog(season);
        } catch (BusinessException exception) {
            // Recover only upstream/catalog outages or a missing default selection.
            // Never replace unrelated errors or return an event from another season.
            if (!exception.status().is5xxServerError()) {
                throw exception;
            }
            var previous = archive.findDefaultEventId(season, defaults.division(), defaults.category());
            if (previous.isPresent()) {
                log.warn("Using archived default sports event season={} eventId={} upstreamCode={}",
                        season, previous.getAsLong(), exception.code());
                return previous.getAsLong();
            }
            throw exception;
        }
    }

    private long resolveFromCatalog(int season) {
        CatalogItemView division =
                catalog.divisions(season)
                        .stream()
                        .filter(item ->
                                same(
                                        item.name(),
                                        defaults.division()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                unavailable(
                                        season,
                                        "division"
                                )
                        );

        CatalogCategoryView category =
                catalog.categories(
                                season,
                                division.id()
                        )
                        .stream()
                        .filter(item ->
                                same(
                                        item.name(),
                                        defaults.category()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                unavailable(
                                        season,
                                        "category"
                                )
                        );

        return category.eventId();
    }

    private int currentSeason() {
        Clock applicationClock =
                clock.withZone(
                        appProperties.zoneId()
                );

        return Year.now(
                applicationClock
        ).getValue();
    }

    private RuntimeException unavailable(
            int season,
            String missing
    ) {
        log.warn(
                "Sports default unavailable season={} missing={} division={} category={}",
                season,
                missing,
                defaults.division(),
                defaults.category()
        );

        return Errors.dependencyUnavailable(
                "SPORTS_DEFAULT_UNAVAILABLE",
                "A competição padrão do DNA Futsal está temporariamente indisponível."
        );
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
        String normalized =
                Normalizer.normalize(
                        value == null
                                ? ""
                                : value,
                        Normalizer.Form.NFD
                );

        return normalized
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
}