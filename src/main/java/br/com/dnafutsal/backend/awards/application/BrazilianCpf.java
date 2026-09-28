package br.com.dnafutsal.backend.awards.application;

public final class BrazilianCpf {

    private BrazilianCpf() {
    }

    public static String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.replaceAll(
                        "\\D",
                        ""
                );
    }

    public static boolean isValid(
            String value
    ) {
        String cpf =
                normalize(
                        value
                );

        if (cpf.length() != 11) {
            return false;
        }

        boolean allEqual = true;

        for (int index = 1;
             index < cpf.length();
             index++) {
            if (cpf.charAt(index)
                    != cpf.charAt(0)) {
                allEqual = false;
                break;
            }
        }

        if (allEqual) {
            return false;
        }

        return checkDigit(
                cpf,
                9
        ) == digitAt(
                cpf,
                9
        )
                && checkDigit(
                cpf,
                10
        ) == digitAt(
                cpf,
                10
        );
    }

    private static int checkDigit(
            String cpf,
            int length
    ) {
        int sum = 0;

        for (int index = 0;
             index < length;
             index++) {
            sum += digitAt(
                    cpf,
                    index
            ) * (length + 1 - index);
        }

        int remainder =
                (sum * 10) % 11;

        return remainder == 10
                ? 0
                : remainder;
    }

    private static int digitAt(
            String value,
            int index
    ) {
        return value.charAt(index)
                - '0';
    }
}
