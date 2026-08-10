package br.com.dnafutsal.backend.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenSupportTest {

    private final TokenSupport tokens = new TokenSupport();

    @Test
    void generatesUniqueOpaqueTokensAndStableHashes() {
        String first = tokens.generate();
        String second = tokens.generate();

        assertThat(first).hasSize(43).isNotEqualTo(second);
        assertThat(tokens.hash(first)).hasSize(43).isEqualTo(tokens.hash(first));
        assertThat(tokens.hash(first)).isNotEqualTo(tokens.hash(second));
    }
}
