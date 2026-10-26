package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SportsCatalogTitlesTest {
    @Test
    void forwardsOfficialTitlesWithoutGuessingIndependentUnion() {
        SportsDataGateway gateway = mock(SportsDataGateway.class);
        when(gateway.catalogTitles(2026)).thenReturn(List.of(new CatalogItemView(16, "Paulista")));
        var catalog = new SportsCatalogService(gateway);
        assertThat(catalog.titles(2026)).extracting(CatalogItemView::name)
                .containsExactly("Paulista");
        verify(gateway).catalogTitles(2026);
    }
}
