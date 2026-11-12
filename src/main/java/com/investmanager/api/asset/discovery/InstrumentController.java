package com.investmanager.api.asset.discovery;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/assets")
public class InstrumentController {
    private final InstrumentDiscoveryService discovery;
    public InstrumentController(InstrumentDiscoveryService discovery) {
        this.discovery = discovery;
    }
    @GetMapping("/search")
    public InstrumentDiscoveryService.SearchResult search(@RequestParam String query, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int limit) {
        return discovery.search(query, page, limit);
    }
}
