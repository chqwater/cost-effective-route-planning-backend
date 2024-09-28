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

    public List<Route> findAllRoutes (){
        String sql = """
            SELECT r.*, l.l_type
            FROM cer_routes r
            JOIN cer_lines l ON r.l_id = l.l_id
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) ->
            new Route(
                rs.getInt("r_id"),
                rs.getInt("from_s_id"),
                rs.getInt("to_s_id"),
                rs.getInt("l_id"),
                rs.getDouble("travel_time"),
                rs.getInt("distance"),
                rs.getString("l_type")
            )
        );
    }

    public List<Station> findAllStations (){
        String sql = "SELECT * FROM cer_stations";
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new Station(rs.getInt("s_id"), rs.getString("s_name"), rs.getString("s_type"), rs.getDouble("latitude"), rs.getDouble("longitude")));
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

        return jdbcTemplate.query(sql, new Object[]{lat, lon, lat, lon, limit}, (rs, rowNum) ->
                new Station(
                    rs.getInt("s_id"),
                    rs.getString("s_name"),
                    rs.getString("s_type"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude")
                )
        );
    }

    public List<Route> findRoutesByFromStationId(int fromStationId) {
        String sql = """
            SELECT r.*, l.l_type
            FROM cer_routes r
            JOIN cer_lines l ON r.l_id = l.l_id
            WHERE r.from_s_id = ?
        """;
        return jdbcTemplate.query(sql, new Object[]{fromStationId}, (rs, rowNum) ->
            new Route(
                rs.getInt("r_id"),
                rs.getInt("from_s_id"),
                rs.getInt("to_s_id"),
                rs.getInt("l_id"),
                rs.getDouble("travel_time"),
                rs.getInt("distance"),
                rs.getString("l_type")
            )
        );
    }

    public Station findStationById(int stationId){
        String sql = """
                select * from cer_stations
                where s_id = ?
                """;
        return jdbcTemplate.queryForObject(sql, new Object[]{stationId}, (rs, rowNum) ->
                new Station(rs.getInt("s_id"),
                        rs.getString("s_name"),
                        rs.getString("s_type"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude")));
    }

    public Route findRouteBetweenStations(int fromStationId, int toStationId){
        String sql = """
            SELECT r.*, l.l_type
            FROM cer_routes r
            JOIN cer_lines l ON r.l_id = l.l_id
            WHERE r.from_s_id = ? AND r.to_s_id = ?
        """;
        return jdbcTemplate.queryForObject(sql, new Object[]{fromStationId, toStationId}, (rs, rowNum) ->
            new Route(
                rs.getInt("r_id"),
                rs.getInt("from_s_id"),
                rs.getInt("to_s_id"),
                rs.getInt("l_id"),
                rs.getDouble("travel_time"),
                rs.getInt("distance"),
                rs.getString("l_type")
            )
        );
    }
}
