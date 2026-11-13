# deep-analysis

Quarkus AWS Lambda `deep-analysis-demo` (Java 21, JVM packaging). Demo only: it answers
"Noch nicht implementiert ..." for a valid ISIN plus harmless Lambda metadata (function name, version,
region, memory, request id, cold start, time). It never returns the invoked ARN, account id, log
group/stream or other environment variables.

Request: `{ "isin": "US0378331005" }` (trimmed, uppercased, regex `[A-Z]{2}[A-Z0-9]{9}[0-9]`).
Invalid input returns `status: INVALID_REQUEST`, `message: Ungültige ISIN`.

## Tests

    mvn test

## Run locally (dev mode, mock event server)

    mvn quarkus:dev

    curl -X POST http://localhost:8090/ -H 'Content-Type: application/json' -d '{"isin":"US0378331005"}'

The dev mock event server runs on port 8090 (8080 is used by the backend, dev-up.sh); set via
`%dev.quarkus.lambda.mock-event-server.dev-port`. In test mode it listens on
`quarkus.lambda.mock-event-server.test-port` (default 8081).

## Deployment

Not part of this phase. Creating the Lambda, IAM role and permissions follows later with Terraform.
