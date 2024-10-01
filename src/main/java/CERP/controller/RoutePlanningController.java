package CERP.controller;

import CERP.model.RouteResult;
import CERP.model.TravelSegment;
import CERP.service.RoutePlanningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/api/route")
public class RoutePlanningController {
    private final RoutePlanningService routePlanningService;

    public RoutePlanningController(RoutePlanningService routePlanningService) {
        this.routePlanningService = routePlanningService;
    }

    @PostMapping("/fastest")
    public ResponseEntity<Map<String, Object>> planRouteWithTime(@RequestBody Map<String, String> request) {
        try {
            double startLat = Double.parseDouble(request.get("startLat"));
            double startLon = Double.parseDouble(request.get("startLon"));
            double endLat = Double.parseDouble(request.get("endLat"));
            double endLon = Double.parseDouble(request.get("endLon"));

            RouteResult result = routePlanningService.findShortestPath(startLat, startLon, endLat, endLon);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "successful");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "route planning failed：" + e.getMessage());
            response.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/cost-effective")
    public ResponseEntity<Map<String, Object>> planCostEffectiveRoute(@RequestBody Map<String, String> request) {
        try {
            double startLat = Double.parseDouble(request.get("startLat"));
            double startLon = Double.parseDouble(request.get("startLon"));
            double endLat = Double.parseDouble(request.get("endLat"));
            double endLon = Double.parseDouble(request.get("endLon"));

            RouteResult result = routePlanningService.findMostCostEffectivePath(startLat, startLon, endLat, endLon);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "successful");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "route planning failed：" + e.getMessage());
            response.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
