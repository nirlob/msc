#!/usr/bin/env python3
"""End-to-end smoke test for the MSC Spring Boot server.

Reads a payload from a JSON file, signs it with Ed25519, registers the
public key in SQLite, POSTs the contribution to /msc/send, and prints the
server's response.

This server exposes a single public endpoint, so the smoke test is
deliberately small: register key, sign, POST, parse response. There is
no /msc/status, no /msc/audit, no /msc/list yet — those will arrive in
future revisions.

Usage:
    python3 scripts/smoke_test.py [path/to/payload.json]

Default payload: ./payload.json
"""
from __future__ import annotations

import base64
import json
import sqlite3
import sys
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import (
    Encoding, PrivateFormat, PublicFormat, NoEncryption,
)

BASE_URL = "http://127.0.0.1:8000"
DB_PATH = "msc.db"


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

    # 2) Register public key in SQLite (the reference server has no admin
    #    endpoint for key registration; this is intentional)
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

    # 4) POST to the only public endpoint
    req = urllib.request.Request(
        f"{BASE_URL}/msc/send",
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
            print(f"\n[send] HTTP {r.status}")
            print(r.read().decode())
    except urllib.error.HTTPError as e:
        print(f"\n[send] HTTP {e.code}: {e.read().decode()}")
        return 1

    # 5) Quick exploration of what the SQLite DB has recorded
    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()
    cur.execute("SELECT id, status, decision, moderator_id FROM contributions")
    print("\n[contributions] rows in msc.db:")
    for row in cur.fetchall():
        print(f"  {row[0]:<28} {row[1]:<10} {row[2] or '-':<14} {row[3] or '-'}")
    cur.execute("SELECT id, contribution_id, decision, moderator_id, reason "
                "FROM audit_log ORDER BY id DESC LIMIT 5")
    print("\n[audit_log] last 5 rows:")
    for row in cur.fetchall():
        print(f"  #{row[0]} cid={(row[1] or '-'):<28} {row[2]:<12} {row[3]:<14} {row[4] or ''}")
    con.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())