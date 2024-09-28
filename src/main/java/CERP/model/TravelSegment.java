package CERP.model;

import lombok.Data;

@Data
public class TravelSegment {
    private TravelMode mode;
    private Route route;
    private Station fromStation;
    private Station toStation;
    private double startLat;
    private double startLon;
    private double endLat;
    private double endLon;
    private double duration;

    public TravelSegment(TravelMode mode, Route route, Station fromStation, Station toStation,
                         double startLat, double startLon, double endLat, double endLon, double duration) {
        this.mode = mode;
        this.route = route;
        this.fromStation = fromStation;
        this.toStation = toStation;
        this.startLat = startLat;
        this.startLon = startLon;
        this.endLat = endLat;
        this.endLon = endLon;
        this.duration = duration;
    }
}

