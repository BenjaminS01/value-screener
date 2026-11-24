# AWS Architecture Overview

Quick reference of the deployment **as built** (serverless, Terraform, live since 2026-11-24). Reasoning and
decisions: [`docs/superpowers/specs/2026-11-14-serverless-terraform-design.md`](../superpowers/specs/2026-11-14-serverless-terraform-design.md);
step-by-step history: the plans `2026-11-18-terraform-foundations.md` and `2026-11-22-terraform-data-app.md` in
`docs/superpowers/plans/`. The earlier ECS/RDS setup (2026-09-20 design) is deleted; see git history.
Region: `eu-central-1`.

```
                                  Browser
                                     │ HTTPS (default *.cloudfront.net certificate)
                                     ▼
          ┌──────────────── CloudFront (PriceClass_100) ────────────────┐
          │ default  → S3 value-screener-frontend-<account> (OAC, SPA)  │
          │ /api/*   → HTTP API, no cache, adds X-Origin-Verify header  │
          │ security headers: HSTS, strict CSP, frame DENY, nosniff     │
          └──────────────────────────────┬──────────────────────────────┘
                                         ▼
                 API Gateway HTTP API (payload 1.0, rate 5 / burst 10)
                                         │ ANY /api/{proxy+}
                                         ▼
                 Lambda value-screener-backend:live (Spring Boot, Java 21,
                 1024 MB, 29 s, SnapStart; rejects requests without the
                 origin header with 403)
                    │                │                     │
                    ▼                ▼                     ▼
             DynamoDB table   SSM Parameter Store   Lambda deep-analysis-demo:live
             value-screener   /value-screener/*     (Quarkus, 512 MB, 10 s)
             (20/20, PITR,    (hashes, origin
              TTL, protected)  secret, sync hash)

   CI/CD: git tag v* → GitHub Actions (OIDC → role value-screener-github-deploy, no keys)
          tests → zips to S3 value-screener-artifacts-<account> → update code → publish version
          → alias live; frontend dist → S3 sync → CloudFront invalidation
```

## Terraform stacks (`infra/`)

| Stack | State | Holds |
|---|---|---|
| `bootstrap` | local | state bucket `value-screener-tfstate-<account>` |
| `foundation` | S3 | artifact bucket, GitHub OIDC provider, deploy role (trust: `v*` tags, subject with owner/repo ids), empty backend role, budget 5 USD with alerts and automatic deny action |
| `data` | S3 | DynamoDB table (`prevent_destroy`), SSM secrets with write-only placeholders |
| `app` | S3 | both Lambdas + aliases + log groups (14 days), HTTP API, frontend bucket, CloudFront, origin secret, role policies |

`app` finds `data` and `foundation` resources by fixed names (data sources), never through another stack's state.
Terraform owns configuration; CI owns code (Lambda code and alias version are `ignore_changes`). A config change
(env var, SSM value) reaches `live` only with the next tag deploy.

## Around it

- **IAM:** no access keys. Humans: `aws login` + `admin` role with fresh MFA (< 20 min). CI: OIDC role, rights only
  for the two functions, the frontend bucket, invalidations and the artifact bucket.
- **Secrets:** SSM `SecureString` (AWS-managed key). Terraform writes only placeholders via `value_wo`; real values
  are entered in the console and never land in state (exception by design: the origin secret).
- **Costs:** idle ≈ 0 USD. DynamoDB provisioned 20/20 (free tier), no VPC, no NAT, no reserved concurrency (account
  quota 10). Budget 5 USD/month, alerts at 1 USD actual and 3 USD forecast, deny action at 5 USD.
- **Research data:** researched only locally (sandbox); reviewed snapshots go live with `scripts/push-research.sh`
  (sync token, `POST /api/research/sync/snapshots`). The ingest endpoint does not exist in AWS.

## Deliberately not used

WAF, GuardDuty, AWS Config, own domain / ACM certificate (viewer TLS minimum stays `TLSv1` with the default
certificate), access logs. Backlog: CloudFront flat-rate Free plan with WAF (eligibility unclear).
