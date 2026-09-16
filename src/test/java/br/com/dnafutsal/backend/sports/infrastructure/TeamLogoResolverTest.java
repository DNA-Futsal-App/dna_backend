package br.com.dnafutsal.backend.sports.infrastructure;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TeamLogoResolverTest {
    private static final String PATH = "/team-logos/" + "a".repeat(64) + ".webp";
    private static final String BASE = "https://assets.example.test";

    private TeamLogoResolver.Manifest manifest(TeamLogoResolver.Binding... bindings) {
        return new TeamLogoResolver.Manifest(1,
                List.of(new TeamLogoResolver.Club("clube-teste", PATH)), List.of(bindings));
    }
    private TeamLogoResolver.Binding binding(long event, String id, String alias) {
        return new TeamLogoResolver.Binding(event, id, "clube-teste", List.of(alias));
    }

    @Test
    void scopesIdsAndAliasesToTheEventAndDoesNotGuessAnUnknownNumericId() {
        var resolver = new TeamLogoResolver(true, BASE, manifest(binding(7001, "10", "São Clube")));
        assertThat(resolver.resolve(7001, "10", "Outro nome", null)).isEqualTo(BASE + PATH);
        assertThat(resolver.resolve(7002, "10", "São Clube", null)).isNull();
        assertThat(resolver.resolve(7001, "999", "São Clube", null)).isNull();
        assertThat(resolver.resolve(7001, "name:sao-clube", " SÃO  CLUBE ", null)).isEqualTo(BASE + PATH);
        assertThat(resolver.resolve(7001, "name:sao-clube-b", "São Clube B", null)).isNull();
    }

    @Test
    void rejectsAmbiguousAliasesAndDuplicateIdsBeforeServingRequests() {
        assertThatThrownBy(() -> new TeamLogoResolver(true, BASE,
                manifest(binding(7001, "10", "São Clube"), binding(7001, "20", "Sao Clube"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ambiguous alias");
        assertThatThrownBy(() -> new TeamLogoResolver(true, BASE,
                manifest(binding(7001, "10", "A"), binding(7001, "10", "B"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate event/team");
    }

    @Test
    void allowsTheSameAliasAndIdInDifferentEvents() {
        var resolver = new TeamLogoResolver(true, BASE,
                manifest(binding(7001, "10", "Clube"), binding(7002, "10", "Clube")));
        assertThat(resolver.resolve(7002, "10", "Clube", null)).isEqualTo(BASE + PATH);
    }

    @Test
    void disablesEnrichmentWithoutNeedingAValidManifestOrOrigin() {
        var resolver = TeamLogoResolver.disabled();
        assertThat(resolver.resolve(7001, "10", "Clube", "https://source.example/logo.png"))
                .isEqualTo("https://source.example/logo.png");
    }

    @Test
    void rejectsEmptyManifestAndNonLocalHttpOrigin() {
        assertThatThrownBy(() -> new TeamLogoResolver(true, BASE, manifest()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No approved");
        assertThatThrownBy(() -> new TeamLogoResolver(true, "http://assets.example.test",
                manifest(binding(7001, "10", "Clube"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS origin");
    }
}
