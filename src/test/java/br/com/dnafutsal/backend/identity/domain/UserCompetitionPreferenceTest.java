package br.com.dnafutsal.backend.identity.domain;

import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserCompetitionPreferenceTest {
    @Test
    void olderUserKeepsNullCompetitionWithoutLosingItsEvent() {
        UserAccount user = new UserAccount("Pessoa", "teste@example.com", "+5511999999999",
                "hash", null, 917L, "7", "3", "10");
        assertThat(user.getFollowedCompetitionCode()).isNull();
        assertThat(UserProfileResponse.from(user).eventId()).isEqualTo(917L);
    }

    @Test
    void storesFemaleCompetitionAndExposesInProfile() {
        UserAccount user = new UserAccount("Pessoa", "teste@example.com", "+5511999999999",
                "hash", null, 926L, "20", "10", "11", SportsCompetitionCode.PAULISTA_FEMININO);
        assertThat(UserProfileResponse.from(user).competitionCode())
                .isEqualTo(SportsCompetitionCode.PAULISTA_FEMININO);
        user.updateProfile("Pessoa", "teste@example.com", "+5511999999999", null,
                917L, "7", "3", "10", null);
        assertThat(user.getFollowedCompetitionCode()).isNull();
        assertThat(user.getFollowedEventId()).isEqualTo(917L);
    }
}
