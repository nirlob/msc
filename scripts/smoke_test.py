#!/usr/bin/env python3
"""End-to-end smoke test for the MSC Spring Boot server.

Reads a payload from a JSON file, signs it with Ed25519, registers the
public key in SQLite, POSTs the contribution, and verifies status / list / audit.

Usage:
    python3 scripts/smoke_test.py [path/to/payload.json]

Default payload: ./payload.json
"""
from __future__ import annotations

import base64
import json
import os
import sqlite3
import sys
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import (
    Encoding, PrivateFormat, PublicFormat, NoEncryption,
)

BASE_URL = os.environ.get("MSC_BASE", "http://127.0.0.1:8000")
DB_PATH = os.environ.get("MSC_DB", "./msc.db")


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def main() -> int:
    payload_path = Path(sys.argv[1] if len(sys.argv) > 1 else "payload.json")
    print(f"Reading payload from {payload_path}")
    contribution = json.loads(payload_path.read_text())
    cid = contribution["id"]

    # 1) Generate Ed25519 keypair
    priv = Ed25519PrivateKey.generate()
    pub_raw = priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    pub_wire = "ed25519:" + b64url(pub_raw)
    origin = "claude-opus-4.1:demo-user"

    # 2) Register public key in SQLite
    con = sqlite3.connect(DB_PATH)
    con.execute(
        "INSERT OR REPLACE INTO keys(origin, public_key, revoked, added_at) "
        "VALUES (?, ?, 0, ?)",
        (origin, pub_wire, "2026-08-21T00:00:00Z"),
    )
    con.commit()
    con.close()
    print(f"Registered origin '{origin}' with pubkey {pub_wire[:30]}...")

    # 3) Sign the body bytes
    body_bytes = json.dumps(contribution).encode("utf-8")
    sig = priv.sign(body_bytes)
    sig_wire = "ed25519:" + b64url(sig)

    # 4) POST
    req = urllib.request.Request(
        f"{BASE_URL}/msc/contributions",
        data=body_bytes,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "MSC-Origin": origin,
            "MSC-Origin-Key": pub_wire,
            "MSC-Signature": sig_wire,
        },
    )
    try:
        with urllib.request.urlopen(req) as r:
            print(f"\n[submit] HTTP {r.status}")
            print(r.read().decode())
    except urllib.error.HTTPError as e:
        print(f"\n[submit] HTTP {e.code}: {e.read().decode()}")
        return 1

    # 5) Status
    with urllib.request.urlopen(f"{BASE_URL}/msc/status/{cid}") as r:
        print(f"\n[status] HTTP {r.status}")
        print(r.read().decode())

    # 6) List
    with urllib.request.urlopen(f"{BASE_URL}/msc/contributions?limit=5") as r:
        rows = json.loads(r.read())
        print(f"\n[list] {len(rows)} contribution(s):")
        for row in rows:
            print(f"  {row['id']:<28} {row['status']:<10} {row['origin']}")

    # 7) Audit
    with urllib.request.urlopen(f"{BASE_URL}/msc/audit?limit=5") as r:
        rows = json.loads(r.read())
        print(f"\n[audit] {len(rows)} entries:")
        for row in rows:
            print(f"  #{row['id']} {row['timestamp']} {row['decision']:<10} "
                  f"{row['moderator_id']:<14} "
                  f"{(row.get('contribution_id') or '-')[:26]} / "
                  f"{row.get('reason')}")

    return 0


if __name__ == "__main__":
    sys.exit(main())