package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AwardRegistrationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class AwardVideoProcessor {

    private final AwardRegistrationProperties properties;
    private final Semaphore transcodes;

    public AwardVideoProcessor(
            AwardRegistrationProperties properties
    ) {
        this.properties = properties;
        this.transcodes =
                new Semaphore(
                        properties.effectiveMaxConcurrentTranscodes(),
                        true
                );
    }

    public ProcessedVideo process(
            Path source
    ) {
        Probe sourceProbe =
                probe(
                        source
                );

        if (sourceProbe.durationSeconds()
                > properties.effectiveMaxDurationSeconds()
                + 0.05d) {
            throw Errors.badRequest(
                    "AWARD_VIDEO_TOO_LONG",
                    "O vídeo deve ter no máximo "
                            + properties.effectiveMaxDurationSeconds()
                            + " segundos."
            );
        }

        Dimensions target =
                targetDimensions(
                        sourceProbe.width(),
                        sourceProbe.height()
                );

        Path output = null;
        boolean acquired = false;

        try {
            transcodes.acquire();
            acquired = true;

            output =
                    Files.createTempFile(
                            "dna-award-video-",
                            ".mp4"
                    );

            List<String> command =
                    List.of(
                            properties.effectiveFfmpegBinary(),
                            "-hide_banner",
                            "-loglevel",
                            "error",
                            "-y",
                            "-i",
                            source.toAbsolutePath()
                                    .toString(),
                            "-map",
                            "0:v:0",
                            "-map",
                            "0:a?",
                            "-vf",
                            "scale="
                                    + target.width()
                                    + ":"
                                    + target.height(),
                            "-c:v",
                            "libx264",
                            "-preset",
                            properties.effectiveFfmpegPreset(),
                            "-crf",
                            String.valueOf(
                                    properties.effectiveVideoCrf()
                            ),
                            "-pix_fmt",
                            "yuv420p",
                            "-c:a",
                            "aac",
                            "-b:a",
                            "96k",
                            "-ac",
                            "2",
                            "-map_metadata",
                            "-1",
                            "-movflags",
                            "+faststart",
                            output.toAbsolutePath()
                                    .toString()
                    );

            ProcessResult transcode =
                    runDiscardingOutput(
                            command,
                            properties.effectiveProcessingTimeoutSeconds()
                    );

            if (transcode.exitCode()
                    != 0) {
                deleteQuietly(
                        output
                );

                throw Errors.badRequest(
                        "AWARD_VIDEO_PROCESSING_FAILED",
                        "Não foi possível processar este vídeo. Envie um arquivo de vídeo válido."
                );
            }

            Probe processed =
                    probe(
                            output
                    );

            validateProcessedDimensions(
                    processed.width(),
                    processed.height()
            );

            return new ProcessedVideo(
                    output,
                    Math.round(
                            processed.durationSeconds()
                                    * 1000d
                    ),
                    processed.width(),
                    processed.height(),
                    Files.size(
                            output
                    )
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            deleteQuietly(
                    output
            );

            throw Errors.dependencyUnavailable(
                    "AWARD_VIDEO_PROCESSOR_INTERRUPTED",
                    "O processamento do vídeo foi interrompido."
            );
        } catch (IOException exception) {
            deleteQuietly(
                    output
            );

            throw Errors.dependencyUnavailable(
                    "AWARD_VIDEO_PROCESSOR_UNAVAILABLE",
                    "O servidor não conseguiu preparar o vídeo para armazenamento.",
                    exception,
                    Map.of()
            );
        } finally {
            if (acquired) {
                transcodes.release();
            }
        }
    }

    Probe probe(
            Path source
    ) {
        ProcessResult result =
                runCapturingOutput(
                        List.of(
                                properties.effectiveFfprobeBinary(),
                                "-v",
                                "error",
                                "-select_streams",
                                "v:0",
                                "-show_entries",
                                "stream=codec_type,width,height",
                                "-show_entries",
                                "format=duration",
                                "-of",
                                "default=noprint_wrappers=1",
                                source.toAbsolutePath()
                                        .toString()
                        ),
                        30
                );

        if (result.exitCode()
                != 0) {
            throw Errors.badRequest(
                    "AWARD_UPLOAD_NOT_VIDEO",
                    "O arquivo enviado não é um vídeo válido. Faça o upload de um vídeo."
            );
        }

        Map<String, String> values =
                new HashMap<>();

        result.output()
                .lines()
                .forEach(line -> {
                    int separator =
                            line.indexOf(
                                    '='
                            );

                    if (separator > 0) {
                        values.put(
                                line.substring(
                                        0,
                                        separator
                                ),
                                line.substring(
                                        separator + 1
                                )
                        );
                    }
                });

        if (!"video".equals(
                values.get(
                        "codec_type"
                )
        )) {
            throw Errors.badRequest(
                    "AWARD_UPLOAD_NOT_VIDEO",
                    "O arquivo enviado não é um vídeo válido. Faça o upload de um vídeo."
            );
        }

        try {
            int width =
                    Integer.parseInt(
                            values.getOrDefault(
                                    "width",
                                    "0"
                            )
                    );

            int height =
                    Integer.parseInt(
                            values.getOrDefault(
                                    "height",
                                    "0"
                            )
                    );

            double duration =
                    Double.parseDouble(
                            values.getOrDefault(
                                    "duration",
                                    "0"
                            )
                    );

            if (width <= 0
                    || height <= 0
                    || duration <= 0
                    || !Double.isFinite(
                    duration
            )) {
                throw new NumberFormatException(
                        "Invalid video metadata."
                );
            }

            return new Probe(
                    duration,
                    width,
                    height
            );
        } catch (NumberFormatException exception) {
            throw Errors.badRequest(
                    "AWARD_UPLOAD_NOT_VIDEO",
                    "Não foi possível identificar corretamente duração e resolução do vídeo."
            );
        }
    }

    Dimensions targetDimensions(
            int sourceWidth,
            int sourceHeight
    ) {
        int maxWidth =
                sourceWidth >= sourceHeight
                        ? 1280
                        : 720;

        int maxHeight =
                sourceWidth >= sourceHeight
                        ? 720
                        : 1280;

        double factor =
                Math.min(
                        1d,
                        Math.min(
                                maxWidth
                                        / (double) sourceWidth,
                                maxHeight
                                        / (double) sourceHeight
                        )
                );

        int width =
                evenDown(
                        Math.max(
                                2,
                                (int) Math.floor(
                                        sourceWidth
                                                * factor
                                )
                        )
                );

        int height =
                evenDown(
                        Math.max(
                                2,
                                (int) Math.floor(
                                        sourceHeight
                                                * factor
                                )
                        )
                );

        return new Dimensions(
                width,
                height
        );
    }

    private void validateProcessedDimensions(
            int width,
            int height
    ) {
        boolean landscape =
                width >= height;

        boolean valid =
                landscape
                        ? width <= 1280
                        && height <= 720
                        : width <= 720
                        && height <= 1280;

        if (!valid) {
            throw Errors.dependencyUnavailable(
                    "AWARD_VIDEO_RESIZE_FAILED",
                    "O vídeo processado ultrapassou a resolução máxima permitida."
            );
        }
    }

    private int evenDown(
            int value
    ) {
        return value % 2 == 0
                ? value
                : value - 1;
    }

    private ProcessResult runCapturingOutput(
            List<String> command,
            int timeoutSeconds
    ) {
        try {
            Path outputFile =
                    Files.createTempFile(
                            "dna-award-process-",
                            ".log"
                    );

            try {
                Process process =
                        new ProcessBuilder(command)
                                .redirectErrorStream(true)
                                .redirectOutput(outputFile.toFile())
                                .start();

                waitFor(process, timeoutSeconds);

                String output =
                        Files.readString(
                                outputFile,
                                StandardCharsets.UTF_8
                        );

                return new ProcessResult(
                        process.exitValue(),
                        output
                );
            } finally {
                deleteQuietly(outputFile);
            }
        } catch (IOException exception) {
            throw toolUnavailable(exception);
        }
    }

    private ProcessResult runDiscardingOutput(
            List<String> command,
            int timeoutSeconds
    ) {
        try {
            Process process =
                    new ProcessBuilder(command)
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start();

            waitFor(process, timeoutSeconds);

            return new ProcessResult(
                    process.exitValue(),
                    ""
            );
        } catch (IOException exception) {
            throw toolUnavailable(exception);
        }
    }

    private void waitFor(
            Process process,
            int timeoutSeconds
    ) {
        try {
            boolean finished =
                    process.waitFor(
                            timeoutSeconds,
                            TimeUnit.SECONDS
                    );

            if (!finished) {
                process.destroyForcibly();
                throw Errors.dependencyUnavailable(
                        "AWARD_VIDEO_PROCESSING_TIMEOUT",
                        "O processamento do vídeo excedeu o tempo permitido. Tente novamente."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw Errors.dependencyUnavailable(
                    "AWARD_VIDEO_PROCESSOR_INTERRUPTED",
                    "O processamento do vídeo foi interrompido."
            );
        }
    }

    private RuntimeException toolUnavailable(
            IOException exception
    ) {
        return Errors.dependencyUnavailable(
                "AWARD_VIDEO_TOOL_UNAVAILABLE",
                "FFmpeg/FFprobe não estão disponíveis no servidor.",
                exception,
                Map.of()
        );
    }

    public static void deleteQuietly(
            Path path
    ) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(
                    path
            );
        } catch (IOException ignored) {
        }
    }

    public record ProcessedVideo(
            Path path,
            long durationMs,
            int width,
            int height,
            long fileSizeBytes
    ) {
    }

    record Probe(
            double durationSeconds,
            int width,
            int height
    ) {
    }

    record Dimensions(
            int width,
            int height
    ) {
    }

    private record ProcessResult(
            int exitCode,
            String output
    ) {
    }
}
