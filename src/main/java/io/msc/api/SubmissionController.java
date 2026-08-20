package io.msc.api;

import io.msc.api.exception.MscException;
import io.msc.api.generated.SubmissionApi;
import io.msc.api.generated.model.Contribution;
import io.msc.api.generated.model.StatusResponse;
import io.msc.api.generated.model.SubmitResponse;
import io.msc.service.ContributionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Submission + status endpoints. The POST handler reads the raw request
 * body via {@link CachedBodyFilter#currentBody()}, verifies the Ed25519
 * signature, then forwards to the service.
 */
@RestController
public class SubmissionController implements SubmissionApi {

    private final ContributionService service;

    public SubmissionController(ContributionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<SubmitResponse> submitContribution(
            @RequestHeader("MSC-Origin") String msCOrigin,
            @RequestHeader("MSC-Origin-Key") String msCOriginKey,
            @RequestHeader("MSC-Signature") String msCSignature,
            @RequestBody Contribution contribution) {

        // 1) Read the cached raw body bytes (preserved across the filter).
        byte[] rawBody = CachedBodyFilter.currentBody();
        if (rawBody == null) {
            throw new MscException(HttpStatus.BAD_REQUEST,
                                   "missing raw body — CachedBodyFilter not active?");
        }

        // 2) Submit through the service.
        Optional<ContributionService.SubmissionResult> result =
            service.submit(msCOrigin, msCOriginKey, msCSignature,
                           rawBody, contribution);

        // 3) Map outcome → HTTP response.
        if (result.isEmpty()) {
            if (service.find(contribution.getId()).isPresent()) {
                throw new MscException(HttpStatus.UNAUTHORIZED,
                                       "signature verification failed");
            }
            throw new MscException(HttpStatus.UNAUTHORIZED,
                                   "unknown origin '" + msCOrigin + "' — key not registered");
        }

        ContributionService.SubmissionResult r = result.get();
        SubmitResponse resp = new SubmitResponse();
        resp.setId(r.contributionId());
        resp.setStatus(SubmitResponse.StatusEnum.fromValue(r.status()));
        resp.setReviewUrl("/msc/status/" + r.contributionId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }

    @Override
    public ResponseEntity<StatusResponse> getStatus(@PathVariable("contribution_id") String id) {
        return service.find(id)
            .map(c -> {
                StatusResponse sr = new StatusResponse();
                sr.setId(c.id());
                sr.setStatus(StatusResponse.StatusEnum.fromValue(c.statusWire()));
                sr.setDecision(c.decision() != null ? c.decision().wire() : null);
                sr.setReason(c.reason());
                sr.setModeratorId(c.moderatorId());
                sr.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(c.createdAt()));
                sr.setDecidedAt(c.decidedAt() != null
                    ? DateTimeFormatter.ISO_INSTANT.format(c.decidedAt()) : null);
                return ResponseEntity.ok(sr);
            })
            .orElseThrow(() -> new MscException(HttpStatus.NOT_FOUND, "contribution not found"));
    }
}