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
    private static final double WALK_SPEED = 5.0 * 1000 / 60;
    private static final double MAX_WALK_DISTANCE = 2000;
    private static final double WAGE_PER_MINUTE = 0.83;
    private static final double MAX_TRANSFER_DISTANCE = 1000;
    private static final int MAX_NEARBY_STATIONS = 5;

    public RoutePlanningService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }


    public RouteResult findShortestPath(double startLat, double startLon, double endLat, double endLon) {
        double directDistance = calculateDistance(startLat, startLon, endLat, endLon);
        // find nearest stations and then process on them
        List<Station> nearestStartStations = findNearestStations(startLat, startLon, 5);
        List<Station> nearestEndStations = findNearestStations(endLat, endLon, 5);
        double estimatedWalkDistance = calculateDistance(startLat, startLon, nearestStartStations.get(0).getLatitude(), nearestStartStations.get(0).getLongitude())
                + calculateDistance(endLat, endLon, nearestEndStations.get(0).getLatitude(), nearestEndStations.get(0).getLongitude());

        if (directDistance <= estimatedWalkDistance) {
            double walkDuration = directDistance / WALK_SPEED;
            List<TravelSegment> directWalk = Collections.singletonList(
                    new TravelSegment(TravelMode.WALK, null, null, null, startLat, startLon, endLat, endLon, walkDuration)
            );
            double totalCost = calculateMoneyCost(directWalk);
            return new RouteResult(directWalk, totalCost);
        }

        record PathResult(List<TravelSegment> path, double totalTime) {}

        PathResult bestResult = nearestStartStations.parallelStream()
            .flatMap(startStation -> nearestEndStations.stream()
                .map(endStation -> {
                    List<Station> path = findShortestPathBetweenStations(startStation, endStation);
                    if (path != null) {
                        List<TravelSegment> fullPath = createFullPath(startLat, startLon, path, endLat, endLon);
                        double totalTime = calculateTotalTime(fullPath);
                        return new PathResult(fullPath, totalTime);
                    }
                    return null;
                }))
            .filter(Objects::nonNull)
            .min(Comparator.comparingDouble(PathResult::totalTime))
            .orElse(null);

        if (bestResult != null) {
            double totalCost = calculateMoneyCost(bestResult.path);
            return new RouteResult(bestResult.path, totalCost);
        }

        return null; // 或者返回一个表示没有找到路径的结果
    }

    public RouteResult findMostCostEffectivePath(double startLat, double startLon, double endLat, double endLon) {
        double directDistance = calculateDistance(startLat, startLon, endLat, endLon);
        List<Station> nearestStartStations = findNearestStations(startLat, startLon, 5);
        List<Station> nearestEndStations = findNearestStations(endLat, endLon, 5);
        double estimatedWalkDistance = calculateDistance(startLat, startLon, nearestStartStations.get(0).getLatitude(), nearestStartStations.get(0).getLongitude())
                + calculateDistance(endLat, endLon, nearestEndStations.get(0).getLatitude(), nearestEndStations.get(0).getLongitude());
        if (directDistance <= estimatedWalkDistance) {
            double walkDuration = directDistance / WALK_SPEED;
            List<TravelSegment> directWalk = Collections.singletonList(
                    new TravelSegment(TravelMode.WALK, null, null, null, startLat, startLon, endLat, endLon, walkDuration)
            );
            double totalCost = calculateMoneyCost(directWalk);
            return new RouteResult(directWalk, totalCost);
        }

        record PathResult(List<TravelSegment> path, double totalCost) {}

        PathResult bestResult = nearestStartStations.parallelStream()
            .flatMap(startStation -> nearestEndStations.stream()
                .map(endStation -> {
                    List<Station> path = findMostCostEffectivePathBetweenStations(startStation, endStation);
                    if (path != null) {
                        List<TravelSegment> fullPath = createFullPath(startLat, startLon, path, endLat, endLon);
                        double totalCost = calculateTotalCost(fullPath);
                        return new PathResult(fullPath, totalCost);
                    }
                    return null;
                }))
            .filter(Objects::nonNull)
            .min(Comparator.comparingDouble(PathResult::totalCost))
            .orElse(null);

        if (bestResult != null) {
            double totalCost = calculateMoneyCost(bestResult.path);
            return new RouteResult(bestResult.path, totalCost);
        }

        return null; // 或者返回一个表示没有找到路径的结果
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

            // 预先获取所有相邻路线
            List<Route> adjacentRoutes = getAdjacentRoutes(current.station);
            
            for (Route route : adjacentRoutes) {
                int neighborId = route.getToStationId();
                if (closedList.contains(neighborId)) {
                    continue;
                }

                double tentativeCost = current.gScore + calculateSegmentCost(route);

                Node neighborNode = allNodes.computeIfAbsent(neighborId, k -> {
                    Station neighbor = routeRepository.findStationById(neighborId);
                    return new Node(neighbor, current, Double.MAX_VALUE, estimateCost(neighbor, end));
                });

                if (tentativeCost < neighborNode.gScore) {
                    neighborNode.parent = current;
                    neighborNode.gScore = tentativeCost;
                    neighborNode.fScore = neighborNode.gScore + neighborNode.hScore;

                    if (!openList.contains(neighborNode)) {
                        openList.add(neighborNode);
                    } else {
                        // 更新优先队列中的节点
                        openList.remove(neighborNode);
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
        double estimatedSpeed = 30.0 * 1000 / 60;
        double estimatedTime = directDistance / estimatedSpeed;
        double estimatedCost = (directDistance / 5000) / 0.5;
        return estimatedTime * WAGE_PER_MINUTE + estimatedCost; // only consider the time
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

    private List<Station> findNearestStations(double lat, double lon, int limit) {
        return routeRepository.findNearestStations(lat, lon, limit);
    }

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
        double estimatedSpeed = 30.0 * 1000 / 60;
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

        //add the segment that from origin to first station
        Station firstStation = stationPath.get(0);
        double walkDistance = calculateDistance(startLat, startLon, firstStation.getLatitude(), firstStation.getLongitude());
        double walkDuration = walkDistance / WALK_SPEED;
        fullPath.add(new TravelSegment(TravelMode.WALK, null, null, firstStation, startLat, startLon,
                                       firstStation.getLatitude(), firstStation.getLongitude(), walkDuration));

        for (int i = 0; i < stationPath.size() - 1; i++) {
            Station from = stationPath.get(i);
            Station to = stationPath.get(i + 1);
            Route route = findRouteBetweenStations(from, to);

            if (route == null) {
                // add walk to transfer segment
                addWalkSegment(fullPath, from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude(), from, to);
            }else{
               fullPath.add(new TravelSegment(TravelMode.PUBLIC_TRANSPORT, route, from, to,
                                           from.getLatitude(), from.getLongitude(),
                                           to.getLatitude(), to.getLongitude(), route.getTravelTime()));
            }

        }

        // last walk
        Station lastStation = stationPath.get(stationPath.size() - 1);
        addWalkSegment(fullPath, lastStation.getLatitude(), lastStation.getLongitude(), endLat, endLon, lastStation, null);
        return fullPath;
    }

    private void addWalkSegment(List<TravelSegment> path, double startLat, double startLon,
                                double endLat, double endLon, Station from, Station to) {
        double walkDistance = calculateDistance(startLat, startLon, endLat, endLon);
        double walkDuration = walkDistance / WALK_SPEED;
        path.add(new TravelSegment(TravelMode.WALK, null, from, to, startLat, startLon, endLat, endLon, walkDuration));
    }

    private double calculateTotalTime(List<TravelSegment> path) {
        return path.stream().mapToDouble(TravelSegment::getDuration).sum();
    }

    private Route findRouteBetweenStations(Station from, Station to) {
        try {
            return routeRepository.findRouteBetweenStations(from.getStationId(), to.getStationId());
        } catch (Exception e) {
            // return null if no such route
            return null;
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
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