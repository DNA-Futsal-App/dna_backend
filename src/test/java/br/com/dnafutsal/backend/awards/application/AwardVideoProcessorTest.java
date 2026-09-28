package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.config.AwardRegistrationProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwardVideoProcessorTest {

    private final AwardVideoProcessor processor =
            new AwardVideoProcessor(
                    new AwardRegistrationProperties(
                            "premio-dna-2026",
                            null,
                            0,
                            0,
                            1,
                            0,
                            28,
                            "veryfast",
                            "ffmpeg",
                            "ffprobe",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    )
            );

    @Test
    void downsizesLandscape4kTo720pWithoutChangingAspectRatio() {
        AwardVideoProcessor.Dimensions dimensions =
                processor.targetDimensions(
                        3840,
                        2160
                );

        assertEquals(
                1280,
                dimensions.width()
        );

        assertEquals(
                720,
                dimensions.height()
        );
    }

    @Test
    void downsizesVerticalFullHdTo720By1280() {
        AwardVideoProcessor.Dimensions dimensions =
                processor.targetDimensions(
                        1080,
                        1920
                );

        assertEquals(
                720,
                dimensions.width()
        );

        assertEquals(
                1280,
                dimensions.height()
        );
    }

    @Test
    void doesNotUpscaleSmallVideo() {
        AwardVideoProcessor.Dimensions dimensions =
                processor.targetDimensions(
                        640,
                        360
                );

        assertEquals(
                640,
                dimensions.width()
        );

        assertEquals(
                360,
                dimensions.height()
        );
    }

    @Test
    void keepsCompatible720pH264Mp4WithAacAudio() {
        AwardVideoProcessor.Probe probe =
                new AwardVideoProcessor.Probe(
                        45.2d,
                        1280,
                        720,
                        "h264",
                        "yuv420p",
                        "mov,mp4,m4a,3gp,3g2,mj2",
                        "aac"
                );

        assertTrue(
                processor.canKeepOriginal(
                        probe
                )
        );
    }

    @Test
    void keepsCompatibleVideoWithoutAudio() {
        AwardVideoProcessor.Probe probe =
                new AwardVideoProcessor.Probe(
                        20.0d,
                        720,
                        1280,
                        "h264",
                        "yuv420p",
                        "mov,mp4,m4a,3gp,3g2,mj2",
                        null
                );

        assertTrue(
                processor.canKeepOriginal(
                        probe
                )
        );
    }

    @Test
    void transcodesFullHdVideo() {
        AwardVideoProcessor.Probe probe =
                new AwardVideoProcessor.Probe(
                        30.0d,
                        1920,
                        1080,
                        "h264",
                        "yuv420p",
                        "mov,mp4,m4a,3gp,3g2,mj2",
                        "aac"
                );

        assertFalse(
                processor.canKeepOriginal(
                        probe
                )
        );
    }

    @Test
    void transcodesHevcEvenWhenResolutionIsAlready720p() {
        AwardVideoProcessor.Probe probe =
                new AwardVideoProcessor.Probe(
                        30.0d,
                        1280,
                        720,
                        "hevc",
                        "yuv420p",
                        "mov,mp4,m4a,3gp,3g2,mj2",
                        "aac"
                );

        assertFalse(
                processor.canKeepOriginal(
                        probe
                )
        );
    }

    @Test
    void transcodesNonAacAudio() {
        AwardVideoProcessor.Probe probe =
                new AwardVideoProcessor.Probe(
                        30.0d,
                        1280,
                        720,
                        "h264",
                        "yuv420p",
                        "mov,mp4,m4a,3gp,3g2,mj2",
                        "mp3"
                );

        assertFalse(
                processor.canKeepOriginal(
                        probe
                )
        );
    }

    @Test
    void transcodesIncompatiblePixelFormat() {
        AwardVideoProcessor.Probe probe =
                new AwardVideoProcessor.Probe(
                        30.0d,
                        1280,
                        720,
                        "h264",
                        "yuv420p10le",
                        "mov,mp4,m4a,3gp,3g2,mj2",
                        "aac"
                );

        assertFalse(
                processor.canKeepOriginal(
                        probe
                )
        );
    }

    @Test
    void transcodesNonMp4Container() {
        AwardVideoProcessor.Probe probe =
                new AwardVideoProcessor.Probe(
                        30.0d,
                        1280,
                        720,
                        "h264",
                        "yuv420p",
                        "matroska,webm",
                        "aac"
                );

        assertFalse(
                processor.canKeepOriginal(
                        probe
                )
        );
    }
}