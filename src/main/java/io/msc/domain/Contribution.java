package io.msc.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Stored representation of a contribution once it has been persisted by the
 * server. Mutable fields reflect the current decision state.
 *
 * Immutable fields: id, origin, payload fields, createdAt.
 * Mutable fields:    status, decision, reason, moderatorId, decidedAt.
 */
public final class Contribution {

    public enum Status { QUEUED, ACCEPTED, REJECTED, EDITED, WITHDRAWN }

    private final String id;
    private final String origin;
    private final String body;
    private final BigDecimal confidence;
    private final String contributionType;
    private final String language;
    private final String userAttribution;
    private final String license;
    private final List<String> tags;
    private final String sourceUrl;
    private final String sourceCanonical;
    private final String timestamp;
    private final Instant createdAt;

    private Status status = Status.QUEUED;
    private Decision decision;
    private String reason;
    private String moderatorId;
    private Instant decidedAt;

    public Contribution(String id, String origin, String body,
                        BigDecimal confidence, String contributionType,
                        String language, String userAttribution,
                        String license, List<String> tags,
                        String sourceUrl, String sourceCanonical,
                        String timestamp, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.origin = Objects.requireNonNull(origin);
        this.body = body;
        this.confidence = confidence;
        this.contributionType = contributionType;
        this.language = language;
        this.userAttribution = userAttribution;
        this.license = license;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.sourceUrl = sourceUrl;
        this.sourceCanonical = sourceCanonical;
        this.timestamp = timestamp;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public String id() { return id; }
    public String origin() { return origin; }
    public String body() { return body; }
    public BigDecimal confidence() { return confidence; }
    public String contributionType() { return contributionType; }
    public String language() { return language; }
    public String userAttribution() { return userAttribution; }
    public String license() { return license; }
    public List<String> tags() { return tags; }
    public String sourceUrl() { return sourceUrl; }
    public String sourceCanonical() { return sourceCanonical; }
    public String timestamp() { return timestamp; }
    public Instant createdAt() { return createdAt; }
    public Status status() { return status; }
    public Decision decision() { return decision; }
    public String reason() { return reason; }
    public String moderatorId() { return moderatorId; }
    public Instant decidedAt() { return decidedAt; }

    public void recordDecision(Decision decision, String reason,
                               String moderatorId, Instant when) {
        this.decision = decision;
        this.reason = reason;
        this.moderatorId = moderatorId;
        this.decidedAt = when;
        this.status = switch (decision) {
            case AUTO_ACCEPT, ACCEPT -> Status.ACCEPTED;
            case AUTO_REJECT, REJECT -> Status.REJECTED;
            case EDIT -> Status.EDITED;
            case WITHDRAW -> Status.WITHDRAWN;
        };
    }

    public void markQueued() {
        this.status = Status.QUEUED;
    }

    public String statusWire() { return status.name().toLowerCase(); }
}