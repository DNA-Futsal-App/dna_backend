package br.com.dnafutsal.backend.sports.domain;

/** Identifies a sports competition, not the gender of the account holder. */
public enum SportsCompetitionCode {
    PAULISTA("Paulista"),
    PAULISTA_FEMININO("Paulista Feminino");

    private final String catalogTitle;

    SportsCompetitionCode(String catalogTitle) {
        this.catalogTitle = catalogTitle;
    }

    public String catalogTitle() {
        return catalogTitle;
    }
}
