package br.com.dnafutsal.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.awards.registration")
public record AwardRegistrationProperties(
        String editionSlug,
        Duration uploadTicketTtl,
        long maxUploadBytes,
        int maxDurationSeconds,
        int maxConcurrentTranscodes,
        int processingTimeoutSeconds,
        int videoCrf,
        String ffmpegPreset,
        String ffmpegBinary,
        String ffprobeBinary,
        String cpfEncryptionKeyBase64,

        String ociNamespace,
        String ociBucket,
        String ociRegion,
        String ociEndpoint,
        String ociTenancyOcid,
        String ociUserOcid,
        String ociFingerprint,
        String ociPrivateKeyBase64,
        String ociPrivateKeyPassphrase
) {

    public String effectiveEditionSlug() {
        return hasText(editionSlug)
                ? editionSlug.trim()
                : "premio-dna-2026";
    }

    public Duration effectiveUploadTicketTtl() {
        return uploadTicketTtl != null
                ? uploadTicketTtl
                : Duration.ofMinutes(15);
    }

    public long effectiveMaxUploadBytes() {
        return maxUploadBytes > 0
                ? maxUploadBytes
                : 262_144_000L;
    }

    public int effectiveMaxDurationSeconds() {
        return maxDurationSeconds > 0
                ? maxDurationSeconds
                : 60;
    }

    public int effectiveMaxConcurrentTranscodes() {
        return maxConcurrentTranscodes > 0
                ? maxConcurrentTranscodes
                : 1;
    }

    public int effectiveProcessingTimeoutSeconds() {
        return processingTimeoutSeconds > 0
                ? processingTimeoutSeconds
                : 300;
    }

    public int effectiveVideoCrf() {
        return videoCrf >= 18 && videoCrf <= 35
                ? videoCrf
                : 28;
    }

    public String effectiveFfmpegPreset() {
        return hasText(ffmpegPreset)
                ? ffmpegPreset.trim()
                : "slow";
    }

    public String effectiveFfmpegBinary() {
        return hasText(ffmpegBinary)
                ? ffmpegBinary.trim()
                : "ffmpeg";
    }

    public String effectiveFfprobeBinary() {
        return hasText(ffprobeBinary)
                ? ffprobeBinary.trim()
                : "ffprobe";
    }

    public String effectiveOciEndpoint() {
        if (hasText(ociEndpoint)) {
            return stripTrailingSlash(
                    ociEndpoint.trim()
            );
        }

        if (hasText(ociRegion)) {
            return "https://objectstorage."
                    + ociRegion.trim()
                    + ".oraclecloud.com";
        }

        return "";
    }

    public boolean hasObjectStorageConfiguration() {
        return hasText(ociNamespace)
                && hasText(ociBucket)
                && hasText(ociRegion)
                && hasText(ociTenancyOcid)
                && hasText(ociUserOcid)
                && hasText(ociFingerprint)
                && hasText(ociPrivateKeyBase64);
    }

    private static boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private static String stripTrailingSlash(
            String value
    ) {
        String result = value;

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }

    public List<String> missingObjectStorageConfiguration() {
        List<String> missing =
                new ArrayList<>();

        if (!hasText(ociNamespace)) {
            missing.add("ociNamespace");
        }

        if (!hasText(ociBucket)) {
            missing.add("ociBucket");
        }

        if (!hasText(ociRegion)) {
            missing.add("ociRegion");
        }

        if (!hasText(ociTenancyOcid)) {
            missing.add("ociTenancyOcid");
        }

        if (!hasText(ociUserOcid)) {
            missing.add("ociUserOcid");
        }

        if (!hasText(ociFingerprint)) {
            missing.add("ociFingerprint");
        }

        if (!hasText(ociPrivateKeyBase64)) {
            missing.add("ociPrivateKeyBase64");
        }

        return java.util.List.copyOf(missing);
    }
}
