package br.com.dnafutsal.backend.notification.community.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.CommunityUpdatesProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class CommunityWebhookAuthenticator {

    private final CommunityUpdatesProperties properties;

    public CommunityWebhookAuthenticator(
            CommunityUpdatesProperties properties
    ) {
        this.properties = properties;
    }

    public void authenticate(
            String secret,
            String chatId
    ) {
        String expected =
                properties.webhookSecret();

        if (
                expected == null
                        || secret == null
                        || !MessageDigest.isEqual(
                        expected.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            throw Errors.unauthorized(
                    "COMMUNITY_WEBHOOK_INVALID",
                    "Credencial de integração inválida."
            );
        }

        if (
                !properties
                        .allowedChatIds()
                        .contains(chatId)
        ) {
            throw Errors.forbidden(
                    "COMMUNITY_CHAT_NOT_ALLOWED",
                    "Este grupo não está autorizado."
            );
        }
    }
}