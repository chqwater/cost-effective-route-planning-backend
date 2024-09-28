package CERP.model;


import lombok.Data;


@Data
public class Route {
    private int routeId;
    private int fromStationId;
    private int toStationId;
    private int lineId;
    private double travelTime;
    private int distance;
    private String lineType;

    public Route(int routeId, int fromStationId, int toStationId, int lineId, double travelTime, int distance, String lineType) {
        this.routeId = routeId;
        this.fromStationId = fromStationId;
        this.toStationId = toStationId;
        this.lineId = lineId;
        this.travelTime = travelTime;
        this.distance = distance;
        this.lineType = lineType;
    }

    public boolean isSubway() {
        return "metro".equals(lineType);
    }
}
