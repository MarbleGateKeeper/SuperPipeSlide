package dev.marblegate.superpipeslide.common.core.path;

public record PathSearchOptions(int maxVisitedNodes) {
    public PathSearchOptions {
        maxVisitedNodes = Math.max(1, maxVisitedNodes);
    }
}
