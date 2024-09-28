package CERP.model;

import lombok.Data;
import java.util.List;

@Data
public class RouteResult {
    private List<TravelSegment> path;
    private double totalCost;

    public RouteResult(List<TravelSegment> path, double totalCost) {
        this.path = path;
        this.totalCost = totalCost;
    }
}
