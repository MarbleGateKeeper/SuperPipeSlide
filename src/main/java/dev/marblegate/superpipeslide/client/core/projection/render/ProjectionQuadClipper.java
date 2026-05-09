package dev.marblegate.superpipeslide.client.core.projection.render;

import java.util.ArrayList;
import java.util.List;

public final class ProjectionQuadClipper {
    private ProjectionQuadClipper() {
    }

    public static List<Vertex> clip(float minX, float minY, float maxX, float maxY, Vertex... vertices) {
        if (vertices == null || vertices.length < 3 || maxX <= minX || maxY <= minY) {
            return List.of();
        }
        List<Vertex> polygon = new ArrayList<>(List.of(vertices));
        polygon = clipLeft(polygon, minX);
        polygon = clipRight(polygon, maxX);
        polygon = clipBottom(polygon, minY);
        polygon = clipTop(polygon, maxY);
        return polygon.size() < 3 ? List.of() : List.copyOf(polygon);
    }

    private static List<Vertex> clipLeft(List<Vertex> input, float minX) {
        return clip(input, vertex -> vertex.x() >= minX, (from, to) -> interpolate(from, to, ratio(from.x(), to.x(), minX)));
    }

    private static List<Vertex> clipRight(List<Vertex> input, float maxX) {
        return clip(input, vertex -> vertex.x() <= maxX, (from, to) -> interpolate(from, to, ratio(from.x(), to.x(), maxX)));
    }

    private static List<Vertex> clipBottom(List<Vertex> input, float minY) {
        return clip(input, vertex -> vertex.y() >= minY, (from, to) -> interpolate(from, to, ratio(from.y(), to.y(), minY)));
    }

    private static List<Vertex> clipTop(List<Vertex> input, float maxY) {
        return clip(input, vertex -> vertex.y() <= maxY, (from, to) -> interpolate(from, to, ratio(from.y(), to.y(), maxY)));
    }

    private static List<Vertex> clip(List<Vertex> input, Inside inside, Intersector intersector) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<Vertex> output = new ArrayList<>(input.size() + 2);
        Vertex previous = input.getLast();
        boolean previousInside = inside.test(previous);
        for (Vertex current : input) {
            boolean currentInside = inside.test(current);
            if (currentInside) {
                if (!previousInside) {
                    output.add(intersector.intersect(previous, current));
                }
                output.add(current);
            } else if (previousInside) {
                output.add(intersector.intersect(previous, current));
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static float ratio(float from, float to, float target) {
        float delta = to - from;
        if (Math.abs(delta) <= 0.000001F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (target - from) / delta));
    }

    private static Vertex interpolate(Vertex from, Vertex to, float ratio) {
        return new Vertex(
                lerp(from.x(), to.x(), ratio),
                lerp(from.y(), to.y(), ratio),
                lerp(from.u(), to.u(), ratio),
                lerp(from.v(), to.v(), ratio)
        );
    }

    private static float lerp(float from, float to, float ratio) {
        return from + (to - from) * ratio;
    }

    private interface Inside {
        boolean test(Vertex vertex);
    }

    private interface Intersector {
        Vertex intersect(Vertex from, Vertex to);
    }

    public record Vertex(float x, float y, float u, float v) {
        public Vertex(float x, float y) {
            this(x, y, 0.0F, 0.0F);
        }
    }
}
