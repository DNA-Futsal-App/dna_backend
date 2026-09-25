package br.com.dnafutsal.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(
        prefix = "app.community-updates"
)
public record CommunityUpdatesProperties(
        String webhookSecret,
        Set<String> allowedChatIds
) {

    public CommunityUpdatesProperties {
        allowedChatIds =
                allowedChatIds == null
                        ? Set.of()
                        : Set.copyOf(
                        allowedChatIds
                );
    }
}