package dev.marblegate.superpipeslide.common.core.appearance.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record PipeSurfaceModel(
        List<LocalSurface> surfaces,
        double perimeter,
        MarkerLanes lanes,
        List<CoatingBand> bands,
        List<PatternedBox> boxes) {

    private static final double EPSILON = 1.0E-5D;
    private static final String INTERNAL_MARKER_ACCELERATION_SLOT = "__marker_acceleration";
    private static final String INTERNAL_MARKER_HIGHWAY_SLOT = "__marker_highway";
    private static final String INTERNAL_MARKER_DIRECTION_SLOT = "__marker_direction";
    private static final String INTERNAL_MARKER_PLATFORM_SLOT = "__marker_platform";
    private static final double ACCELERATION_MARKER_WIDTH = 0.120D;
    private static final double HIGHWAY_MARKER_WIDTH = 0.205D;
    private static final double DIRECTION_MARKER_WIDTH = 0.140D;
    private static final double PLATFORM_MARKER_WIDTH = 0.220D;
    private static final double ACCELERATION_ANCHOR_WIDTH = ACCELERATION_MARKER_WIDTH * 1.12D;
    private static final double HIGHWAY_ANCHOR_WIDTH = HIGHWAY_MARKER_WIDTH * 1.30D;
    private static final double DIRECTION_ANCHOR_WIDTH = DIRECTION_MARKER_WIDTH * 1.18D;
    private static final double PLATFORM_ANCHOR_WIDTH = PLATFORM_MARKER_WIDTH * 2.00D;
    public PipeSurfaceModel {
        surfaces = List.copyOf(surfaces);
        bands = List.copyOf(bands);
        boxes = List.copyOf(boxes);
    }

    public static PipeSurfaceModel build(PipeStyleShape shape, PipeVariantDefinition variant, PipeStyleGeometry geometry) {
        return build(shape, variant, geometry, 0);
    }

    public static PipeSurfaceModel build(PipeStyleShape shape, PipeVariantDefinition variant, PipeStyleGeometry geometry, int lod) {
        int detail = Math.max(0, Math.min(3, lod));
        SurfaceBuilder builder = new SurfaceBuilder();
        List<CoatingBand> bands = new ArrayList<>();
        List<PatternedBox> boxes = new ArrayList<>();
        switch (shape) {
            case ROUND -> roundSurfaces(builder, geometry.radius(), roundSides(detail), "body");
            case FACETED -> facetedSurfaces(builder, geometry.radius(), variant.reinforced(), facetedSides(detail));
            case BOX -> boxSurfaces(builder, geometry, variant.splitCoating());
            case TRIANGLE -> triangleSurfaces(builder, geometry, variant.reinforced());
            case RAIL -> railSurfaces(builder, boxes, geometry, variant.reinforced());
            case SLIDE -> slideSurfaces(builder, geometry, variant.reinforced(), variant.curved(), detail);
            case MONORAIL -> monorailSurfaces(builder, geometry, variant.reinforced());
            case COVERED -> coveredSurfaces(builder, boxes, geometry, variant.reinforced(), variant.extraRibs(), detail);
        }
        if (variant.extraRibs()) {
            String ribSlot = shape == PipeStyleShape.COVERED ? "frame" : "rib";
            bands.add(new CoatingBand(ribSlot, builder.perimeter() * 0.5D, builder.perimeter() * 0.96D, 0.58D, 0.075D, 0.02D, 3));
        }
        addMarkerSurface(shape, builder, geometry, variant);
        return new PipeSurfaceModel(List.copyOf(builder.surfaces()), builder.perimeter(), markerLanes(builder), List.copyOf(bands), List.copyOf(boxes));
    }

    public List<String> slotIds() {
        List<String> slots = new ArrayList<>();
        for (LocalSurface surface : this.surfaces) {
            if (isInternalSlot(surface.slotId())) {
                continue;
            }
            if (!slots.contains(surface.slotId())) {
                slots.add(surface.slotId());
            }
        }
        for (CoatingBand band : this.bands) {
            if (!slots.contains(band.slotId())) {
                slots.add(band.slotId());
            }
        }
        for (PatternedBox box : this.boxes) {
            if (!slots.contains(box.slotId())) {
                slots.add(box.slotId());
            }
        }
        return List.copyOf(slots);
    }

    public LocalSurface surfaceAt(double position) {
        double wrapped = wrap(position, this.perimeter);
        for (LocalSurface surface : this.surfaces) {
            if (wrapped >= surface.vStart() - EPSILON && wrapped <= surface.vEnd() + EPSILON) {
                return surface;
            }
        }
        return this.surfaces.isEmpty() ? new LocalSurface("body", -0.1D, 0.0D, 0.1D, 0.0D, 0.0D, 0.2D, true, FaceVisibility.TWO_SIDED) : this.surfaces.getFirst();
    }

    public static double center(LocalSurface surface) {
        return (surface.vStart() + surface.vEnd()) * 0.5D;
    }

    public static double width(LocalSurface surface) {
        return Math.max(EPSILON, surface.vEnd() - surface.vStart());
    }

    public static double at(LocalSurface surface, double fraction) {
        return surface.vStart() + width(surface) * clamp(fraction, 0.0D, 1.0D);
    }

    public static double wrap(double value, double perimeter) {
        if (perimeter <= EPSILON) {
            return 0.0D;
        }
        double wrapped = value % perimeter;
        return wrapped < 0.0D ? wrapped + perimeter : wrapped;
    }

    private static void roundSurfaces(SurfaceBuilder builder, double radius, int sides, String slotId) {
        List<Local2> points = polygonPoints(radius, sides, Math.PI / sides);
        for (int i = 0; i < points.size(); i++) {
            Local2 a = points.get(i);
            Local2 b = points.get((i + 1) % points.size());
            builder.add(slotId, a.x(), a.y(), b.x(), b.y(), true, FaceVisibility.SINGLE_SIDED_OUTWARD);
        }
    }

    private static int roundSides(int lod) {
        return switch (lod) {
            case 0 -> 12;
            case 1 -> 8;
            case 2 -> 6;
            default -> 4;
        };
    }

    private static int facetedSides(int lod) {
        return switch (lod) {
            case 0 -> 8;
            case 1 -> 6;
            default -> 4;
        };
    }

    private static int curvedSlideSegments(int lod) {
        return switch (lod) {
            case 0 -> 10;
            case 1 -> 7;
            case 2 -> 5;
            default -> 3;
        };
    }

    private static int coveredArchSegments(int lod) {
        return switch (lod) {
            case 0 -> 10;
            case 1 -> 7;
            case 2 -> 5;
            default -> 4;
        };
    }

    private static void facetedSurfaces(SurfaceBuilder builder, double radius, boolean edge, int sides) {
        List<Local2> points = polygonPoints(radius, sides, Math.PI / sides);
        for (int i = 0; i < points.size(); i++) {
            Local2 a = points.get(i);
            Local2 b = points.get((i + 1) % points.size());
            double midY = (a.y() + b.y()) * 0.5D;
            String slot = edge && midY > radius * 0.45D ? "edge" : "body";
            builder.add(slot, a.x(), a.y(), b.x(), b.y(), true, FaceVisibility.SINGLE_SIDED_OUTWARD);
        }
    }

    private static void boxSurfaces(SurfaceBuilder builder, PipeStyleGeometry geometry, boolean split) {
        double w = geometry.halfWidth();
        double h = geometry.halfHeight();
        String cap = split ? "cap" : "body";
        String side = split ? "side" : "body";
        builder.add(side, w, h, w, -h, true);
        builder.add(cap, w, -h, -w, -h, true);
        builder.add(side, -w, -h, -w, h, true);
        builder.add(cap, -w, h, w, h, true);
    }

    private static void triangleSurfaces(SurfaceBuilder builder, PipeStyleGeometry geometry, boolean keel) {
        double halfWidth = geometry.halfWidth();
        double topHalf = Math.max(halfWidth * geometry.topFlatness(), halfWidth * 0.10D);
        double bottomHalf = keel ? Math.max(geometry.edgeWidth(), halfWidth * 0.08D) : 0.0D;
        double depth = geometry.depth();
        builder.add("top", -topHalf, 0.0D, topHalf, 0.0D, true);
        builder.add("side", topHalf, 0.0D, bottomHalf, -depth, true);
        if (bottomHalf > 0.0D) {
            builder.add("keel", bottomHalf, -depth, -bottomHalf, -depth, true);
        }
        builder.add("side", -bottomHalf, -depth, -topHalf, 0.0D, true);
    }

    private static void railSurfaces(SurfaceBuilder builder, List<PatternedBox> boxes, PipeStyleGeometry geometry, boolean heavy) {
        double gauge = geometry.gauge();
        double railHalf = geometry.railWidth() * 0.5D;
        double railHeight = geometry.railHeight();
        double railCenter = gauge * 0.5D;
        addRect(builder, "rail", -railCenter - railHalf, -railHeight, -railCenter + railHalf, 0.0D);
        addRect(builder, "rail", railCenter - railHalf, -railHeight, railCenter + railHalf, 0.0D);
        double tieHalf = railCenter + railHalf + geometry.tieWidth() * 0.5D;
        double tieTop = -railHeight - 0.022D;
        double tieBottom = tieTop - Math.max(0.025D, railHeight * 0.32D);
        boxes.add(new PatternedBox("tie", -tieHalf, tieBottom, tieHalf, tieTop, geometry.tieInterval(), geometry.tieWidth(), 0.0D, 1));
        if (heavy) {
            double bedH = Math.max(0.050D, geometry.railHeight() * 0.48D);
            addRect(builder, "bed", -tieHalf, tieBottom - bedH - 0.010D, tieHalf, tieBottom - 0.010D);
        }
    }

    private static void slideSurfaces(SurfaceBuilder builder, PipeStyleGeometry geometry, boolean rim, boolean curved, int lod) {
        if (curved) {
            curvedSlideSurfaces(builder, geometry, rim, lod);
            return;
        }
        double halfWidth = geometry.halfWidth();
        double floorHalf = Math.max(0.04D, halfWidth * geometry.floorRatio());
        double topExtra = halfWidth * geometry.wallSlope();
        double topHalf = halfWidth + topExtra * 0.35D;
        double depth = geometry.depth();
        builder.add("floor", -floorHalf, 0.0D, floorHalf, 0.0D, true);
        builder.add("wall", floorHalf, 0.0D, topHalf, depth, true);
        if (rim) {
            builder.add("rim", topHalf, depth, Math.max(floorHalf, topHalf - geometry.rimWidth()), depth, true);
            builder.add("rim", -Math.max(floorHalf, topHalf - geometry.rimWidth()), depth, -topHalf, depth, true);
        }
        builder.add("wall", -topHalf, depth, -floorHalf, 0.0D, true);
    }

    private static void curvedSlideSurfaces(SurfaceBuilder builder, PipeStyleGeometry geometry, boolean rim, int lod) {
        double halfWidth = geometry.halfWidth();
        double topHalf = halfWidth + halfWidth * geometry.wallSlope() * 0.35D;
        double depth = geometry.depth();
        double rimWidth = rim ? Math.min(geometry.rimWidth(), topHalf * 0.32D) : 0.0D;
        double bodyTopHalf = rim ? Math.max(halfWidth * 0.38D, topHalf - rimWidth) : topHalf;
        double floorLimit = Math.max(0.08D, bodyTopHalf * geometry.floorRatio() * 0.52D);
        int segments = curvedSlideSegments(lod);
        Local2 previous = null;
        for (int i = 0; i <= segments; i++) {
            double t = -1.0D + 2.0D * i / segments;
            double x = t * bodyTopHalf;
            double y = depth * Math.pow(Math.abs(t), 1.85D);
            Local2 current = new Local2(x, y);
            if (previous != null) {
                double midX = (previous.x() + current.x()) * 0.5D;
                String slot = Math.abs(midX) <= floorLimit ? "floor" : "wall";
                builder.add(slot, previous.x(), previous.y(), current.x(), current.y(), true);
            }
            previous = current;
        }
        if (rim) {
            double rimLift = Math.max(0.028D, rimWidth * 0.48D);
            double capInset = Math.max(bodyTopHalf, topHalf - rimWidth * 0.46D);
            builder.add("rim", bodyTopHalf, depth, topHalf, depth + rimLift, true);
            builder.add("rim", topHalf, depth + rimLift, capInset, depth + rimLift, true);
            builder.add("rim", -capInset, depth + rimLift, -topHalf, depth + rimLift, true);
            builder.add("rim", -topHalf, depth + rimLift, -bodyTopHalf, depth, true);
        }
    }

    private static void monorailSurfaces(SurfaceBuilder builder, PipeStyleGeometry geometry, boolean heavy) {
        double w = geometry.halfWidth();
        double h = geometry.halfHeight();
        double trackHalf = Math.min(w * 0.55D, Math.max(geometry.edgeWidth(), w * 0.34D));
        builder.add("track", -trackHalf, 0.0D, trackHalf, 0.0D, true);
        if (heavy) {
            builder.add("edge", trackHalf, 0.0D, w, -h * 0.28D, true);
            builder.add("beam", w, -h * 0.28D, w * 0.42D, -h * 2.0D, true);
            builder.add("beam", w * 0.42D, -h * 2.0D, -w * 0.42D, -h * 2.0D, true);
            builder.add("beam", -w * 0.42D, -h * 2.0D, -w, -h * 0.28D, true);
            builder.add("edge", -w, -h * 0.28D, -trackHalf, 0.0D, true);
        } else {
            builder.add("beam", trackHalf, 0.0D, w * 0.52D, -h * 2.0D, true);
            builder.add("beam", w * 0.52D, -h * 2.0D, -w * 0.52D, -h * 2.0D, true);
            builder.add("beam", -w * 0.52D, -h * 2.0D, -trackHalf, 0.0D, true);
        }
    }

    private static void coveredSurfaces(SurfaceBuilder builder, List<PatternedBox> boxes, PipeStyleGeometry geometry, boolean framed, boolean ringed, int lod) {
        double halfWidth = geometry.halfWidth();
        double height = geometry.halfHeight();
        double baseThickness = Math.max(0.035D, geometry.rimWidth());
        double floorHalf = Math.max(halfWidth * 0.44D, halfWidth - baseThickness * 1.55D);
        builder.add("base", -floorHalf, 0.0D, floorHalf, 0.0D, true);
        builder.add("base", floorHalf, 0.0D, halfWidth, -baseThickness, true);
        builder.add("base", halfWidth, -baseThickness, -halfWidth, -baseThickness, true);
        builder.add("base", -halfWidth, -baseThickness, -floorHalf, 0.0D, true);

        List<Local2> arch = new ArrayList<>();
        int segments = coveredArchSegments(lod);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI - Math.PI * i / segments;
            arch.add(new Local2(Math.cos(angle) * halfWidth, Math.sin(angle) * height));
        }
        for (int i = 0; i + 1 < arch.size(); i++) {
            Local2 a = arch.get(i);
            Local2 b = arch.get(i + 1);
            builder.add("canopy", a.x(), a.y(), b.x(), b.y(), true);
        }

        if (framed) {
            double frameWidth = Math.max(0.020D, baseThickness * 0.52D);
            addRect(builder, "frame", -halfWidth - frameWidth * 0.25D, -baseThickness * 0.15D, -halfWidth + frameWidth, height * 0.22D);
            addRect(builder, "frame", halfWidth - frameWidth, -baseThickness * 0.15D, halfWidth + frameWidth * 0.25D, height * 0.22D);
            addRect(builder, "frame", -frameWidth * 0.60D, height - frameWidth * 0.40D, frameWidth * 0.60D, height + frameWidth * 0.80D);
        }
        if (ringed) {
            double frameHeight = Math.max(0.020D, baseThickness * 0.42D);
            boxes.add(new PatternedBox("frame", -halfWidth * 1.02D, height * 0.76D, halfWidth * 1.02D, height * 0.76D + frameHeight, 0.62D, 0.060D, 0.08D, 2));
        }
    }

    private static void addRect(SurfaceBuilder builder, String slotId, double left, double bottom, double right, double top) {
        builder.add(slotId, left, top, right, top, true);
        builder.add(slotId, right, top, right, bottom, true);
        builder.add(slotId, right, bottom, left, bottom, true);
        builder.add(slotId, left, bottom, left, top, true);
    }

    private static void addMarkerSurface(PipeStyleShape shape, SurfaceBuilder builder, PipeStyleGeometry geometry, PipeVariantDefinition variant) {
        switch (shape) {
            case ROUND -> {
                double radius = geometry.radius();
                addRadialMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, radius * 1.08D, Math.toRadians(-34.0D), ACCELERATION_ANCHOR_WIDTH);
                addRadialMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, radius * 1.10D, Math.toRadians(56.0D), HIGHWAY_ANCHOR_WIDTH);
                addRadialMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, radius * 1.10D, Math.toRadians(-58.0D), DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, radius * 1.11D, PLATFORM_ANCHOR_WIDTH);
            }
            case FACETED -> {
                double radius = geometry.radius();
                addRadialMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, radius * 1.05D, Math.toRadians(-30.0D), ACCELERATION_ANCHOR_WIDTH);
                addRadialMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, radius * 1.08D, Math.toRadians(52.0D), HIGHWAY_ANCHOR_WIDTH);
                addRadialMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, radius * 1.08D, Math.toRadians(-54.0D), DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, radius * 1.08D, PLATFORM_ANCHOR_WIDTH);
            }
            case BOX -> {
                double w = geometry.halfWidth();
                double h = geometry.halfHeight();
                addHorizontalMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, -w * 0.34D, h + 0.018D, ACCELERATION_ANCHOR_WIDTH);
                addVerticalMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, w + 0.018D, h * 0.34D, HIGHWAY_ANCHOR_WIDTH);
                addVerticalMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, -w - 0.018D, h * 0.34D, DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, h + 0.022D, PLATFORM_ANCHOR_WIDTH);
            }
            case TRIANGLE -> {
                double topHalf = Math.max(geometry.halfWidth() * geometry.topFlatness(), geometry.halfWidth() * 0.10D);
                double sideX = Math.max(topHalf + ACCELERATION_MARKER_WIDTH, geometry.halfWidth() * 0.38D);
                addHorizontalMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, 0.0D, 0.026D, ACCELERATION_ANCHOR_WIDTH);
                addAngledMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, sideX, -geometry.depth() * 0.36D, -0.48D, HIGHWAY_ANCHOR_WIDTH);
                addAngledMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, -sideX, -geometry.depth() * 0.36D, 0.48D, DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, 0.030D, PLATFORM_ANCHOR_WIDTH);
            }
            case RAIL -> {
                double half = geometry.gauge() * 0.5D + geometry.railWidth() + geometry.tieWidth() * 0.46D;
                addHorizontalMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, 0.0D, 0.050D, ACCELERATION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, half + HIGHWAY_ANCHOR_WIDTH * 0.42D, 0.036D, HIGHWAY_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, -half - DIRECTION_ANCHOR_WIDTH * 0.42D, 0.036D, DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, 0.060D, PLATFORM_ANCHOR_WIDTH);
            }
            case SLIDE -> {
                double halfWidth = geometry.halfWidth();
                double floorHalf = variant.curved() ? halfWidth * Math.max(0.25D, geometry.floorRatio() * 0.42D) : Math.max(0.04D, halfWidth * geometry.floorRatio());
                addHorizontalMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, -floorHalf * 0.50D, 0.034D, ACCELERATION_ANCHOR_WIDTH);
                addAngledMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, halfWidth * 0.82D, geometry.depth() * 0.54D, 0.58D, HIGHWAY_ANCHOR_WIDTH);
                addAngledMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, -halfWidth * 0.82D, geometry.depth() * 0.54D, -0.58D, DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, 0.040D, PLATFORM_ANCHOR_WIDTH);
            }
            case MONORAIL -> {
                double trackHalf = Math.min(geometry.halfWidth() * 0.55D, Math.max(geometry.edgeWidth(), geometry.halfWidth() * 0.34D));
                addHorizontalMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, 0.0D, 0.038D, ACCELERATION_ANCHOR_WIDTH);
                addVerticalMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, geometry.halfWidth() + 0.018D, -geometry.halfHeight() * 0.62D, HIGHWAY_ANCHOR_WIDTH);
                addVerticalMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, -geometry.halfWidth() - 0.018D, -geometry.halfHeight() * 0.62D, DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, 0.044D, PLATFORM_ANCHOR_WIDTH);
            }
            case COVERED -> {
                double floorHalf = Math.max(geometry.halfWidth() * 0.34D, geometry.halfWidth() - geometry.rimWidth() * 1.80D);
                addHorizontalMarker(builder, INTERNAL_MARKER_ACCELERATION_SLOT, -floorHalf * 0.45D, 0.045D, ACCELERATION_ANCHOR_WIDTH);
                addVerticalMarker(builder, INTERNAL_MARKER_HIGHWAY_SLOT, geometry.halfWidth() + 0.020D, -geometry.rimWidth() * 0.15D, HIGHWAY_ANCHOR_WIDTH);
                addVerticalMarker(builder, INTERNAL_MARKER_DIRECTION_SLOT, -geometry.halfWidth() - 0.020D, -geometry.rimWidth() * 0.15D, DIRECTION_ANCHOR_WIDTH);
                addHorizontalMarker(builder, INTERNAL_MARKER_PLATFORM_SLOT, 0.0D, 0.050D, PLATFORM_ANCHOR_WIDTH);
            }
        }
    }

    private static void addHorizontalMarker(SurfaceBuilder builder, String slotId, double centerX, double centerY, double width) {
        builder.add(slotId, centerX - width * 0.5D, centerY, centerX + width * 0.5D, centerY, false);
    }

    private static void addVerticalMarker(SurfaceBuilder builder, String slotId, double centerX, double centerY, double width) {
        builder.add(slotId, centerX, centerY - width * 0.5D, centerX, centerY + width * 0.5D, false);
    }

    private static void addAngledMarker(SurfaceBuilder builder, String slotId, double centerX, double centerY, double slope, double width) {
        double dx = width * 0.5D / Math.sqrt(1.0D + slope * slope);
        double dy = dx * slope;
        builder.add(slotId, centerX - dx, centerY - dy, centerX + dx, centerY + dy, false);
    }

    private static void addRadialMarker(SurfaceBuilder builder, String slotId, double radius, double angle, double width) {
        double centerX = Math.sin(angle) * radius;
        double centerY = Math.cos(angle) * radius;
        double tangentX = Math.cos(angle);
        double tangentY = -Math.sin(angle);
        builder.add(slotId, centerX - tangentX * width * 0.5D, centerY - tangentY * width * 0.5D, centerX + tangentX * width * 0.5D, centerY + tangentY * width * 0.5D, false);
    }

    private static List<Local2> polygonPoints(double radius, int sides, double angleOffset) {
        List<Local2> points = new ArrayList<>();
        for (int i = 0; i < sides; i++) {
            double angle = Math.PI * 2.0D * i / sides + angleOffset;
            points.add(new Local2(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
        return List.copyOf(points);
    }

    private static MarkerLanes markerLanes(SurfaceBuilder builder) {
        LocalSurface top = builder.highestRenderable();
        LocalSurface acceleration = builder.firstBySlot(INTERNAL_MARKER_ACCELERATION_SLOT).orElse(top);
        LocalSurface highway = builder.firstBySlot(INTERNAL_MARKER_HIGHWAY_SLOT).orElse(acceleration);
        LocalSurface direction = builder.firstBySlot(INTERNAL_MARKER_DIRECTION_SLOT).orElse(acceleration);
        LocalSurface platform = builder.firstBySlot(INTERNAL_MARKER_PLATFORM_SLOT).orElse(acceleration);
        return new MarkerLanes(
                center(acceleration),
                ACCELERATION_MARKER_WIDTH,
                center(highway),
                HIGHWAY_MARKER_WIDTH,
                center(direction),
                DIRECTION_MARKER_WIDTH,
                center(platform),
                PLATFORM_MARKER_WIDTH);
    }

    private static boolean isInternalSlot(String slotId) {
        return slotId.startsWith("__");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum FaceVisibility {
        SINGLE_SIDED_OUTWARD,
        TWO_SIDED
    }

    public record LocalSurface(String slotId, double ax, double ay, double bx, double by, double vStart, double vEnd, boolean render, FaceVisibility visibility) {}

    public record Local2(double x, double y) {}

    public record CoatingBand(String slotId, double vCenter, double vWidth, double period, double length, double phase, int layer) {}

    public record PatternedBox(String slotId, double left, double bottom, double right, double top, double period, double length, double phase, int layer) {}

    public record MarkerLanes(double accelerationCenter, double accelerationWidth, double highwayCenter, double highwayWidth, double directionCenter, double directionWidth, double platformCenter, double platformWidth) {}

    private static final class SurfaceBuilder {
        private final List<LocalSurface> surfaces = new ArrayList<>();
        private double cursor;

        LocalSurface add(String slotId, double ax, double ay, double bx, double by, boolean render) {
            return this.add(slotId, ax, ay, bx, by, render, FaceVisibility.TWO_SIDED);
        }

        LocalSurface add(String slotId, double ax, double ay, double bx, double by, boolean render, FaceVisibility visibility) {
            double length = Math.hypot(bx - ax, by - ay);
            if (length <= EPSILON) {
                length = EPSILON;
            }
            LocalSurface surface = new LocalSurface(slotId, ax, ay, bx, by, this.cursor, this.cursor + length, render, visibility);
            this.surfaces.add(surface);
            this.cursor += length;
            return surface;
        }

        List<LocalSurface> surfaces() {
            return this.surfaces;
        }

        double perimeter() {
            return Math.max(EPSILON, this.cursor);
        }

        Optional<LocalSurface> firstBySlot(String slotId) {
            return this.surfaces.stream()
                    .filter(surface -> surface.slotId().equals(slotId))
                    .findFirst();
        }

        LocalSurface highestRenderable() {
            return this.surfaces.stream()
                    .filter(LocalSurface::render)
                    .max(java.util.Comparator.comparingDouble(surface -> (surface.ay() + surface.by()) * 0.5D))
                    .orElseGet(() -> this.surfaces.isEmpty() ? new LocalSurface("body", -0.1D, 0.0D, 0.1D, 0.0D, 0.0D, 0.2D, true, FaceVisibility.TWO_SIDED) : this.surfaces.getFirst());
        }
    }
}
