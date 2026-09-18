package de.verdox.hwapi.pricing.web;

import de.verdox.hwapi.pricing.application.kleinanzeigen.KleinanzeigenPriceService;
import de.verdox.hwapi.pricing.model.C2cMatchReview;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing/c2c-reviews")
public class C2cMatchReviewController {
    private final KleinanzeigenPriceService service;

    public C2cMatchReviewController(KleinanzeigenPriceService service) { this.service = service; }

    @GetMapping
    public List<C2cMatchReview> list(@RequestParam(defaultValue = "PENDING") C2cMatchReview.Status status) {
        return status == C2cMatchReview.Status.PENDING ? service.getReviewQueue() : service.getReviews(status);
    }

    @PostMapping("/{id}/confirm")
    public C2cMatchReview confirm(@PathVariable Long id, @RequestBody(required = false) ReviewAction action) {
        service.confirmC2cMatch(id, action == null ? "frontend" : action.actor(),
                action == null ? null : action.ean(), action == null ? null : action.mpn());
        return service.getReview(id);
    }

    @PostMapping("/{id}/reject")
    public C2cMatchReview reject(@PathVariable Long id, @RequestBody(required = false) ReviewAction action) {
        service.rejectC2cMatch(id, action == null ? "frontend" : action.actor());
        return service.getReview(id);
    }

    public record ReviewAction(String actor, String ean, String mpn) {}
}
