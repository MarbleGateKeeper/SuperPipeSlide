package dev.marblegate.superpipeslide.client.core.route;

import java.util.UUID;

public record RouteCandidate(UUID layoutId, int routeDirection, UUID platformStopId, UUID nextPlatformStopId, UUID sectionId) {    public RouteCandidate {
        routeDirection = routeDirection < 0 ? -1 : 1;
    }    public boolean sameRoute(RouteCandidate other) {
        return this.layoutId.equals(other.layoutId) && this.routeDirection == other.routeDirection;
    }
}
