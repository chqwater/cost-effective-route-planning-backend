package CERP.repository;

import CERP.model.Route;
import CERP.model.Station;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RouteRepository {
    private final JdbcTemplate jdbcTemplate;

    public RouteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Route> findAllRoutes() {
        String sql = """
                    SELECT r.*, l.l_type
                    FROM cer_routes r
                    JOIN cer_lines l ON r.l_id = l.l_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Route(
                rs.getInt("r_id"),
                rs.getInt("from_s_id"),
                rs.getInt("to_s_id"),
                rs.getInt("l_id"),
                rs.getDouble("travel_time"),
                rs.getInt("distance"),
                rs.getString("l_type")));
    }

    public List<Station> findAllStations() {
        String sql = "SELECT * FROM cer_stations";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Station(rs.getInt("s_id"), rs.getString("s_name"),
                rs.getString("s_type"), rs.getDouble("latitude"), rs.getDouble("longitude")));
    }

    public List<Station> findNearestStations(double lat, double lon, int limit) {
        String sql = """
                    WITH ranked_stations AS (
                        SELECT s.s_id, s.s_name, s.s_type, s.latitude, s.longitude,
                               SQRT(POWER(s.latitude - ?, 2) + POWER(s.longitude - ?, 2)) AS distance,
                               ROW_NUMBER() OVER (PARTITION BY sl.l_id ORDER BY SQRT(POWER(s.latitude - ?, 2) + POWER(s.longitude - ?, 2))) AS rn
                        FROM cer_stations s
                        JOIN cer_line_station sl ON s.s_id = sl.s_id
                    ),
                    closest_stations AS (
                        SELECT *
                        FROM ranked_stations
                        WHERE rn = 1
                    ),
                    final_selection AS (
                        SELECT s_id, s_name, s_type, latitude, longitude, distance,
                               ROW_NUMBER() OVER (ORDER BY distance) AS final_rank
                        FROM closest_stations
                    )
                    SELECT s_id, s_name, s_type, latitude, longitude
                    FROM final_selection
                    WHERE final_rank <= ?
                    ORDER BY distance
                """;

        return jdbcTemplate.query(sql, new Object[] { lat, lon, lat, lon, limit }, (rs, rowNum) -> new Station(
                rs.getInt("s_id"),
                rs.getString("s_name"),
                rs.getString("s_type"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude")));
    }

    public List<Route> findRoutesByFromStationId(int fromStationId) {
        String sql = """
                    SELECT r.*, l.l_type
                    FROM cer_routes r
                    JOIN cer_lines l ON r.l_id = l.l_id
                    WHERE r.from_s_id = ?
                """;
        List<Route> routes = jdbcTemplate.query(sql, new Object[] { fromStationId }, (rs, rowNum) -> new Route(
                rs.getInt("r_id"),
                rs.getInt("from_s_id"),
                rs.getInt("to_s_id"),
                rs.getInt("l_id"),
                rs.getDouble("travel_time"),
                rs.getInt("distance"),
                rs.getString("l_type")));

        String linesSql = "SELECT DISTINCT l_id FROM cer_lines";
        List<Integer> allLineIds = jdbcTemplate.queryForList(linesSql, Integer.class);

        Station fromStation = findStationById(fromStationId);

        for (Integer lineId : allLineIds) {
            if (routes.stream().noneMatch(r -> r.getLineId() == lineId)) {

                String nearestStationSql = """
                            SELECT * FROM (
                                SELECT s.*
                                FROM cer_stations s
                                JOIN cer_line_station ls ON s.s_id = ls.s_id
                                WHERE ls.l_id = ? AND s.s_id != ?
                                ORDER BY (s.latitude - ?)*(s.latitude - ?) + (s.longitude - ?)*(s.longitude - ?)
                            ) WHERE ROWNUM = 1
                        """;

                try {
                    Station nearestStation = jdbcTemplate.queryForObject(
                            nearestStationSql,
                            new Object[]{
                                lineId, fromStationId,
                                fromStation.getLatitude(), fromStation.getLatitude(),
                                fromStation.getLongitude(), fromStation.getLongitude()
                            },
                            (rs, rowNum) -> new Station(
                                    rs.getInt("s_id"),
                                    rs.getString("s_name"),
                                    rs.getString("s_type"),
                                    rs.getDouble("latitude"),
                                    rs.getDouble("longitude")
                            )
                    );

                    if (nearestStation != null) {
                        double distance = calculateDistance(fromStation.getLatitude(), fromStation.getLongitude(),
                                nearestStation.getLatitude(), nearestStation.getLongitude());
                        double transferTime = (distance / 5.0) * 60;

                        Route transferRoute = new Route(
                                -1,
                                fromStationId,
                                nearestStation.getStationId(),
                                lineId,
                                transferTime,
                                (int) (distance * 1000),
                                "transfer");
                        routes.add(transferRoute);
                    }
                } catch (Exception e) {
                    System.err.println("failed to query nearest station: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        return routes;
    }

    public Station findStationById(int stationId) {
        String sql = """
                select * from cer_stations
                where s_id = ?
                """;
        return jdbcTemplate.queryForObject(sql, new Object[] { stationId },
                (rs, rowNum) -> new Station(rs.getInt("s_id"),
                        rs.getString("s_name"),
                        rs.getString("s_type"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude")));
    }

    public Route findRouteBetweenStations(int fromStationId, int toStationId) {
        String sql = """
                    SELECT r.*, l.l_type
                    FROM cer_routes r
                    JOIN cer_lines l ON r.l_id = l.l_id
                    WHERE r.from_s_id = ? AND r.to_s_id = ?
                """;
        return jdbcTemplate.queryForObject(sql, new Object[] { fromStationId, toStationId }, (rs, rowNum) -> new Route(
                rs.getInt("r_id"),
                rs.getInt("from_s_id"),
                rs.getInt("to_s_id"),
                rs.getInt("l_id"),
                rs.getDouble("travel_time"),
                rs.getInt("distance"),
                rs.getString("l_type")));
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
