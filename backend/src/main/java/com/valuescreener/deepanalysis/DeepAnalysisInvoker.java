package com.valuescreener.deepanalysis;

/** Runs the deep-analysis demo for one ISIN. */
public interface DeepAnalysisInvoker {

    /** @throws DeepAnalysisUnavailableException if the analysis could not be obtained */
    DeepAnalysisResult invoke(String isin);
}
