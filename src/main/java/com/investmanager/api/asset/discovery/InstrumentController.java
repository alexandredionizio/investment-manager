package com.investmanager.api.asset.discovery;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/assets")
public class InstrumentController {
    private final InstrumentDiscoveryService discovery;
    private final InstrumentIdentityService identities;
    private final com.investmanager.api.asset.service.AssetService assets;
    public InstrumentController(InstrumentDiscoveryService discovery, InstrumentIdentityService identities, com.investmanager.api.asset.service.AssetService assets) {
        this.discovery = discovery; this.identities = identities; this.assets = assets;
    }
    @GetMapping("/search")
    public InstrumentDiscoveryService.SearchResult search(@RequestParam String query, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int limit) {
        return discovery.search(query, page, limit);
    }
    @GetMapping("/{id}/identifiers")
    public java.util.List<InstrumentIdentityService.Identifier> identifiers(@PathVariable Long id) { assets.findById(id); return identities.identifiers(id); }
}
