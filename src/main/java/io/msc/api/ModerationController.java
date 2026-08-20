package io.msc.api;

import io.msc.api.exception.MscException;
import io.msc.api.generated.ModerationApi;
import io.msc.api.generated.model.ContributionListItem;
import io.msc.api.generated.model.DecideContribution200Response;
import io.msc.api.generated.model.DecisionRequest;
import io.msc.api.generated.model.StatusResponse;
import io.msc.domain.Decision;
import io.msc.service.ContributionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Moderation queue (list) and decision endpoint.
 */
@RestController
public class ModerationController implements ModerationApi {

    private final ContributionService service;

    public ModerationController(ContributionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ContributionListItem>> listContributions(
            @RequestParam(value = "status_filter", required = false) String statusFilter,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {

        return ResponseEntity.ok(
            service.list(statusFilter, limit).stream()
                .map(c -> {
                    ContributionListItem i = new ContributionListItem();
                    i.setId(c.id());
                    i.setOrigin(c.origin());
                    i.setStatus(c.statusWire());
                    i.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(c.createdAt()));
                    i.setDecidedAt(c.decidedAt() != null
                        ? DateTimeFormatter.ISO_INSTANT.format(c.decidedAt()) : null);
                    i.setDecision(c.decision() != null ? c.decision().wire() : null);
                    return i;
                })
                .toList());
    }

    @Override
    public ResponseEntity<DecideContribution200Response> decideContribution(
            @PathVariable("contribution_id") String id,
            DecisionRequest req) {

        Decision decision = switch (req.getDecision().getValue()) {
            case "accept"  -> Decision.ACCEPT;
            case "reject"  -> Decision.REJECT;
            case "edit"    -> Decision.EDIT;
            case "withdraw" -> Decision.WITHDRAW;
            default -> throw new MscException(HttpStatus.BAD_REQUEST,
                "invalid decision: " + req.getDecision().getValue());
        };

        try {
            service.decide(id, decision, req.getReason(), req.getModeratorId());
        } catch (IllegalArgumentException e) {
            throw new MscException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        DecideContribution200Response r = new DecideContribution200Response();
        r.setId(id);
        var c = service.find(id).orElseThrow();
        r.setStatus(c.statusWire());
        return ResponseEntity.ok(r);
    }
}