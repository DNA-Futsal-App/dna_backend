package br.com.dnafutsal.backend.awards.domain;

public enum AwardRegistrationContestCategory {

    BEAUTIFUL_GOAL(
            "Gol mais bonito",
            "gol_mais_bonito"
    ),

    BEST_DRIBBLE(
            "Melhor drible",
            "melhor_drible"
    ),

    FREE_KICK_GOAL(
            "Gol de falta mais bonito",
            "gol_de_falta_mais_bonito"
    ),

    BEST_SAVE(
            "Melhor defesa",
            "melhor_defesa"
    );

    private final String label;
    private final String storageSlug;

    AwardRegistrationContestCategory(
            String label,
            String storageSlug
    ) {
        this.label = label;
        this.storageSlug = storageSlug;
    }

    public String label() {
        return label;
    }

    public String storageSlug() {
        return storageSlug;
    }
}
