# AWS Architecture Overview

Quick-reference sketch of the value-screener AWS deployment (Phase 4). For full reasoning and the
Decision Log, see [`docs/superpowers/specs/2026-09-20-aws-deployment-design.md`](../superpowers/specs/2026-09-20-aws-deployment-design.md)
and [`docs/superpowers/plans/2026-09-20-aws-deployment-plan.md`](../superpowers/plans/2026-09-20-aws-deployment-plan.md).
Region: `eu-central-1`.

```
                         GITHUB (Code + CI/CD)
                                 │
                    git tag v1.0.0 → push
                                 │
                                 ▼
                   GitHub Actions (Auth: OIDC → IAM-Rolle, kein Access Key)
                    Docker-Image bauen → ECR-Push → ECS-Deploy auslösen
                                 │
                                 ▼
                              ┌─────┐
                              │ ECR │
                              └──┬──┘
                                 │
                    ═══════════════════════════════
                                  INTERNET
                    ═══════════════════════════════
                 │                                                │
        (Browser: Frontend)                            (Browser/API: Backend)
                 ▼                                                ▼
          CloudFront + S3                          API Gateway (HTTP API)
           (Frontend)                             ┌──────────┴──────────┐
                                                    │                     │
                                             VPC Link (ECS)      Lambda-Integration
                                                    │             (Demo-Button, throttled)
                                                    ▼                     │
        ═══════════ VPC: value-screener-vpc (privat, kein IGW/NAT) ═══════════
        ║  2 private Subnetze über 2 AZs                                      ║
        ║  ECS-Task-SG → RDS-SG → RDS PostgreSQL (Single-AZ, verschlüsselt)   ║
        ║  ECS Fargate Task (Backend-Container)                               ║
        ║  VPC-Endpoints: Secrets Manager, ggf. Lambda                        ║
        ═══════════════════════════════════════════════════════════════════

   ── Separat, außerhalb der VPC ──────────────────────────────────────────
   Company Research Agent (Lambda, echt)        Demo-Lambdas (2×, klein)
   ← manueller Trigger, nie öffentlich           ← Button-Aufruf (ratenbegrenzt)
   ← SQS Dead Letter Queue                       ← EventBridge (täglich) → schreibt
                                                     Heartbeat nach DynamoDB
                                                   ← 2. Lambda liest Heartbeat
                                                     für die Landingpage-Anzeige
   Beide: SQS DLQ bei Fehlern
```

## Rundherum, service-übergreifend

- **IAM** — Root/MFA, Alltags-User ohne Access Key, `Admin` nur mit frischer MFA (< 20 Min.),
  GitHub-OIDC-Rolle für CI/CD
- **AWS Budgets** — 40 %/80 %-Alarme + automatische Kosten-Notbremse (Deny-Policy bei 100 %)
- **Secrets Manager** — RDS-Zugangsdaten (aktiv), später Anthropic-API-Key
- **DynamoDB** — Rate-Limit-Zähler für den Demo-Button + EventBridge-Heartbeat
- **SQS** — Dead Letter Queues für alle Lambdas (Zuverlässigkeit, kein Selbstzweck)
- **CloudTrail, Cognito, KMS, X-Ray** — noch offene Themen

## Bewusst NICHT dauerhaft aktiv

WAF, GuardDuty, AWS Config, RDS Multi-AZ, NAT Gateway — echte laufende Kosten, nicht mit dem
~20-€-Budget vereinbar im Dauerbetrieb. Siehe Design-Spec Abschnitt 9.

## Baustand

| Bereich | Status |
|---|---|
| Account-Setup (Root/MFA, IAM, Budget) | ✅ fertig |
| VPC & Security Groups | ✅ fertig |
| RDS | ✅ fertig |
| ECS-Backend (Thema 4) | 🔄 in Arbeit — Dockerfile steht |
| Lambda/EventBridge/Demo/SQS (Thema 5) | ⏳ offen |
| Alles Weitere (Frontend-Hosting, Cognito, CloudTrail, X-Ray, CDK, ...) | ⏳ offen |

Details und laufend aktualisierter Fortschritt: siehe die Plan-Datei (verlinkt oben).
