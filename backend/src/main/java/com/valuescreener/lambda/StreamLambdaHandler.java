package com.valuescreener.lambda;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.valuescreener.ValueScreenerApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lambda entry point behind the HTTP API (payload format 2.0). Spring starts once in the static initialiser,
 * which is what SnapStart snapshots after the init phase. Terraform handler:
 * {@code com.valuescreener.lambda.StreamLambdaHandler::handleRequest}.
 */
public class StreamLambdaHandler implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> HANDLER;

    static {
        try {
            HANDLER = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(ValueScreenerApplication.class);
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
