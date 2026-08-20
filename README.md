# MSC Reference Server (Java / Spring Boot)

Reference implementation of the **MSC (Model Solution Client)** protocol,
defined in `msc-rfc-protocol.md` (`draft-msc-protocol-00`).

This project is the Java / Spring Boot port of the MSC reference. The HTTP
contract is **contract-first**: `openapi.yaml` is the single source of truth,
and Spring controllers + DTOs are generated from it on every build via the
official `openapi-generator-maven-plugin`.

---

## What MSC is

MSC is the protocol counterpart of MCP. Where MCP lets an LLM *consume*
resources from the web (read), MSC lets an LLM *emit* contributions back to a
web site (write), with the user's consent and under the receiving site's
moderation.

A contribution is a JSON document signed with Ed25519, posted to a site's
discovery endpoint. The site decides whether to accept, reject, or queue it
for human review, then records the decision in an append-only audit log.

See [`msc-rfc-protocol.md`](./msc-rfc-protocol.md) for the full specification.

---

## Project layout

```
msc-rfc-protocol.md    Protocol specification (English).
openapi.yaml           OpenAPI 3.1 contract. Source of truth for HTTP API.
pom.xml                Maven build with openapi-generator plugin.
mvnw, mvnw.cmd         Maven Wrapper (no Maven install required).
payload.json           (gitignored) example payload — auto-accept.
payload-alt.json       (gitignored) example payload — queued for review.
.gitignore             Ignores build artefacts and local fixtures.

src/main/java/io/msc/
├── MscReferenceServerApplication.java   Spring Boot entry point.
├── api/
│   ├── DiscoveryController             GET /.well-known/msc.json, /msc/capabilities, /msc/policy.
│   ├── SubmissionController             POST /msc/contributions, GET /msc/status/{id}.
│   ├── ModerationController             GET /msc/contributions, POST .../decision.
│   ├── AuditController                  GET /msc/audit.
│   ├── CachedBodyFilter                 Caches raw request body for signature verification.
│   ├── RateLimitFilter                  Per-IP / per-origin 100/h rate limit.
│   ├── exception/
│   │   ├── MscException                 Domain exception → mapped to HTTP via GlobalExceptionHandler.
│   │   └── GlobalExceptionHandler       Maps exceptions to JSON error bodies.
│   └── generated/
│       └── ApiUtil.java                 Stub for the generator's helper class.
├── config/
│   └── MscConfig.java                   DataSource (SQLite), MscRepository, ContributionService beans.
├── domain/
│   ├── Contribution.java                Mutable status + immutable payload fields.
│   ├── AuditEntry.java                  Append-only audit row.
│   ├── Decision.java                    AUTO_ACCEPT / AUTO_REJECT / ACCEPT / REJECT / EDIT / WITHDRAW.
│   └── OriginKey.java                   Registered public key for an origin.
├── security/
│   ├── Ed25519Verifier.java             Wire-format signature verification.
│   └── Base64Url.java                   base64url encoder/decoder.
├── service/
│   └── ContributionService.java         Policy engine: signature, auto-reject, auto-accept, queue, decide.
└── storage/
    └── MscRepository.java               SQLite repository (5 tables, append-only audit).

src/main/resources/
└── application.properties              server.port, msc.db.path.

scripts/
└── smoke_test.py                        End-to-end smoke test (Python client).
```

Generated sources live in `target/generated-sources/openapi/`. They are
**not** committed; they are regenerated on every `mvn` invocation. The
generated package is `io.msc.api.generated` (controllers / interfaces) and
`io.msc.api.generated.model` (DTOs).

---

## Quickstart

### Prerequisites

- JDK 21+ (`java -version`)
- The bundled Maven Wrapper handles Maven itself.

### Run the server

```bash
./mvnw -B -ntp spring-boot:run
```

The server listens on `http://localhost:8000`. SQLite is created at
`./msc.db` (configured via `msc.db.path` in `application.properties`).

### Hit the endpoints with curl

```bash
# Discovery
curl -s http://127.0.0.1:8000/.well-known/msc.json | jq .

# Capabilities
curl -s http://127.0.0.1:8000/msc/capabilities | jq .

# Policy
curl -s http://127.0.0.1:8000/msc/policy | jq .

# Audit log (empty at first)
curl -s http://127.0.0.1:8000/msc/audit | jq .
```

### Smoke test (signed POST)

```bash
./mvnw spring-boot:run &     # one terminal
python3 scripts/smoke_test.py
```

The script:

1. Generates an Ed25519 keypair.
2. Registers the public key in `./msc.db` (the `keys` table).
3. Reads `payload.json`, signs the bytes, and POSTs to `/msc/contributions`.
4. Verifies `/msc/status/{id}`, `/msc/contributions`, and `/msc/audit`.

Try both flows:

```bash
python3 scripts/smoke_test.py payload.json        # conf 0.85 + comment  → accepted
python3 scripts/smoke_test.py payload-alt.json    # conf 0.40 + edit     → queued
```

### Build only

```bash
./mvnw -B -ntp clean package
```

Produces `target/msc-reference-server-0.1.0.jar`. The OpenAPI client and
DTOs are regenerated automatically as part of the `generate-sources`
phase, which runs before `compile`.

---

## How the contract is enforced

`openapi.yaml` is the single source of truth. The `openapi-generator-maven-plugin`
reads it on every build and generates:

- **Interfaces** in `io.msc.api.generated.*` (one per tag: `DiscoveryApi`,
  `SubmissionApi`, `ModerationApi`, `AuditApi`).
- **DTOs** in `io.msc.api.generated.model.*` (`Contribution`, `Source`,
  `Context`, `SubmitResponse`, `StatusResponse`, `DecisionRequest`, etc.).

Each hand-written controller in `io.msc.api` implements one of the generated
interfaces. This means:

- The HTTP path, method, headers, query parameters, request body schema,
  and response codes are all defined in YAML.
- Adding or renaming a field requires editing `openapi.yaml` *and*
  regenerating; you cannot drift from the contract by mistake.
- The `openapi.yaml` is human-readable in code review and can be fed to
  any OpenAPI tooling (Swagger UI, Redoc, Postman, other-language client
  generators).

Generated examples via the live server:

- **Swagger UI**: `http://127.0.0.1:8000/docs` *(requires adding
  `springdoc-openapi-starter-webmvc-ui`; not bundled here to keep deps
  minimal — the contract is already verified by `test_openapi_e2e.py`
  in the original Python reference, equivalent to the YAML contract).*
- **Raw spec**: `http://127.0.0.1:8000/openapi.json` *(Spring Boot generates
  it from the controllers; it matches the `openapi.yaml` shape by
  construction because every controller implements a generated interface).*

---

## Decision pipeline

For every incoming `POST /msc/contributions`:

1. **Rate limit** filter bumps the per-IP and per-origin buckets, returns
   `429` with `RateLimit-Limit` / `RateLimit-Remaining` headers.
2. **Body cache** filter reads the raw bytes once into a `ThreadLocal` so
   the controller can verify the signature against the *exact* bytes
   that the client signed (otherwise Jackson would re-encode and the
   signature would not match).
3. **Signature verification**: `Ed25519Verifier.verify(publicKey, body, sig)`.
   The public key is fetched from the `keys` table by `MSC-Origin` header.
   Returns `401` on failure.
4. **Deduplication**: `INSERT` into `contributions` is unique on `id`.
   A duplicate id returns `409`.
5. **Auto-reject filters**: credit-card-shaped digit runs, US-SSN shape,
   `password=...`, `api_key=...`. Match → `202 status=rejected` with
   reason.
6. **Auto-accept policy**: `confidence ≥ 0.7` *and* `type ∈ {comment, related}`
   *and* `user_attribution ≠ real_name` → `202 status=accepted`.
7. **Otherwise**: `202 status=queued` for human review.
8. Every decision appends an entry to `audit_log` (append-only, no UPDATE).

---

## Persistence

SQLite, single file (`./msc.db` by default).

| Table | Purpose |
|-------|---------|
| `contributions` | One row per submitted contribution. `status`, `decision`, `moderator_id` are mutable; everything else is set on insert. |
| `keys` | Registered `origin` → `ed25519:<base64url pub>`. `revoked` flag for key rotation. |
| `audit_log` | Append-only decision trail. `INSERT` only, never UPDATE / DELETE. |
| `rate_buckets` | Rolling counters keyed by `(bucket, window_start)`. |

Reset by deleting `msc.db`; the schema is recreated on the next boot.

---

## Configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `server.port` | `8000` | HTTP port |
| `msc.db.path` | `./msc.db` | SQLite file path |
| `logging.level.io.msc` | `INFO` | Service-level logging |

The two auto-policy thresholds (`AUTO_ACCEPT_CONFIDENCE = 0.7` and
`AUTO_REJECT_PATTERNS`) live in `ContributionService.java`. Change them there
and rebuild.

---

## What this is **not**

- **Not** a forum bot framework. It is a protocol and a reference server,
  not a scraper or auto-poster.
- **Not** an attempt to "fix" the open-web collapse on its own. It is one
  primitive of many that would be needed.
- **Not** a production deployment. Use it to learn the protocol and to
  prototype; harden it (auth, rate limits, multi-process) before exposing
  it publicly.
- **Not** an alternative to moderation. Every contribution either passes
  an automatic policy or lands in front of a human moderator.

---

## License

- **Code**: MIT.
- **Protocol spec** (`msc-rfc-protocol.md`): CC-BY-4.0.
- **OpenAPI contract** (`openapi.yaml`): MIT.