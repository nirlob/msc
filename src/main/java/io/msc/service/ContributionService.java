package io.msc.service;

import io.msc.api.generated.model.Contribution;
import io.msc.domain.Decision;
import io.msc.security.Ed25519Verifier;
import io.msc.storage.MscRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service layer. Holds the policy engine and orchestrates the persistence
 * and audit log. All business rules live here; controllers are thin shells.
 */
public final class ContributionService {

    public static final BigDecimal AUTO_ACCEPT_CONFIDENCE = new BigDecimal("0.7");
    public static final int RATE_LIMIT_PER_IP = 100;
    public static final int RATE_LIMIT_PER_ORIGIN = 1000;

    private static final List<Pattern> AUTO_REJECT_PATTERNS = List.of(
        // 13-19 digits, allowing spaces or dashes between groups (credit cards etc.)
        Pattern.compile("(?:\\d[\\s-]?){12,18}\\d"),
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),                       // US SSN shape
        Pattern.compile("(?i)password\\s*[:=]\\s*\\S+"),
        Pattern.compile("(?i)\\bapi[_-]?key\\s*[:=]\\s*\\S+")
    );

    /** Outcome of a submission attempt. */
    public enum Outcome { ACCEPTED, REJECTED, QUEUED, DUPLICATE }

    public record SubmissionResult(
        Outcome outcome,
        String contributionId,
        String status,
        String reason,         // populated for REJECTED / DUPLICATE
        String moderatorId) {  // populated for ACCEPTED / REJECTED

        public static SubmissionResult accepted(String id, String moderatorId) {
            return new SubmissionResult(Outcome.ACCEPTED, id, "accepted",
                                        "high-confidence safe type", moderatorId);
        }
        public static SubmissionResult rejected(String id, String reason,
                                                String moderatorId) {
            return new SubmissionResult(Outcome.REJECTED, id, "rejected", reason, moderatorId);
        }
        public static SubmissionResult queued(String id) {
            return new SubmissionResult(Outcome.QUEUED, id, "queued", null, null);
        }
        public static SubmissionResult duplicate(String id) {
            return new SubmissionResult(Outcome.DUPLICATE, id, "rejected",
                                        "duplicate contribution id", "system");
        }
    }

    private final MscRepository repo;

    public ContributionService(MscRepository repo) {
        this.repo = repo;
    }

    /**
     * Verify an Ed25519 signature over a request body, then evaluate the
     * submission against auto-reject / auto-accept policies.
     *
     * @param origin         "model_id:user_id"
     * @param publicKeyWire  "ed25519:&lt;base64url&gt;" declared in header
     * @param signatureWire  "ed25519:&lt;base64url&gt;" of body
     * @param rawBody        exact UTF-8 bytes the client signed
     * @param dto            parsed contribution (same content as rawBody)
     */
    public Optional<SubmissionResult> submit(String origin, String publicKeyWire,
                                             String signatureWire, byte[] rawBody,
                                             Contribution dto) {
        // 1) signature verification
        var keyOpt = repo.getActiveKey(origin);
        if (keyOpt.isEmpty()) return Optional.empty();
        var key = keyOpt.get();

        // The declared key MUST match the registered one.
        if (!key.publicKeyWire().equals(publicKeyWire)) return Optional.empty();

        if (!Ed25519Verifier.verify(publicKeyWire, rawBody, signatureWire)) {
            repo.appendAudit(null, Decision.AUTO_REJECT, "bad_signature",
                             origin, Instant.now());
            return Optional.empty();
        }

        // 2) build the domain object
        var c = new io.msc.domain.Contribution(
            dto.getId(),
            origin,
            dto.getBody(),
            dto.getConfidence(),
            dto.getType().getValue(),
            dto.getLanguage() == null ? "en" : dto.getLanguage().getValue(),
            dto.getUserAttribution() == null ? "anonymous"
                                             : dto.getUserAttribution().getValue(),
            dto.getLicense(),
            dto.getTags() == null ? List.of() : dto.getTags(),
            dto.getSource().getUrl(),
            dto.getSource().getCanonical(),
            dto.getTimestamp(),
            Instant.now()
        );

        // 3) dedup
        if (!repo.insertContribution(c)) {
            return Optional.of(SubmissionResult.duplicate(c.id()));
        }

        // 4) auto-reject filters
        for (var pattern : AUTO_REJECT_PATTERNS) {
            if (pattern.matcher(c.body()).find()) {
                c.recordDecision(Decision.AUTO_REJECT,
                                 "matched pattern: " + pattern.pattern(),
                                 "auto-filter", Instant.now());
                repo.recordDecision(c.id(), Decision.AUTO_REJECT,
                                    "matched pattern: " + pattern.pattern(),
                                    "auto-filter",
                                    "rejected",
                                    Instant.now(), null);
                repo.appendAudit(c.id(), Decision.AUTO_REJECT,
                                 "auto_reject:" + pattern.pattern(),
                                 "auto-filter", Instant.now());
                return Optional.of(SubmissionResult.rejected(c.id(),
                                    "matched pattern: " + pattern.pattern(),
                                    "auto-filter"));
            }
        }

        // 5) auto-accept policy
        boolean safeType = "comment".equals(c.contributionType())
                        || "related".equals(c.contributionType());
        boolean autoAccept = c.confidence().compareTo(AUTO_ACCEPT_CONFIDENCE) >= 0
                          && safeType
                          && !"real_name".equals(c.userAttribution());

        if (autoAccept) {
            c.recordDecision(Decision.AUTO_ACCEPT, "high-confidence safe type",
                             "auto-policy", Instant.now());
            repo.recordDecision(c.id(), Decision.AUTO_ACCEPT,
                                "high-confidence safe type",
                                "auto-policy",
                                "accepted",
                                Instant.now(), null);
            repo.appendAudit(c.id(), Decision.AUTO_ACCEPT, "auto_accept",
                             "auto-policy", Instant.now());
            return Optional.of(SubmissionResult.accepted(c.id(), "auto-policy"));
        }

        // 6) default: queue for human review
        c.markQueued();
        repo.appendAudit(c.id(), Decision.AUTO_ACCEPT, null, "system", Instant.now());
        return Optional.of(SubmissionResult.queued(c.id()));
    }

    /**
     * Record a moderator decision. Throws if the contribution does not exist.
     */
    public void decide(String contributionId, Decision decision,
                       String reason, String moderatorId) {
        var c = repo.getContribution(contributionId)
            .orElseThrow(() -> new IllegalArgumentException("contribution not found"));
        c.recordDecision(decision, reason, moderatorId, Instant.now());
        repo.recordDecision(contributionId, decision, reason, moderatorId,
                            c.statusWire(), Instant.now(), null);
        repo.appendAudit(contributionId, decision, reason, moderatorId, Instant.now());
    }

    public Optional<io.msc.domain.Contribution> find(String id) {
        return repo.getContribution(id);
    }

    public List<io.msc.domain.Contribution> list(String statusFilter, int limit) {
        return repo.listContributions(statusFilter, limit);
    }

    public List<io.msc.domain.AuditEntry> audit(int limit) {
        return repo.listAudit(limit);
    }

    /**
     * Atomically increment a rate bucket. Returns the new counter value.
     */
    public int incrementRate(String bucketKey, String windowStart) {
        return repo.rateIncrement(bucketKey, windowStart);
    }
}