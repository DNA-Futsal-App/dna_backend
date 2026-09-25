package br.com.dnafutsal.backend.notification.community.application;

import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommunityScoreParser {

    private static final Pattern PATTERN =
            Pattern.compile(
                    "^\\s*" +
                            "([^;]{1,50})" +
                            "\\s*;\\s*" +
                            "([^;]{1,100})" +
                            "\\s*;\\s*" +
                            "(.+?)" +
                            "\\s+(\\d{1,2})" +
                            "\\s*[xX×]\\s*" +
                            "(\\d{1,2})" +
                            "\\s+(.+?)" +
                            "\\s*$"
            );

    public ParsedCommunityScore parse(
            String value
    ) {
        Matcher matcher =
                PATTERN.matcher(
                        value == null
                                ? ""
                                : value.trim()
                );

        if (!matcher.matches()) {
            throw Errors.badRequest(
                    "COMMUNITY_SCORE_PATTERN_INVALID",
                    "A mensagem não segue o padrão esperado."
            );
        }

        int homeScore =
                Integer.parseInt(
                        matcher.group(4)
                );

        int awayScore =
                Integer.parseInt(
                        matcher.group(5)
                );

        return new ParsedCommunityScore(
                matcher.group(1).trim(),
                matcher.group(2).trim(),
                matcher.group(3).trim(),
                homeScore,
                awayScore,
                matcher.group(6).trim()
        );
    }

    public record ParsedCommunityScore(
            String division,
            String category,
            String homeTeam,
            int homeScore,
            int awayScore,
            String awayTeam
    ) {
    }
}