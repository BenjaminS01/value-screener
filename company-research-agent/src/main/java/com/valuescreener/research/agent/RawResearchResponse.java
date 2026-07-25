package com.valuescreener.research.agent;

import java.util.List;

record RawResearchResponse(
        String summary,
        String valueTrapAssessment,
        List<RawSourceReference> sources,
        boolean noReliableReportFound) {

    record RawSourceReference(String url, String claim) {
    }
}
