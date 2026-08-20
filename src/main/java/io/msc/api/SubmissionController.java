package io.msc.api;

import io.msc.api.exception.MscException;
import io.msc.api.generated.SubmissionApi;
import io.msc.api.generated.model.Contribution;
import io.msc.api.generated.model.SubmitResponse;
import io.msc.service.ContributionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * The single public endpoint of the reference server: {@code POST /msc/send}.
 *
 * Reads the cached raw request body via {@link CachedBodyFilter}, verifies
 * the Ed25519 signature, then forwards to {@link ContributionService}
 * which applies the policy engine and persists the decision.
 */
@RestController
public class SubmissionController implements SubmissionApi {

    private final ContributionService service;

    public SubmissionController(ContributionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<SubmitResponse> sendContribution(
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
        resp.setReviewUrl("/msc/send/" + r.contributionId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }
}