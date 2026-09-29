package br.com.dnafutsal.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AwardVideoAsyncConfiguration {

    @Bean(name = "awardVideoExecutor")
    public Executor awardVideoExecutor(
            AwardRegistrationProperties properties
    ) {
        int workers =
                properties.effectiveMaxConcurrentTranscodes();

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(
                workers
        );

        executor.setMaxPoolSize(
                workers
        );

        executor.setQueueCapacity(
                100
        );

        executor.setThreadNamePrefix(
                "award-video-"
        );

        executor.initialize();

        return executor;
    }
}