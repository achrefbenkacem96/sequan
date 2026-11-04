# Sequan Architecture

## Overview

Sequan is a modular monolith:

- **Angular SPA** — authenticated client for escrow workflows
- **Spring Boot API** — domain logic, authorization, Stripe orchestration
- **PostgreSQL** — system of record for escrows, ledger, audit, webhooks

Money never becomes “true” because the UI says so. Stripe webhooks and transactional domain commands update balances.

## Escrow state machine

Valid transitions are enforced by `EscrowStateMachine`. Controllers never accept a raw `status` field.

Typical happy path:

`DRAFT → PENDING_PARTICIPANTS → PENDING_FUNDING → FUNDED → IN_PROGRESS → AWAITING_RELEASE → RELEASED`

Alternate paths include cancel/expire before funding, partial release, refund, and dispute resolution.

## Financial consistency

- Amounts are stored as **integer minor units** (`BIGINT`)
- Escrow invariant: `funded = released + refunded + available`
- Mutations go through `EscrowEntity.applyRelease` / `applyRefund` / `markFunded`
- Optimistic locking (`@Version`) reduces lost updates under concurrency
- Idempotency keys protect fund/release/refund retries
- Append-only `ledger_entries` provide a financial audit trail

## Authorization

- Global roles: `USER`, `ADMIN`
- Escrow-scoped participant roles: `CLIENT`, `PROVIDER`, `BENEFICIARY`, `APPROVER`, `ARBITRATOR`
- `EscrowAccessService` enforces object-level access and role checks

## Stripe integration

Sequan uses **Stripe Connect separate charges and transfers**:

1. Client funds via **Checkout Session** (embedded) onto the platform account
2. PaymentIntent uses `transfer_group = escrow_{id}`
3. Webhooks (`checkout.session.completed`, `payment_intent.*`) mark the escrow funded
4. Releases create **Transfers** to connected accounts
5. Refunds use the Stripe Refunds API against the original PaymentIntent

Important: Stripe does **not** provide legal escrow accounts. Sequan implements escrow-like delayed disbursement.

## Webhook reliability

`POST /api/webhooks/stripe`:

1. Verifies `Stripe-Signature`
2. Inserts `webhook_events.stripe_event_id` uniquely
3. Duplicate deliveries short-circuit with HTTP 200
4. Processing failures persist `FAILED` + error for safe retry
5. Payment success handlers are idempotent

## Observability

- Structured JSON logs via Logstash encoder
- `X-Request-Id` correlation filter + MDC
- Actuator health/info/metrics/prometheus
- OpenAPI at `/api/swagger-ui.html`
