# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Initial reference implementation of the MSC (Model Solution Client) protocol
  on Java / Spring Boot.
- Contract-first build: `openapi.yaml` is the source of truth; controllers
  and DTOs are regenerated on every build via the official
  `openapi-generator-maven-plugin`.
- SQLite-backed persistence with append-only audit log.
- Ed25519 signature verification on `POST /msc/contributions`, with a
  body-cache filter that captures the raw bytes for the verifier.
- Auto-reject filters (credit-card shape, US-SSN shape, leaked secrets).
- Auto-accept policy (`confidence ≥ 0.7` + safe type + anonymous).
- Per-IP and per-origin rate limiting with `RateLimit-*` headers.
- End-to-end smoke test (`scripts/smoke_test.py`).
- Documentation: `README.md`, `AGENTS.md`, `msc-rfc-protocol.md`,
  `LICENSE`.

### Notes
- This is the Java / Spring Boot port of the original MSC reference; the
  protocol specification (`msc-rfc-protocol.md`, `draft-msc-protocol-00`)
  is shared across implementations.
- API stability follows the MSC protocol version (`msc_version: "0.1"`
  in the payload). Bumps will be coordinated with the RFC.