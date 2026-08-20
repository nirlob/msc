# draft-msc-protocol-00

## MSC: Model Solution Client Protocol

---

## Status of this Memo

This document is an Internet-Draft specification. It reflects the current consensus of the authors and is open for review. Distribution of this memo is unlimited.

**Category:** Standards Track (Experimental)
**Version:** 00
**Date:** 2026-08-20
**Authors:** [TBD]

---

## Abstract

MSC (Model Solution Client) is an open protocol that allows a language-model-based assistant (LLM) to propose contributions — corrections, improvements, alternative solutions, translations, code examples — to a forum, wiki, or web site that opts in to receive them, in a structured, signed, and moderated fashion.

MSC is the complement of the Model Context Protocol (MCP): where MCP describes how an LLM *consumes* resources from the web, MSC describes how an LLM *emits* resources back to the web, with explicit user consent and under the receiving site's control.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Terminology](#2-terminology)
3. [Overview](#3-overview)
4. [Use Cases](#4-use-cases)
5. [Specification](#5-specification)
   - 5.1 [Discovery](#51-discovery)
   - 5.2 [Endpoints](#52-endpoints)
   - 5.3 [Authentication and Signing](#53-authentication-and-signing)
   - 5.4 [Payload](#54-payload)
   - 5.5 [Contribution Types](#55-contribution-types)
   - 5.6 [Confidence Levels](#56-confidence-levels)
   - 5.7 [Rate Limiting](#57-rate-limiting)
6. [Moderation and Filtering](#6-moderation-and-filtering)
7. [Security Considerations](#7-security-considerations)
8. [Privacy Considerations](#8-privacy-considerations)
9. [Reputation and Trust](#9-reputation-and-trust)
10. [Relationship with MCP](#10-relationship-with-mcp)
11. [Examples](#11-examples)
12. [IANA Considerations](#12-iana-considerations)
13. [Future Work](#13-future-work)
14. [References](#14-references)
15. [Appendix A. Reference Implementation](#appendix-a-reference-implementation)
16. [Appendix B. Changes Since Previous Version](#appendix-b-changes-since-previous-version)

---

## 1. Introduction

The generative-AI ecosystem has produced a growing asymmetry: users increasingly consume knowledge through LLMs, but contribute back far less knowledge to the open web. The result is a progressive collapse of the *commons* (technical forums, wikis, Q&A sites) that has historically fed model training itself.

MSC addresses this asymmetry by defining a standardised, secure, and reversible channel through which contributions derived from a human–LLM conversation can flow back to the originating site, subject to the receiver's moderation.

The protocol is designed to be:

- **Minimal**: a small set of primitives, implementable in a weekend.
- **Safe**: every contribution is signed and attributed.
- **Reversible**: the receiver decides what to accept, edit, or reject.
- **Opt-in**: neither the user nor the site is obliged to participate.

---

## 2. Terminology

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119].

- **MSC Client**: software embedded in an assistant or LLM that prepares and sends contributions.
- **MSC Server**: an HTTP endpoint exposed by a web site (forum, wiki, blog) that receives contributions.
- **Source**: the original web resource that motivated the contribution (canonical URL).
- **Contribution**: a proposal for a change, correction, or supplement sent by an MSC Client.
- **Origin**: the identity that signs the contribution. May be the model, the user, or both.
- **Receiver**: the MSC Server that decides what to do with the contribution.

---

## 3. Overview

```
┌──────────────┐                  ┌──────────────┐
│   User       │                  │  Web site    │
│              │   Signed POST    │  (forum/wiki)│
│  ┌────────┐  │ ────────────────▶│ ┌──────────┐ │
│  │  LLM   │  │                  │ │ MSC srv  │ │
│  │ + MSC  │  │ ◀──── 202 ──────│ │ + filter │ │
│  │ client │  │   (accepted)     │ │ + queue  │ │
│  └────────┘  │                  │ └──────────┘ │
│              │                  │       │      │
│              │                  │       ▼      │
│              │                  │   Human      │
│              │                  │   moderator  │
└──────────────┘                  └──────────────┘
```

The MSC Client:

1. Detects that the user has produced or endorsed an improvement relative to a web source.
2. Packages it as a `Contribution`.
3. Requests explicit user consent (preview, then accept / edit / cancel).
4. Signs the contribution with the Origin's identity.
5. POSTs to the MSC Server endpoint of the site.
6. Optionally polls status (`accepted`, `rejected`, `published`, `edited`).

The MSC Server:

1. Validates the signature.
2. Applies automatic filters (length, language, banned terms, blocked models, etc.).
3. Queues for human moderation or auto-publishes according to site policy.
4. Returns an identifier and current state.

---

## 4. Use Cases

- **UC-1**: During a pair-programming session with an assistant, the user corrects a suggestion. `/msc publish stackoverflow --as comment` sends it to the original post.
- **UC-2**: The assistant synthesises an answer from three Reddit threads. The user asks it to publish each finding as a related link in the corresponding threads.
- **UC-3**: In a collaborative wiki, an LLM-backed bot proposes an edit to a low-activity page. The edit enters a human-review queue.
- **UC-4**: The assistant contributes a better translation of an open-source article under CC-BY-SA. The translation is sent to the original site for review.
- **UC-5**: During research, the assistant finds an answer behind a paywall. The user cannot publish (no MSC API), but the assistant internally flags the correction as `non_publishable`.

---

## 5. Specification

### 5.1 Discovery

A web site that supports MSC MUST publish a manifest at `/.well-known/msc.json` of the following form:

```json
{
  "msc_version": "0.1",
  "endpoint": "https://api.example.com/msc/contributions",
  "status_endpoint": "https://api.example.com/msc/status",
  "auth_methods": ["ed25519", "jwt"],
  "accepted_types": ["comment", "edit", "alternative", "translation"],
  "policy_url": "https://example.com/msc/policy",
  "rate_limit": {
    "per_ip": 100,
    "per_model": 1000,
    "window": "1h"
  }
}
```

### 5.2 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/msc/contributions` | Submit a new contribution |
| GET | `/msc/status/{id}` | Query the status of a contribution |
| GET | `/msc/capabilities` | (Optional) Human-readable summary of capabilities |
| GET | `/msc/reputation/{origin}` | (Optional) Origin reputation |

### 5.3 Authentication and Signing

Every request MUST include:

- Header `MSC-Origin`: identifier of the origin (model and/or user).
- Header `MSC-Origin-Key`: the public key used to verify the signature.
- Header `MSC-Signature`: signature over the request body.

Example:

```
POST /msc/contributions HTTP/1.1
Host: api.example.com
Content-Type: application/json
MSC-Origin: claude-opus-4.1:user:abc123
MSC-Origin-Key: ed25519:MCowBQYDK2VwAyEAGb9ECWmEzf...
MSC-Signature: ed25519:MEUCIQCx...

{ ...payload... }
```

The recommended signature scheme is Ed25519 over the raw UTF-8 bytes of the request body, encoded as base64url. JWT MAY be supported as an alternative for sites that prefer a token-based flow.

Public keys MAY be published at `GET /msc/keys/{origin}` or in a federated registry (out of scope for this document).

### 5.4 Payload

```json
{
  "msc_version": "0.1",
  "id": "01JABCDEF...",          // ULID, generated by the client
  "timestamp": "2026-08-20T10:30:00Z",
  "source": {
    "url": "https://stackoverflow.com/q/123",
    "canonical": "https://stackoverflow.com/q/123#answer-456",
    "hash": "sha256:9f86d081...",     // Optional: hash of the fetched source
    "fetched_at": "2026-08-20T10:00:00Z"
  },
  "type": "comment",            // See 5.5
  "body": "Markdown or plain text",
  "language": "es",
  "confidence": 0.85,           // See 5.6
  "user_attribution": "anonymous",  // "anonymous" | "username" | "real_name"
  "license": "CC-BY-SA-4.0",
  "tags": ["python", "performance"],
  "context": {
    "model_id": "claude-opus-4.1",
    "session_hash": "sha256:abcd...", // One-way hash of the session id
    "consent_token": "user-issued-opaque-xyz"
  }
}
```

### 5.5 Contribution Types

| Type | Description | Typical use |
|------|-------------|-------------|
| `comment` | Comment on the source resource | Corrections, clarifications |
| `edit` | Proposed edit to the source | Text improvements, typos |
| `alternative` | Alternative solution | Another way to solve it |
| `translation` | Translation | i18n |
| `related` | Related link or reference | Cross-link |
| `example` | Additional example | Code, data |

### 5.6 Confidence Levels

`confidence` is a float in `[0.0, 1.0]` assigned by the client. Receivers MAY use it as a filter:

- `[0.0, 0.4)`: low — mandatory human review.
- `[0.4, 0.7)`: medium — standard review queue.
- `[0.7, 0.9)`: high — auto-publish if origin has good reputation.
- `[0.9, 1.0]`: very high — auto-publish unless site policy forbids.

### 5.7 Rate Limiting

Servers MUST return standard `RateLimit-*` headers as defined in [RFC 9239] and respond with `429 Too Many Requests` when the limits declared in `.well-known/msc.json` are exceeded.

---

## 6. Moderation and Filtering

The MSC Server MUST allow configuring, per origin or globally:

```yaml
auto_accept:
  if:
    confidence: ">= 0.8"
    origin_reputation: ">= 0.7"
    type: ["comment", "related"]
    user_karma: ">= 100"
auto_reject:
  if:
    contains: ["personal data", "credit card", "ssn"]
    matches_regex: ["\\b\\d{16}\\b"]
    origin_in_blocklist: ["spam-bot-v1"]
queue_for_human_review:
  default: true
  on_disagreement_with_source: true
```

The server MUST log every decision to an append-only audit log containing:

- `contribution_id`
- `decision` (`accepted` | `rejected` | `edited` | `escalated` | `withdrawn`)
- `reason`
- `moderator_id` (human id, or `auto-{rule_id}`)
- `timestamp`

---

## 7. Security Considerations

- **Prompt injection**: a contribution may contain adversarial text intended to confuse a human moderator or another LLM that later consumes the resource. Servers MUST render contributions in safe mode (sanitised markdown, no raw HTML by default) and moderators MUST see the original source side by side.
- **Origin spoofing**: Ed25519 signatures mitigate this, but servers MUST support key rotation and revocation.
- **Malicious replay**: an attacker could re-send a legitimate contribution to another site for spam or DoS. Each `contribution.id` MUST be globally unique (ULID + origin id) and servers MUST reject duplicates within a reasonable time window.
- **Code execution**: contributions with `type=example` containing code MUST NOT be executed server-side; they are treated as plain text.
- **Key compromise**: a leaked `MSC-Origin-Key` MUST be revocable; the server MUST publish a revocation list.

---

## 8. Privacy Considerations

- `session_hash` MUST be a one-way hash; it MUST NOT be reversible to the original conversation.
- The user MUST be able to revoke a `consent_token` after submission; the server MUST then move the contribution to `withdrawn` and hide it.
- Contributions are pseudo-anonymous by default: attributed to the model, not the user. Users MAY opt in to revealing identity.
- Servers MUST NOT use contribution contents for model training without an explicit ToS.
- Servers MUST support erasure under GDPR/LOPD within a reasonable timeframe (RECOMMENDED ≤ 30 days).

---

## 9. Reputation and Trust

MSC introduces two optional metrics that servers MAY query:

- **`origin_reputation`**: reputation of the pair `model_id + user_id`, computed from accepted vs. rejected contributions over time, with exponential decay.
- **`source_consensus`**: degree to which multiple independent contributions on the same source agree — a signal that the correction is sound.

Servers MAY share these metrics via:

```
GET /msc/reputation/{origin}
```

Cross-server reputation federation is out of scope for this document (Future Work).

---

## 10. Relationship with MCP

| | MCP | MSC |
|---|---|---|
| Direction | Web → LLM | LLM → Web |
| Initiator | Model | User (via model) |
| Use case | "Read this resource", "Run this tool" | "Publish this improvement" |
| Filtering | LLM chooses what to invoke | Site chooses what to accept |
| Status | Standardised | Proposed (this RFC) |

A conversational agent that supports both protocols can run a session in which it alternately *reads* (MCP) and *writes* (MSC) the web, completing the loop.

---

## 11. Examples

### 11.1 Publishing a Comment

```
User: the model says to use asyncio.gather, but TaskGroup is better for Python 3.11+. Publish this on the Stack Overflow post.
Assistant: I've prepared this contribution:

  Source: https://stackoverflow.com/q/123   Type:    comment
  Body:    "In Python 3.11+, asyncio.TaskGroup is preferable because..."
  Confidence: 0.82
  Attribution: anonymous

  Publish? [Yes] [Edit] [Cancel]
```

After confirmation:

```http
POST /msc/contributions HTTP/1.1
Host: api.stackoverflow.com
MSC-Origin: claude-opus-4.1:user:abc123
MSC-Origin-Key: ed25519:MCowBQ...
MSC-Signature: ed25519:MEUCIQCx...

{
  "msc_version": "0.1",
  "id": "01JAB7Y3K9...",
  "timestamp": "2026-08-20T10:30:00Z",
  "source": {
    "url": "https://stackoverflow.com/q/123",
    "canonical": "https://stackoverflow.com/q/123#a456"
  },
  "type": "comment",
  "body": "In Python 3.11+, `asyncio.TaskGroup` is preferable because...",
  "language": "es",
  "confidence": 0.82,
  "user_attribution": "anonymous",
  "license": "CC-BY-SA-4.0",
  "tags": ["python", "asyncio"]
}
```

Response:

```http
HTTP/1.1 202 Accepted
Content-Type: application/json
RateLimit-Limit: 100
RateLimit-Remaining: 99

{
  "id": "01JAB7Y3K9...",
  "status": "queued",
  "review_url": "https://stackoverflow.com/msc/review/01JAB7Y3K9"
}
```

### 11.2 Proposed Edit to a Wiki

```json
{
  "msc_version": "0.1",
  "id": "01JAB8Z...",
  "timestamp": "2026-08-20T10:35:00Z",
  "source": {
    "url": "https://wiki.example.org/article/X",
    "canonical": "https://wiki.example.org/article/X?rev=42"
  },
  "type": "edit",
  "body": "Section updated with the correction ...",
  "language": "es",
  "confidence": 0.75,
  "user_attribution": "username",
  "license": "CC-BY-SA-4.0",
  "context": {
    "model_id": "claude-opus-4.1",
    "session_hash": "sha256:abcd...",
    "diff": "@@ -10,3 +10,5 @@\n -old\n +new"
  }
}
```

---

## 12. IANA Considerations

This protocol registers the following:

- **Well-known URI**: `/.well-known/msc.json`
- **Media type**: `application/msc+json`
- **HTTP headers**: `MSC-Origin`, `MSC-Origin-Key`, `MSC-Signature`

---

## 13. Future Work

- Cross-server reputation federation.
- End-to-end encryption for sensitive contributions.
- Multimedia contributions (images, audio, video).
- Bidirectional MSC+MCP synchronisation within a single session.
- A "MSC browser extension" standard showing contribution status in the user's browser.

---

## 14. References

- [RFC 2119] Bradner, S., "Key words for use in RFCs to Indicate Requirement Levels", BCP 14, RFC 2119, March 1997.
- [RFC 9239] Polli, R., "Additional HTTP Status Codes for Rate Limiting Use Cases", RFC 9239, May 2022.
- [ULID] Spec at https://github.com/ulid/spec
- [MCP] Anthropic, "Model Context Protocol", https://modelcontextprotocol.io

---

## Appendix A. Reference Implementation

A reference MSC server (Python + FastAPI + SQLite) and a small client CLI live in this repository:

> `https://github.com/msc-protocol/reference-impl` *(pending publication)*

The repository includes a test forum based on self-hosted Discourse and a local LLM (Ollama) with the MSC client integrated.

---

## Appendix B. Changes Since Previous Version

N/A (initial version).

---

## License

This document is available under the [Creative Commons Attribution 4.0 License](https://creativecommons.org/licenses/by/4.0/).