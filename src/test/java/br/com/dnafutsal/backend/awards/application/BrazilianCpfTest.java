package br.com.dnafutsal.backend.awards.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrazilianCpfTest {

    @Test
    void acceptsValidCpf() {
        assertTrue(
                BrazilianCpf.isValid(
                        "529.982.247-25"
                )
        );
    }

    @Test
    void rejectsRepeatedDigits() {
        assertFalse(
                BrazilianCpf.isValid(
                        "111.111.111-11"
                )
        );
    }

    @Test
    void rejectsInvalidCheckDigits() {
        assertFalse(
                BrazilianCpf.isValid(
                        "529.982.247-24"
                )
        );
    }
}
