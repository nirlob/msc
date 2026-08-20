package io.msc.api;

import io.msc.api.generated.AuditApi;
import io.msc.api.generated.model.AuditEntry;
import io.msc.service.ContributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only access to the append-only audit log.
 */
@RestController
public class AuditController implements AuditApi {

    private final ContributionService service;

    public AuditController(ContributionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<AuditEntry>> listAudit(
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {

        return ResponseEntity.ok(
            service.audit(limit).stream()
                .map(e -> {
                    AuditEntry dto = new AuditEntry();
                    dto.setId(Math.toIntExact(e.id()));
                    dto.setContributionId(e.contributionId());
                    dto.setDecision(e.decision().wire());
                    dto.setReason(e.reason());
                    dto.setModeratorId(e.moderatorId());
                    dto.setTimestamp(DateTimeFormatter.ISO_INSTANT.format(e.timestamp()));
                    return dto;
                })
                .toList());
    }
}