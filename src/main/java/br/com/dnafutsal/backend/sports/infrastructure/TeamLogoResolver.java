package br.com.dnafutsal.backend.sports.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class TeamLogoResolver {

    public record Club(String key, String logoPath) {}
    public record Binding(long eventId, String teamId, String clubKey, List<String> aliases) {}
    public record Manifest(int version, List<Club> clubs, List<Binding> bindings) {}
    private record Key(long eventId, String value) {}

    private final boolean enabled;
    private final Map<Key, String> byId;
    private final Map<Key, String> byAlias;

    @Autowired
    public TeamLogoResolver(
            JsonMapper mapper,
            @Value("${TEAM_LOGOS_ENABLED:false}") boolean enabled,
            @Value("${TEAM_LOGOS_BASE_URL:}") String baseUrl
    ) {
        this(enabled, baseUrl, enabled ? readManifest(mapper) : null);
    }

    TeamLogoResolver(boolean enabled, String baseUrl, Manifest manifest) {
        this.enabled = enabled;
        if (!enabled) {
            byId = Map.of();
            byAlias = Map.of();
            return;
        }
        String origin = validateOrigin(baseUrl);
        require(manifest != null && manifest.version() == 1, "Manifest version must be 1");
        require(manifest.clubs() != null && manifest.bindings() != null, "Missing clubs/bindings");
        require(!manifest.bindings().isEmpty(), "No approved team logos; keep TEAM_LOGOS_ENABLED=false");
        Map<String, String> clubs = new HashMap<>();
        for (Club club : manifest.clubs()) {
            require(club != null && club.key() != null && club.key().matches("[a-z0-9]+(?:-[a-z0-9]+)*"),
                    "Invalid club key");
            require(club.logoPath() != null && club.logoPath().matches("/team-logos/[a-f0-9]{64}\\.webp"),
                    "Invalid logo path for " + club.key());
            require(clubs.putIfAbsent(club.key(), origin + club.logoPath()) == null,
                    "Duplicate club key: " + club.key());
        }
        Map<Key, String> ids = new HashMap<>();
        Map<Key, String> aliases = new HashMap<>();
        for (Binding binding : manifest.bindings()) {
            require(binding != null && binding.eventId() > 0 && numericId(binding.teamId()),
                    "Invalid event/team ID");
            String url = clubs.get(binding.clubKey());
            require(url != null, "Unknown club: " + binding.clubKey());
            require(ids.putIfAbsent(new Key(binding.eventId(), binding.teamId()), url) == null,
                    "Duplicate event/team: " + binding.eventId() + "/" + binding.teamId());
            require(binding.aliases() != null, "Aliases must be an array");
            var uniqueAliases = new HashSet<String>();
            for (String alias : binding.aliases()) {
                String normalized = normalize(alias);
                require(!normalized.isBlank(), "Empty alias");
                if (uniqueAliases.add(normalized)) {
                    require(aliases.putIfAbsent(new Key(binding.eventId(), normalized), url) == null,
                            "Ambiguous alias in event " + binding.eventId() + ": " + alias);
                }
            }
        }
        byId = Map.copyOf(ids);
        byAlias = Map.copyOf(aliases);
    }

    public static TeamLogoResolver disabled() {
        return new TeamLogoResolver(false, "", null);
    }

    public String resolve(long eventId, String teamId, String teamName, String upstreamLogo) {
        if (!enabled) return upstreamLogo;
        // An unknown numeric ID must never inherit another team's logo by name.
        return numericId(teamId)
                ? byId.get(new Key(eventId, teamId))
                : byAlias.get(new Key(eventId, normalize(teamName)));
    }

    static String normalize(String value) {
        String prepared = Objects.toString(value, "").replace('ª', 'a').replace('º', 'o');
        return Normalizer.normalize(prepared, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean numericId(String id) {
        return id != null && id.matches("[1-9][0-9]*");
    }

    private static String validateOrigin(String value) {
        String origin = Objects.toString(value, "").trim().replaceAll("/+$", "");
        URI uri = URI.create(origin);
        String host = uri.getHost();
        boolean localHttp = "http".equals(uri.getScheme())
                && ("localhost".equals(host) || "127.0.0.1".equals(host));
        require(host != null && ("https".equals(uri.getScheme()) || localHttp)
                        && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                        && uri.getRawFragment() == null && uri.getRawPath().isEmpty(),
                "TEAM_LOGOS_BASE_URL must be an HTTPS origin (HTTP only for localhost/127.0.0.1)");
        return origin;
    }

    private static Manifest readManifest(JsonMapper mapper) {
        try (var stream = new ClassPathResource("team-logos.json").getInputStream()) {
            return mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build().readValue(stream, Manifest.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load team-logos.json", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
