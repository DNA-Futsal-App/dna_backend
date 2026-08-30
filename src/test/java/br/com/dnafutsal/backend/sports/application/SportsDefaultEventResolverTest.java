package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.SportsDefaultsProperties;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SportsDefaultEventResolverTest {

    private final SportsCatalogService catalog =
            mock(SportsCatalogService.class);

    private final SportsDefaultsProperties defaults =
            new SportsDefaultsProperties(
                    "A1",
                    "Principal"
            );

    private final AppProperties appProperties =
            new AppProperties(
                    "http://localhost:3000",
                    List.of(
                            "http://localhost:3000"
                    ),
                    "America/Sao_Paulo"
            );

    @Test
    void resolvesDefaultEventFromCurrentSeasonCatalog() {
        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2026-08-29T20:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        when(
                catalog.divisions(2026)
        ).thenReturn(
                List.of(
                        new CatalogItemView(
                                3,
                                "A1"
                        ),
                        new CatalogItemView(
                                4,
                                "A2"
                        )
                )
        );

        when(
                catalog.categories(
                        2026,
                        3
                )
        ).thenReturn(
                List.of(
                        new CatalogCategoryView(
                                7,
                                "Principal",
                                917
                        )
                )
        );

        SportsDefaultEventResolver resolver =
                resolver(clock);

        long result =
                resolver.resolveCurrentEventId();

        assertThat(result)
                .isEqualTo(917);

        verify(catalog)
                .divisions(2026);

        verify(catalog)
                .categories(
                        2026,
                        3
                );
    }

    @Test
    void comparesDefaultNamesIgnoringCaseAndSpacing() {
        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2026-08-29T20:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        when(
                catalog.divisions(2026)
        ).thenReturn(
                List.of(
                        new CatalogItemView(
                                3,
                                "  a1 "
                        )
                )
        );

        when(
                catalog.categories(
                        2026,
                        3
                )
        ).thenReturn(
                List.of(
                        new CatalogCategoryView(
                                7,
                                "PRINCIPAL",
                                917
                        )
                )
        );

        assertThat(
                resolver(clock)
                        .resolveCurrentEventId()
        ).isEqualTo(917);
    }

    @Test
    void usesApplicationTimezoneWhenResolvingSeason() {
        /*
         * 01:30 UTC de 1º de janeiro ainda é
         * 22:30 de 31 de dezembro em São Paulo.
         */
        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2027-01-01T01:30:00Z"
                        ),
                        ZoneOffset.UTC
                );

        when(
                catalog.divisions(2026)
        ).thenReturn(
                List.of(
                        new CatalogItemView(
                                3,
                                "A1"
                        )
                )
        );

        when(
                catalog.categories(
                        2026,
                        3
                )
        ).thenReturn(
                List.of(
                        new CatalogCategoryView(
                                7,
                                "Principal",
                                917
                        )
                )
        );

        assertThat(
                resolver(clock)
                        .resolveCurrentEventId()
        ).isEqualTo(917);
    }

    @Test
    void failsWhenDefaultDivisionDoesNotExist() {
        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2026-08-29T20:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        when(
                catalog.divisions(2026)
        ).thenReturn(
                List.of(
                        new CatalogItemView(
                                4,
                                "A2"
                        )
                )
        );

        assertThatThrownBy(
                () -> resolver(clock)
                        .resolveCurrentEventId()
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(
                                    exception.status()
                                            .value()
                            ).isEqualTo(503);

                            assertThat(
                                    exception.code()
                            ).isEqualTo(
                                    "SPORTS_DEFAULT_UNAVAILABLE"
                            );
                        }
                );
    }

    @Test
    void failsWhenDefaultCategoryDoesNotExist() {
        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2026-08-29T20:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        when(
                catalog.divisions(2026)
        ).thenReturn(
                List.of(
                        new CatalogItemView(
                                3,
                                "A1"
                        )
                )
        );

        when(
                catalog.categories(
                        2026,
                        3
                )
        ).thenReturn(
                List.of(
                        new CatalogCategoryView(
                                8,
                                "Sub-20",
                                918
                        )
                )
        );

        assertThatThrownBy(
                () -> resolver(clock)
                        .resolveCurrentEventId()
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(
                                    exception.status()
                                            .value()
                            ).isEqualTo(503);

                            assertThat(
                                    exception.code()
                            ).isEqualTo(
                                    "SPORTS_DEFAULT_UNAVAILABLE"
                            );
                        }
                );
    }

    private SportsDefaultEventResolver resolver(
            Clock clock
    ) {
        return new SportsDefaultEventResolver(
                catalog,
                defaults,
                appProperties,
                clock
        );
    }
}