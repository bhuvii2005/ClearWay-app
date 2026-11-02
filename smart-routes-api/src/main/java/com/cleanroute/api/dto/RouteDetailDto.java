package com.cleanroute.api.dto;

import com.cleanroute.api.entity.Route;
import org.locationtech.jts.geom.Coordinate;

import java.util.Arrays;
import java.util.List;

/** A saved route including its geometry as [longitude, latitude] pairs, for redrawing on the map. */
public class RouteDetailDto extends RouteDto {

    private List<List<Double>> pathCoordinates;

    public RouteDetailDto() {}

    public RouteDetailDto(Route route) {
        super(route);
        this.pathCoordinates = Arrays.stream(route.getPath().getCoordinates())
                .map((Coordinate c) -> List.of(c.x, c.y))
                .toList();
    }

    public List<List<Double>> getPathCoordinates() { return pathCoordinates; }
    public void setPathCoordinates(List<List<Double>> pathCoordinates) { this.pathCoordinates = pathCoordinates; }
}
