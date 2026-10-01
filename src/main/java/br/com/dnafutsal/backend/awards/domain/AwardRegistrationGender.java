package br.com.dnafutsal.backend.awards.domain;

public enum AwardRegistrationGender {

    MALE(
            "Masculino",
            "Paulista"
    ),

    FEMALE(
            "Feminino",
            "Paulista Feminino"
    );

    private final String label;
    private final String catalogTitle;

    AwardRegistrationGender(
            String label,
            String catalogTitle
    ) {
        this.label = label;
        this.catalogTitle = catalogTitle;
    }

    public String label() {
        return label;
    }

    public String catalogTitle() {
        return catalogTitle;
    }
}