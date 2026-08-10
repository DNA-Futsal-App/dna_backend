package br.com.dnafutsal.backend.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    @Test
    void addsBrazilCountryCodeToNationalMobileNumber() {
        assertThat(normalizer.normalize("(11) 98765-4321")).isEqualTo("+5511987654321");
    }

    @Test
    void preservesInternationalNumber() {
        assertThat(normalizer.normalize("+55 11 98765-4321")).isEqualTo("+5511987654321");
    }

    @Test
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> normalizer.normalize("123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("telefone válido");
    }
}
