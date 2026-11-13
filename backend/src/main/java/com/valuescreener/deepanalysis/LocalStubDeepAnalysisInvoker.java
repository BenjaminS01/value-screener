package com.valuescreener.deepanalysis;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/** Answers without AWS, and says so. Active with app.deep-analysis.mode=stub. */
public class LocalStubDeepAnalysisInvoker implements DeepAnalysisInvoker {

    private final Clock clock;

    public LocalStubDeepAnalysisInvoker(Clock clock) {
        this.clock = clock;
    }

    @Override
    public DeepAnalysisResult invoke(String isin) {
        String normalized = isin.trim().toUpperCase(Locale.ROOT);
        return new DeepAnalysisResult(
                "NOT_IMPLEMENTED",
                "Noch nicht implementiert – hier wird künftig eine Tiefenanalyse für " + normalized + " ausgegeben.",
                "Lokale Simulation – keine AWS-Lambda aufgerufen",
                "local-stub", "local", "local", 0,
                UUID.randomUUID().toString(), false, clock.instant(),
                DeepAnalysisResult.Source.LOCAL_STUB);
    }
}
