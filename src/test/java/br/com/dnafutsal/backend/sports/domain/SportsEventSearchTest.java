package br.com.dnafutsal.backend.sports.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SportsEventSearchTest {

    @Test
    void createsDistinctKeysForNullAndLiteralValues() {
        String withoutTitle = new SportsEventSearch(2026, null, null, null).cacheKey();
        String literalAll = new SportsEventSearch(2026, "all", null, null).cacheKey();

        assertThat(withoutTitle).isNotEqualTo(literalAll);
    }

    @Test
    void createsDistinctKeysForValuesContainingDelimiters() {
        String colon = new SportsEventSearch(2026, "A:B", null, null).cacheKey();
        String underscore = new SportsEventSearch(2026, "A_B", null, null).cacheKey();

        assertThat(colon).isNotEqualTo(underscore);
    }
}
