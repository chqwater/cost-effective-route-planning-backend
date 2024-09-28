package CERP.model;


import lombok.Data;


@Data
public class Station {
    private int stationId;
    private String stationName;
    private String stationType;
    private Double latitude;
    private Double longitude;

    public Station(int stationId, String stationName, String stationType, Double latitude, Double longitude) {
        this.stationId = stationId;
        this.stationName = stationName;
        this.stationType = stationType;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
