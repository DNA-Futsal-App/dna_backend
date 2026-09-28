package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.config.AwardRegistrationProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                            "slow",
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
                processor.targetDimensions(3840, 2160);

        assertEquals(1280, dimensions.width());
        assertEquals(720, dimensions.height());
    }

    @Test
    void downsizesVerticalFullHdTo720By1280() {
        AwardVideoProcessor.Dimensions dimensions =
                processor.targetDimensions(1080, 1920);

        assertEquals(720, dimensions.width());
        assertEquals(1280, dimensions.height());
    }

    @Test
    void doesNotUpscaleSmallVideo() {
        AwardVideoProcessor.Dimensions dimensions =
                processor.targetDimensions(640, 360);

        assertEquals(640, dimensions.width());
        assertEquals(360, dimensions.height());
    }
}
