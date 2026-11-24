package com.valuescreener.lambda;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.valuescreener.ValueScreenerApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lambda entry point behind the HTTP API (payload format 1.0). Spring starts once in the static initialiser,
 * which is what SnapStart snapshots after the init phase. Terraform handler:
 * {@code com.valuescreener.lambda.StreamLambdaHandler::handleRequest}.
 *
 * <p>Format 1.0, not 2.0: in format 2.0 API Gateway reads response headers only from the single-value
 * {@code headers} map, where this adapter keeps just the first value per name, so of the two Set-Cookie headers
 * on login (session + CSRF) the session cookie was lost. Format 1.0 reads {@code multiValueHeaders}.
 * Must match {@code payload_format_version} in infra/app/api.tf.
 */
public class StreamLambdaHandler implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> HANDLER;

    static {
        try {
            HANDLER = SpringBootLambdaContainerHandler.getAwsProxyHandler(ValueScreenerApplication.class);
        } catch (ContainerInitializationException e) {
            // Fail the init phase loudly: a half-started container must not be snapshotted.
            throw new IllegalStateException("Could not initialise Spring Boot application", e);
        }
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        HANDLER.proxyStream(input, output, context);
    }
}
