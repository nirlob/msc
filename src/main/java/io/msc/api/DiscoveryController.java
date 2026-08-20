package io.msc.api;

import io.msc.api.generated.DiscoveryApi;
import io.msc.api.generated.model.Capabilities;
import io.msc.api.generated.model.DiscoveryManifest;
import io.msc.api.generated.model.DiscoveryManifestRateLimit;
import io.msc.api.generated.model.Policy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * Discovery endpoints — purely static content; no business logic.
 */
@RestController
public class DiscoveryController implements DiscoveryApi {

    private static final String MSC_VERSION = "0.1";

    @Override
    public ResponseEntity<DiscoveryManifest> discovery() {
        DiscoveryManifest m = new DiscoveryManifest();
        m.setMscVersion(MSC_VERSION);
        m.setEndpoint(URI.create("http://localhost:8000/msc/contributions"));
        m.setStatusEndpoint(URI.create("http://localhost:8000/msc/status"));
        m.setAuthMethods(List.of(DiscoveryManifest.AuthMethodsEnum.ED25519));
        m.setAcceptedTypes(List.of(
            DiscoveryManifest.AcceptedTypesEnum.COMMENT,
            DiscoveryManifest.AcceptedTypesEnum.EDIT,
            DiscoveryManifest.AcceptedTypesEnum.ALTERNATIVE,
            DiscoveryManifest.AcceptedTypesEnum.TRANSLATION,
            DiscoveryManifest.AcceptedTypesEnum.RELATED,
            DiscoveryManifest.AcceptedTypesEnum.EXAMPLE));
        m.setPolicyUrl(URI.create("http://localhost:8000/msc/policy"));
        DiscoveryManifestRateLimit rl = new DiscoveryManifestRateLimit();
        rl.setPerIp(100);
        rl.setPerModel(1000);
        rl.setWindow("1h");
        m.setRateLimit(rl);
        return ResponseEntity.ok(m);
    }

    @Override
    public ResponseEntity<Capabilities> capabilities() {
        Capabilities c = new Capabilities();
        c.setMscVersion(MSC_VERSION);
        c.setEndpoints(List.of(
            "POST /msc/contributions",
            "GET  /msc/status/{id}",
            "GET  /msc/capabilities",
            "GET  /msc/contributions",
            "POST /msc/contributions/{id}/decision",
            "GET  /msc/audit"));
        c.setAutoAcceptBelowConfidence(new BigDecimal("0.7"));
        c.setAutoRejectPatterns(List.of(
            "(?:\\d[\\s-]?){12,18}\\d",
            "\\b\\d{3}-\\d{2}-\\d{4}\\b",
            "(?i)password\\s*[:=]\\s*\\S+",
            "(?i)\\bapi[_-]?key\\s*[:=]\\s*\\S+"));
        return ResponseEntity.ok(c);
    }

    @Override
    public ResponseEntity<Policy> policy() {
        Policy p = new Policy();
        p.setSummary("Demo MSC server. Contributions under CC-BY-SA-4.0 by default.");
        p.setModeration("All contributions enter the human-review queue.");
        p.setErasure("Requests honored within 30 days.");
        return ResponseEntity.ok(p);
    }
}