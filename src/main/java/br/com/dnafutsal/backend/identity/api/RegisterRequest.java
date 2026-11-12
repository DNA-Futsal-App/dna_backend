package br.com.dnafutsal.backend.identity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(min = 10, max = 72) String password,
        @Pattern(regexp = "^@?[A-Za-z0-9._]{1,30}$", message = "deve ser um usuário válido do Instagram")
        String childInstagram,
        @Positive Long eventId,
        @Size(max = 100) String categoryId,
        @Size(max = 100) String divisionId,
        @Size(max = 100) String teamId,
        @Size(max = 200) String coachAccessToken,
        SportsCompetitionCode competitionCode,
        @Valid CoachCategoryRegistration coachCategoryRegistration
) {
    // Backwards-compatible constructor for existing Java callers.
    public RegisterRequest(String name, String email, String phone, String password,
                           String childInstagram, Long eventId, String categoryId,
                           String divisionId, String teamId, String coachAccessToken) {
        this(name, email, phone, password, childInstagram, eventId, categoryId,
                divisionId, teamId, coachAccessToken, null, null);
    }
    // Keep the previous 11-argument canonical constructor for old tests/callers.
    public RegisterRequest(String name, String email, String phone, String password,
                           String childInstagram, Long eventId, String categoryId,
                           String divisionId, String teamId, String coachAccessToken,
                           SportsCompetitionCode competitionCode) {
        this(name, email, phone, password, childInstagram, eventId, categoryId,
                divisionId, teamId, coachAccessToken, competitionCode, null);
    }

    public record CoachCategoryRegistration(
            @NotBlank @Size(min = 20, max = 200) String token,
            @NotBlank @Size(max = 100) String teamId,
            @NotBlank @Size(max = 80) String role,
            @Size(max = 500) String evidenceUrl
    ) { }
}
