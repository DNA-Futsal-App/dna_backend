
package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.BusinessException;
import org.springframework.http.HttpStatus;

public final class EmailVerificationRequiredException
        extends BusinessException {

    public EmailVerificationRequiredException() {
        super(
                HttpStatus.FORBIDDEN,
                "EMAIL_NOT_VERIFIED",
                "Seu e-mail ainda não foi confirmado. "
                        + "Se o limite diário permitir, "
                        + "um novo link de confirmação será enviado."
        );
    }
}
