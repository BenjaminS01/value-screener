package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchFreshnessTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    /** Like SpringApplication: Boot's conversion service turns "P6M" into a Period for @Value. */
    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()));
    }

    @Test
    void staleBeforeIsNineMonthsBack() {
        ResearchFreshness freshness = new ResearchFreshness(CLOCK, Period.parse("P9M"));

        assertThat(freshness.staleBefore()).isEqualTo(LocalDate.of(2026, 2, 12));
    }

    @Test
    void dateBeforeThresholdIsStale() {
        ResearchFreshness freshness = new ResearchFreshness(CLOCK, Period.parse("P9M"));

        assertThat(freshness.isStale(LocalDate.of(2026, 2, 11))).isTrue();
        assertThat(freshness.isStale(LocalDate.of(2026, 2, 12))).isFalse();
        assertThat(freshness.isStale(null)).isTrue();
    }

    @Test
    void thresholdComesFromPropertyAsIsoPeriod() {
        runner()
                .withBean(Clock.class, () -> CLOCK)
                .withBean(ResearchFreshness.class)
                .withPropertyValues("research.stale-after=P6M")
                .run(context -> assertThat(context.getBean(ResearchFreshness.class).staleBefore())
                        .isEqualTo(LocalDate.of(2026, 5, 12)));
    }

    @Test
    void defaultThresholdIsNineMonths() {
        runner()
                .withBean(Clock.class, () -> CLOCK)
                .withBean(ResearchFreshness.class)
                .run(context -> assertThat(context.getBean(ResearchFreshness.class).staleBefore())
                        .isEqualTo(LocalDate.of(2026, 2, 12)));
    }
}
