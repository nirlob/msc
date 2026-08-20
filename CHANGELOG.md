# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- **Scope reduction**: the public surface is now a single endpoint,
  `POST /msc/send`. All other endpoints (discovery, capabilities, status,
  list, decisions, audit) have been removed. They will be added in future
  revisions **only if needed**.
- `openapi.yaml`, the RFC (§5), the Java controllers, services and
  repository, and the smoke test have all been updated to match the
  minimal contract.
- Rate limiting (`RateLimitFilter`, `rate_buckets` table, `429` handling)
  has been removed in this revision. It will be reintroduced as a
  filter ahead of `CachedBodyFilter` when the deployment surface grows.

### Added
- Initial reference implementation of the MSC (Model Solution Client) protocol
  on Java / Spring Boot.
- Contract-first build: `openapi.yaml` is the source of truth; controllers
  and DTOs are regenerated on every build via the official
  `openapi-generator-maven-plugin`.
- SQLite-backed persistence with append-only audit log.
- Ed25519 signature verification on `POST /msc/send`, with a
  body-cache filter that captures the raw bytes for the verifier.
- Auto-reject filters (credit-card shape, US-SSN shape, leaked secrets).
- Auto-accept policy (`confidence ≥ 0.7` + safe type + anonymous).
- End-to-end smoke test (`scripts/smoke_test.py`).
- Documentation: `README.md`, `AGENTS.md`, `msc-rfc-protocol.md`,
  `LICENSE`.

### Notes
- This is the Java / Spring Boot port of the original MSC reference; the
  protocol specification (`msc-rfc-protocol.md`, `draft-msc-protocol-00`)
  is shared across implementations.
- API stability follows the MSC protocol version (`msc_version: "0.1"`
  in the payload). Bumps will be coordinated with the RFC.
- The reference deliberately keeps authentication and model metadata
  orthogonal: `MSC-Origin` is the authenticated identity, while
  `Contribution.context.model.{provider,id,version}` is informational
  telemetry that does not affect signature continuity. See RFC §5.4.