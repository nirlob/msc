package io.msc.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry in the append-only audit log. Entries are never updated or
 * deleted; corrections are appended as new entries.
 */
public final class AuditEntry {

    private final long id;
    private final String contributionId;
    private final Decision decision;
    private final String reason;
    private final String moderatorId;
    private final Instant timestamp;

    public AuditEntry(long id, String contributionId, Decision decision,
                      String reason, String moderatorId, Instant timestamp) {
        this.id = id;
        this.contributionId = contributionId;
        this.decision = Objects.requireNonNull(decision);
        this.reason = reason;
        this.moderatorId = moderatorId;
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public long id() { return id; }
    public String contributionId() { return contributionId; }
    public Decision decision() { return decision; }
    public String reason() { return reason; }
    public String moderatorId() { return moderatorId; }
    public Instant timestamp() { return timestamp; }
}