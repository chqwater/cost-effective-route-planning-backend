package CERP.service;

import CERP.model.Station;
import CERP.model.Route;
import CERP.model.RouteResult;
import CERP.model.TravelMode;
import CERP.model.TravelSegment;
import CERP.repository.RouteRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RoutePlanningService {
    private final RouteRepository routeRepository;
    private static final double WALK_SPEED = 5.0 * 1000 / 60; // 转换为米/分钟
    private static final double MAX_WALK_DISTANCE = 2000; // 转换为米
    private static final double WAGE_PER_MINUTE = 0.83;


    public RoutePlanningService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }


    public RouteResult findShortestPath(double startLat, double startLon, double endLat, double endLon) {
        double directDistance = calculateDistance(startLat, startLon, endLat, endLon);
        // 找到最近的起点和终点
        List<Station> nearestStartStations = findNearestStations(startLat, startLon, 5);
        List<Station> nearestEndStations = findNearestStations(endLat, endLon, 5);

        if (directDistance <= MAX_WALK_DISTANCE && directDistance <=
                calculateDistance(startLat, startLon, nearestStartStations.get(0).getLatitude(), nearestStartStations.get(0).getLongitude())
        + calculateDistance(endLat, endLon, nearestEndStations.get(0).getLatitude(), nearestEndStations.get(0).getLongitude())) {
            double walkDuration = directDistance / WALK_SPEED;
            List<TravelSegment> directWalk = Collections.singletonList(
                    new TravelSegment(TravelMode.WALK, null, null, null, startLat, startLon, endLat, endLon, walkDuration)
            );
            double totalCost = calculateMoneyCost(directWalk);
            return new RouteResult(directWalk, totalCost);
        }

        List<TravelSegment> bestPath = null;
        double bestTotalTime = Double.MAX_VALUE;
        // 遍历所有可能的起点和终点
        for (Station startStation : nearestStartStations) {
            for (Station endStation : nearestEndStations) {
                List<Station> path = findShortestPathBetweenStations(startStation, endStation);
                if (path != null) {
                    List<TravelSegment> fullPath = createFullPath(startLat, startLon, path, endLat, endLon);
                    double totalTime = calculateTotalTime(fullPath);
                    if (totalTime < bestTotalTime) {
                        bestTotalTime = totalTime;
                        bestPath = fullPath;
                    }
                }
            }
        }

        double totalCost = calculateMoneyCost(bestPath);
        return new RouteResult(bestPath, totalCost);
    }

    public RouteResult findMostCostEffectivePath(double startLat, double startLon, double endLat, double endLon) {
        double directDistance = calculateDistance(startLat, startLon, endLat, endLon);

        List<Station> nearestStartStations = findNearestStations(startLat, startLon, 5);
        List<Station> nearestEndStations = findNearestStations(endLat, endLon, 5);

        if (directDistance <= MAX_WALK_DISTANCE && directDistance <=
                calculateDistance(startLat, startLon, nearestStartStations.get(0).getLatitude(), nearestStartStations.get(0).getLongitude())
                        + calculateDistance(endLat, endLon, nearestEndStations.get(0).getLatitude(), nearestEndStations.get(0).getLongitude())) {
            double walkDuration = directDistance / WALK_SPEED;
            List<TravelSegment> directWalk = Collections.singletonList(
                    new TravelSegment(TravelMode.WALK, null, null, null, startLat, startLon, endLat, endLon, walkDuration)
            );
            double totalCost = calculateMoneyCost(directWalk);
            return new RouteResult(directWalk, totalCost);
        }

        List<TravelSegment> bestPath = null;
        double bestTotalCost = Double.MAX_VALUE;

        for (Station startStation : nearestStartStations) {
            for (Station endStation : nearestEndStations) {
                List<Station> path = findMostCostEffectivePathBetweenStations(startStation, endStation);
                if (path != null) {
                    List<TravelSegment> fullPath = createFullPath(startLat, startLon, path, endLat, endLon);
                    double totalCost = calculateTotalCost(fullPath);
                    if (totalCost < bestTotalCost) {
                        bestTotalCost = totalCost;
                        bestPath = fullPath;
                    }
                }
            }
        }
        double totalCost = calculateMoneyCost(bestPath);
        return new RouteResult(bestPath, totalCost);
    }

    private List<Station> findMostCostEffectivePathBetweenStations(Station start, Station end) {
        PriorityQueue<Node> openList = new PriorityQueue<>();
        Set<Integer> closedList = new HashSet<>();
        Map<Integer, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0, estimateCost(start, end));
        openList.add(startNode);
        allNodes.put(start.getStationId(), startNode);

        while (!openList.isEmpty()) {
            Node current = openList.poll();

            if (current.station.getStationId() == end.getStationId()) {
                return reconstructPath(current);
            }

            closedList.add(current.station.getStationId());

            for (Route route : getAdjacentRoutes(current.station)) {
                Station neighbor = routeRepository.findStationById(route.getToStationId());
                if (closedList.contains(neighbor.getStationId())) {
                    continue;
                }

                double tentativeCost = current.gScore + calculateSegmentCost(route);

                Node neighborNode = allNodes.getOrDefault(neighbor.getStationId(),
                    new Node(neighbor, current, Double.MAX_VALUE, estimateCost(neighbor, end)));
                allNodes.put(neighbor.getStationId(), neighborNode);

                if (tentativeCost < neighborNode.gScore) {
                    neighborNode.parent = current;
                    neighborNode.gScore = tentativeCost;
                    neighborNode.fScore = neighborNode.gScore + neighborNode.hScore;

                    if (!openList.contains(neighborNode)) {
                        openList.add(neighborNode);
                    }
                }
            }
        }

        return null;
    }

    private double calculateSegmentCost(Route route) {
        double timeCost = route.getTravelTime() * WAGE_PER_MINUTE;
        double transportCost = route.getLineType() == "metro" ? 0.5 : 0;
        return timeCost + transportCost;
    }

    private double estimateCost(Station current, Station end) {
        double directDistance = calculateDistance(current.getLatitude(), current.getLongitude(),
                                                  end.getLatitude(), end.getLongitude());
        double estimatedSpeed = 30.0 * 1000 / 60; // 30 km/h 转换为 m/min
        double estimatedTime = directDistance / estimatedSpeed;
        double estimatedCost = (directDistance / 5000) / 0.5;
        return estimatedTime * WAGE_PER_MINUTE + estimatedCost; // 只考虑时间成本作为估计
    }

    private double calculateTotalCost(List<TravelSegment> path) {
        double totalCost = 0;
        int onBus = 0;
        int onSubway = 0;
        for (TravelSegment segment : path) {
            double timeCost = segment.getDuration() * WAGE_PER_MINUTE;
            double transportCost;
            if(segment.getMode() == TravelMode.PUBLIC_TRANSPORT){
                if(segment.getRoute().isSubway() && onSubway == 0){
                    onBus = 0;
                    onSubway = 1;
                    transportCost = 2;
                }else if(segment.getRoute().isSubway() && onSubway == 1){
                    transportCost = 0.5;
                }else if(onBus == 1){
                    transportCost = 0;
                }else{
                    transportCost = 1;
                    onBus = 1;
                    onSubway = 0;
                }
            }else{
                transportCost = 0;
            }
            totalCost += timeCost + transportCost;
        }
        return totalCost;
    }

    private double calculateMoneyCost(List<TravelSegment> path){
        double totalCost = 0;
        int onBus = 0;
        int onSubway = 0;
        for (TravelSegment segment : path) {
            double transportCost;
            if(segment.getMode() == TravelMode.PUBLIC_TRANSPORT){
                if(segment.getRoute().isSubway() && onSubway == 0){
                    onBus = 0;
                    onSubway = 1;
                    transportCost = 2;
                }else if(segment.getRoute().isSubway() && onSubway == 1){
                    transportCost = 0.5;
                }else if(onBus == 1){
                    transportCost = 0;
                }else{
                    transportCost = 1;
                    onBus = 1;
                    onSubway = 0;
                }
            }else{
                transportCost = 0;
            }
            totalCost += transportCost;
        }
        return totalCost;
    }

    // 找到最近的站点
    private List<Station> findNearestStations(double lat, double lon, int limit) {
        System.out.println(routeRepository.findNearestStations(lat, lon, limit));
        return routeRepository.findNearestStations(lat, lon, limit);
    }

    // 找到最短路径
    private List<Station> findShortestPathBetweenStations(Station start, Station end) {
        PriorityQueue<Node> openList = new PriorityQueue<>();
        Set<Integer> closedList = new HashSet<>();
        Map<Integer, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0, calculateHeuristic(start, end));
        openList.add(startNode);
        allNodes.put(start.getStationId(), startNode);

        while (!openList.isEmpty()) {
            Node current = openList.poll();

            if (current.station.getStationId() == end.getStationId()) {
                return reconstructPath(current);
            }

            closedList.add(current.station.getStationId());

            for (Route route : getAdjacentRoutes(current.station)) {
                Station neighbor = routeRepository.findStationById(route.getToStationId());
                if (closedList.contains(neighbor.getStationId())) {
                    continue;
                }

                double tentativeGScore = current.gScore + route.getTravelTime();

                Node neighborNode = allNodes.getOrDefault(neighbor.getStationId(),
                    new Node(neighbor, current, Double.MAX_VALUE, calculateHeuristic(neighbor, end)));
                allNodes.put(neighbor.getStationId(), neighborNode);

                if (tentativeGScore < neighborNode.gScore) {
                    neighborNode.parent = current;
                    neighborNode.gScore = tentativeGScore;
                    neighborNode.fScore = neighborNode.gScore + neighborNode.hScore;

                    if (!openList.contains(neighborNode)) {
                        openList.add(neighborNode);
                    }
                }
            }
        }

        return null;
    }

    private double calculateHeuristic(Station current, Station end) {
        double directDistance = calculateDistance(current.getLatitude(), current.getLongitude(),
                                                  end.getLatitude(), end.getLongitude());
        double estimatedSpeed = 30.0 * 1000 / 60; // km/h
        return directDistance / estimatedSpeed;
    }

    private List<Route> getAdjacentRoutes(Station station) {
        return routeRepository.findRoutesByFromStationId(station.getStationId());
    }

    private List<Station> reconstructPath(Node endNode) {
        List<Station> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(current.station);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private List<TravelSegment> createFullPath(double startLat, double startLon, List<Station> stationPath, double endLat, double endLon) {
        List<TravelSegment> fullPath = new ArrayList<>();

        // 添加从起点到第一个车站的步行段
        Station firstStation = stationPath.get(0);
        double walkDistance = calculateDistance(startLat, startLon, firstStation.getLatitude(), firstStation.getLongitude());
        double walkDuration = walkDistance / WALK_SPEED;
        fullPath.add(new TravelSegment(TravelMode.WALK, null, null, firstStation, startLat, startLon,
                                       firstStation.getLatitude(), firstStation.getLongitude(), walkDuration));

        // 添加公共交通段
        for (int i = 0; i < stationPath.size() - 1; i++) {
            Station from = stationPath.get(i);
            Station to = stationPath.get(i + 1);
            Route route = findRouteBetweenStations(from, to);
            fullPath.add(new TravelSegment(TravelMode.PUBLIC_TRANSPORT, route, from, to,
                                           from.getLatitude(), from.getLongitude(),
                                           to.getLatitude(), to.getLongitude(), route.getTravelTime()));
        }

        // 添加从最后一个车站到终点的步行段
        Station lastStation = stationPath.get(stationPath.size() - 1);
        walkDistance = calculateDistance(lastStation.getLatitude(), lastStation.getLongitude(), endLat, endLon);
        walkDuration = walkDistance / WALK_SPEED;
        fullPath.add(new TravelSegment(TravelMode.WALK, null, lastStation, null,
                                       lastStation.getLatitude(), lastStation.getLongitude(), endLat, endLon, walkDuration));

        return fullPath;
    }

    private double calculateTotalTime(List<TravelSegment> path) {
        return path.stream().mapToDouble(TravelSegment::getDuration).sum();
    }

    private Route findRouteBetweenStations(Station from, Station to) {
        return routeRepository.findRouteBetweenStations(from.getStationId(), to.getStationId());
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private static class Node implements Comparable<Node> {
        Station station;
        Node parent;
        double gScore;
        double hScore;
        double fScore;

        Node(Station station, Node parent, double gScore, double hScore) {
            this.station = station;
            this.parent = parent;
            this.gScore = gScore;
            this.hScore = hScore;
            this.fScore = gScore + hScore;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }
}
