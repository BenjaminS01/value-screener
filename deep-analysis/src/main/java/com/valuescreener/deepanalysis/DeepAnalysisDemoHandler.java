package com.valuescreener.deepanalysis;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Demo Lambda: answers "not implemented yet" plus harmless runtime metadata from the Lambda Context.
 * Deliberately NOT returned: invoked function ARN (contains the account id), log group/stream, other env vars.
 */
@Named("deep-analysis-demo")
public class DeepAnalysisDemoHandler implements RequestHandler<DeepAnalysisRequest, DeepAnalysisResponse> {

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    /** True until the first invocation of this execution environment. */
    private static final AtomicBoolean FIRST_INVOCATION = new AtomicBoolean(true);

    static void resetColdStartForTest() {
        FIRST_INVOCATION.set(true);
    }

    @Override
    public DeepAnalysisResponse handleRequest(DeepAnalysisRequest request, Context context) {
        boolean coldStart = FIRST_INVOCATION.getAndSet(false);
        String isin = request == null || request.isin() == null
                ? ""
                : request.isin().trim().toUpperCase(Locale.ROOT);
        boolean valid = ISIN.matcher(isin).matches();

        return new DeepAnalysisResponse(
                valid ? "NOT_IMPLEMENTED" : "INVALID_REQUEST",
                valid
                        ? "Noch nicht implementiert – hier wird künftig eine Tiefenanalyse für " + isin + " ausgegeben."
                        : "Ungültige ISIN",
                "Hallo von AWS Lambda",
                context.getFunctionName(),
                context.getFunctionVersion(),
                region(),
                context.getMemoryLimitInMB(),
                context.getAwsRequestId(),
                coldStart,
                Instant.now().toString());
    }

    private static String region() {
        String region = System.getenv("AWS_REGION");
        return region == null || region.isBlank() ? "unknown" : region;
    }
}
