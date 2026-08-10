package br.com.dnafutsal.backend.common;

import org.springframework.stereotype.Component;

@Component
public class PhoneNormalizer {

    public String normalize(String rawPhone) {
        if (rawPhone == null) {
            throw Errors.badRequest("INVALID_PHONE", "O telefone é obrigatório.");
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 10 || digits.length() == 11) {
            digits = "55" + digits;
        }
        if (digits.length() < 12 || digits.length() > 15) {
            throw Errors.badRequest("INVALID_PHONE", "Informe um telefone válido com DDD.");
        }
        return "+" + digits;
    }
}
