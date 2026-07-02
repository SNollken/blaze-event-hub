package com.nollen.blaze.points;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<PointsBalance> getBalance(@PathVariable String userId) {
        return pointsService.getBalance(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/leaderboard")
    public List<PointsLeaderboardEntry> getLeaderboard(
            @RequestParam(defaultValue = "20") int limit) {
        return pointsService.getLeaderboard(limit);
    }

    @GetMapping("/history/{userId}")
    public List<PointsLedger> getHistory(@PathVariable String userId) {
        return pointsService.getHistory(userId);
    }

    @GetMapping("/rules")
    public List<PointsRule> getRules() {
        return pointsService.getRules();
    }

    @PostMapping("/adjust")
    public PointsLedger adjustPoints(@RequestBody AdjustRequest request) {
        return pointsService.creditManual(
                request.userId(),
                request.username(),
                request.points(),
                request.description()
        );
    }

    @GetMapping("/stats")
    public PointsStatsResponse getStats() {
        return pointsService.getStats();
    }

    public record AdjustRequest(
            String userId,
            String username,
            int points,
            String description
    ) {
    }
}
