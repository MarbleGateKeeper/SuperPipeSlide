package dev.marblegate.superpipeslide.common.core.path;

import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRef;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record PathSearchResult(Status status, Optional<Path> optionalPath) {
    public static PathSearchResult valid(Path path) {
        return new PathSearchResult(Status.VALID, Optional.of(path));
    }

    public static PathSearchResult broken() {
        return new PathSearchResult(Status.BROKEN, Optional.empty());
    }

    public static PathSearchResult incomplete() {
        return new PathSearchResult(Status.INCOMPLETE, Optional.empty());
    }

    public Path path() {
        return this.optionalPath.orElseThrow();
    }

    public enum Status {
        VALID,
        BROKEN,
        INCOMPLETE
    }

    public record Path(List<PipeConnectionRef> connectionRefs, double length) {
        public Path {
            connectionRefs = List.copyOf(connectionRefs);
            length = Math.max(0.0D, length);
        }

        public List<UUID> connectionIds() {
            return this.connectionRefs.stream().map(PipeConnectionRef::connectionId).toList();
        }
    }
}
