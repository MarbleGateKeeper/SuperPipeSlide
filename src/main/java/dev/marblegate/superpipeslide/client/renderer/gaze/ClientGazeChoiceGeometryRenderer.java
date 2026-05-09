package dev.marblegate.superpipeslide.client.renderer.gaze;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.core.gaze.ClientGazeChoiceController;
import dev.marblegate.superpipeslide.client.core.gaze.ClientGazeChoiceController.ChoiceVisualState;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.common.core.gaze.GazeChoiceShapeType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

public final class ClientGazeChoiceGeometryRenderer {
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "gaze_choice_geometry"));
    private static final int GRID_SIZE = 16;
    private static final double BASE_THICKNESS = 0.105D;

    private ClientGazeChoiceGeometryRenderer() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Vec3 camera = event.getCamera().position();
        List<ChoiceMesh> choices = new ArrayList<>();
        for (ClientGazeChoiceController.RenderChoice choice : ClientGazeChoiceController.renderChoices()) {
            BillboardFrame frame = billboardFrame(choice, camera);
            Vec3 arrowDirection = arrowDirectionInBillboard(choice, frame);
            choices.add(new ChoiceMesh(
                    choice.position(),
                    frame.right(),
                    frame.up(),
                    frame.normal(),
                    choice.radius(),
                    Math.max(0.035D, choice.radius() * BASE_THICKNESS),
                    choice.shape().type(),
                    arrowDirection,
                    choice.colors(),
                    choice.recommended(),
                    colorWithState(choice.primaryColor(), choice.visualState(), choice.focusProgress()),
                    choice.focusProgress(),
                    choice.resultProgress(),
                    choice.visualState()
            ));
        }
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(List.copyOf(choices)));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        RenderData renderData = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (renderData == null || renderData.choices().isEmpty()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            for (ChoiceMesh choice : renderData.choices()) {
                renderChoice(pose, buffer, choice);
            }
        });
        poseStack.popPose();
    }

    private static void renderChoice(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice) {
        renderPlate(pose, buffer, choice);
        if (choice.shapeType() == GazeChoiceShapeType.ARROW) {
            renderArrow(pose, buffer, choice);
        } else {
            boolean[][] mask = buildMask(choice.shapeType(), choice.arrowDirection());
            boolean[][] glowMask = outlineMask(mask, choice.focusProgress() > 0.0D ? 2 : 1);
            renderCells(pose, buffer, choice, glowMask, glowColor(choice), choice.thickness() * 0.82D);
            renderCells(pose, buffer, choice, mask, choice.color(), choice.thickness());
        }
        renderColorTabs(pose, buffer, choice);
        if (choice.recommended()) {
            renderRecommendedMark(pose, buffer, choice);
        }
    }

    private static void renderArrow(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice) {
        Vec3[] points = arrowPolygon(choice.arrowDirection(), choice.focusProgress() > 0.0D ? 1.18D : 1.08D);
        renderArrowPrism(pose, buffer, choice, points, glowColor(choice), choice.thickness() * 0.82D);
        renderArrowPrism(pose, buffer, choice, arrowPolygon(choice.arrowDirection(), 1.0D), choice.color(), choice.thickness());
    }

    private static void renderPlate(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice) {
        double scale = choice.visualState() == ChoiceVisualState.READY ? 1.08D : 1.0D;
        Vec3[] plate = octagonalPlate(scale);
        int shadow = switch (choice.visualState()) {
            case ACCEPTED -> 0xA0163526;
            case REJECTED -> 0xA03C1714;
            case SUBMITTED, READY, FOCUSED -> 0xAA101820;
            case IDLE -> 0x78101820;
        };
        renderFlatPolygon(pose, buffer, choice, plate, shadow, -choice.thickness() * 0.68D);
        if (choice.focusProgress() > 0.0D || choice.visualState() == ChoiceVisualState.SUBMITTED || choice.visualState() == ChoiceVisualState.ACCEPTED || choice.visualState() == ChoiceVisualState.REJECTED) {
            renderPlateOutline(pose, buffer, choice, plate, glowColor(choice), choice.thickness() * 0.5D);
        }
    }

    private static Vec3[] octagonalPlate(double scale) {
        double wide = 1.16D * scale;
        double high = 0.86D * scale;
        double cut = 0.22D * scale;
        return new Vec3[]{
                new Vec3(-wide + cut, -high, 0.0D),
                new Vec3(wide - cut, -high, 0.0D),
                new Vec3(wide, -high + cut, 0.0D),
                new Vec3(wide, high - cut, 0.0D),
                new Vec3(wide - cut, high, 0.0D),
                new Vec3(-wide + cut, high, 0.0D),
                new Vec3(-wide, high - cut, 0.0D),
                new Vec3(-wide, -high + cut, 0.0D)
        };
    }

    private static void renderPlateOutline(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice, Vec3[] plate, int color, double z) {
        Vec3[] expanded = new Vec3[plate.length];
        for (int i = 0; i < plate.length; i++) {
            expanded[i] = plate[i].scale(1.11D);
        }
        renderFlatPolygon(pose, buffer, choice, expanded, withAlpha(color, outlineAlpha(choice)), z);
    }

    private static void renderFlatPolygon(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice, Vec3[] points, int color, double z) {
        Vec3 center = Vec3.ZERO;
        for (int i = 0; i < points.length; i++) {
            Vec3 a = points[i];
            Vec3 b = points[(i + 1) % points.length];
            addTriangle(pose, buffer, choice, color, center, z, a, z, b, z, choice.normal());
        }
    }

    private static void renderColorTabs(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice) {
        if (choice.colors().isEmpty() || choice.visualState() == ChoiceVisualState.REJECTED) {
            return;
        }
        List<Integer> colors = choice.colors().stream().limit(3).toList();
        double totalWidth = 1.28D;
        double tabWidth = totalWidth / colors.size();
        double start = -totalWidth * 0.5D;
        double y0 = -0.98D;
        double y1 = -0.84D;
        double z = choice.thickness() * 0.76D;
        for (int i = 0; i < colors.size(); i++) {
            double x0 = start + i * tabWidth;
            double x1 = x0 + tabWidth + 0.01D;
            int color = withAlpha(colors.get(i), choice.visualState() == ChoiceVisualState.IDLE ? 0xD0 : 0xFF);
            addFace(pose, buffer, choice, color, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, choice.normal());
        }
    }

    private static void renderRecommendedMark(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice) {
        double z = choice.thickness() * 0.92D;
        double cx = 0.82D;
        double cy = 0.72D;
        double r = 0.16D;
        int color = withAlpha(0xFFEAFBFF, choice.visualState() == ChoiceVisualState.IDLE ? 0xD8 : 0xFF);
        Vec3 top = new Vec3(cx, cy + r, 0.0D);
        Vec3 right = new Vec3(cx + r, cy, 0.0D);
        Vec3 bottom = new Vec3(cx, cy - r, 0.0D);
        Vec3 left = new Vec3(cx - r, cy, 0.0D);
        addTriangle(pose, buffer, choice, color, top, z, right, z, bottom, z, choice.normal());
        addTriangle(pose, buffer, choice, color, top, z, bottom, z, left, z, choice.normal());
    }

    private static void renderCells(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice, boolean[][] mask, int color, double thickness) {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (!mask[x][y]) {
                    continue;
                }

                double x0 = -1.0D + x * 2.0D / GRID_SIZE;
                double x1 = -1.0D + (x + 1) * 2.0D / GRID_SIZE;
                double y0 = -1.0D + y * 2.0D / GRID_SIZE;
                double y1 = -1.0D + (y + 1) * 2.0D / GRID_SIZE;
                double z0 = -thickness * 0.5D;
                double z1 = thickness * 0.5D;

                addFace(pose, buffer, choice, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, choice.normal());
                addFace(pose, buffer, choice, color, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, choice.normal().scale(-1.0D));
                if (x == 0 || !mask[x - 1][y]) {
                    addFace(pose, buffer, choice, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, choice.right().scale(-1.0D));
                }
                if (x == GRID_SIZE - 1 || !mask[x + 1][y]) {
                    addFace(pose, buffer, choice, color, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, choice.right());
                }
                if (y == 0 || !mask[x][y - 1]) {
                    addFace(pose, buffer, choice, color, x1, y0, z0, x0, y0, z0, x0, y0, z1, x1, y0, z1, choice.up().scale(-1.0D));
                }
                if (y == GRID_SIZE - 1 || !mask[x][y + 1]) {
                    addFace(pose, buffer, choice, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, choice.up());
                }
            }
        }
    }

    private static boolean[][] buildMask(GazeChoiceShapeType shapeType, Vec3 arrowDirection) {
        boolean[][] mask = new boolean[GRID_SIZE][GRID_SIZE];
        Vec3 direction = safeNormalize(arrowDirection, new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 side = new Vec3(-direction.y, direction.x, 0.0D);
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                double lx = -1.0D + (x + 0.5D) * 2.0D / GRID_SIZE;
                double ly = -1.0D + (y + 0.5D) * 2.0D / GRID_SIZE;
                if (shapeType == GazeChoiceShapeType.CIRCLE) {
                    mask[x][y] = lx * lx + ly * ly <= 0.82D * 0.82D;
                } else {
                    double along = lx * direction.x + ly * direction.y;
                    double across = lx * side.x + ly * side.y;
                    boolean shaft = along >= -0.88D && along <= 0.18D && Math.abs(across) <= 0.28D;
                    double headProgress = Math.max(0.0D, Math.min(1.0D, (along - 0.08D) / 0.82D));
                    double headWidth = 0.68D * (1.0D - headProgress);
                    boolean head = along >= 0.08D && along <= 0.90D && Math.abs(across) <= headWidth;
                    mask[x][y] = shaft || head;
                }
            }
        }
        return mask;
    }

    private static Vec3[] arrowPolygon(Vec3 arrowDirection, double scale) {
        Vec3 direction = safeNormalize(arrowDirection, new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 side = new Vec3(-direction.y, direction.x, 0.0D);
        return new Vec3[]{
                arrowPoint(direction, side, -0.86D, -0.22D, scale),
                arrowPoint(direction, side, 0.14D, -0.22D, scale),
                arrowPoint(direction, side, 0.14D, -0.54D, scale),
                arrowPoint(direction, side, 0.90D, 0.0D, scale),
                arrowPoint(direction, side, 0.14D, 0.54D, scale),
                arrowPoint(direction, side, 0.14D, 0.22D, scale),
                arrowPoint(direction, side, -0.86D, 0.22D, scale)
        };
    }

    private static Vec3 arrowPoint(Vec3 direction, Vec3 side, double along, double across, double scale) {
        return direction.scale(along * scale).add(side.scale(across * scale));
    }

    private static void renderArrowPrism(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice, Vec3[] points, int color, double thickness) {
        double z0 = -thickness * 0.5D;
        double z1 = thickness * 0.5D;

        addTriangle(pose, buffer, choice, color, points[0], z1, points[1], z1, points[5], z1, choice.normal());
        addTriangle(pose, buffer, choice, color, points[0], z1, points[5], z1, points[6], z1, choice.normal());
        addTriangle(pose, buffer, choice, color, points[2], z1, points[3], z1, points[4], z1, choice.normal());

        addTriangle(pose, buffer, choice, color, points[5], z0, points[1], z0, points[0], z0, choice.normal().scale(-1.0D));
        addTriangle(pose, buffer, choice, color, points[6], z0, points[5], z0, points[0], z0, choice.normal().scale(-1.0D));
        addTriangle(pose, buffer, choice, color, points[4], z0, points[3], z0, points[2], z0, choice.normal().scale(-1.0D));

        for (int i = 0; i < points.length; i++) {
            Vec3 a = points[i];
            Vec3 b = points[(i + 1) % points.length];
            Vec3 edgeNormal = localEdgeNormal(a, b);
            addFace(pose, buffer, choice, color, a.x, a.y, z0, b.x, b.y, z0, b.x, b.y, z1, a.x, a.y, z1, edgeNormal);
        }
    }

    private static Vec3 localEdgeNormal(Vec3 a, Vec3 b) {
        Vec3 edge = b.subtract(a);
        return safeNormalize(new Vec3(edge.y, -edge.x, 0.0D), new Vec3(1.0D, 0.0D, 0.0D));
    }

    private static boolean[][] outlineMask(boolean[][] source, int radius) {
        boolean[][] mask = new boolean[GRID_SIZE][GRID_SIZE];
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (source[x][y]) {
                    continue;
                }

                boolean nearShape = false;
                for (int oy = -radius; oy <= radius && !nearShape; oy++) {
                    for (int ox = -radius; ox <= radius; ox++) {
                        int sx = x + ox;
                        int sy = y + oy;
                        if (sx < 0 || sx >= GRID_SIZE || sy < 0 || sy >= GRID_SIZE) {
                            continue;
                        }
                        if (Math.abs(ox) + Math.abs(oy) <= radius && source[sx][sy]) {
                            nearShape = true;
                            break;
                        }
                    }
                }
                mask[x][y] = nearShape;
            }
        }
        return mask;
    }

    private static void addFace(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice, int color,
                                double ax, double ay, double az,
                                double bx, double by, double bz,
                                double cx, double cy, double cz,
                                double dx, double dy, double dz,
                                Vec3 normal) {
        addVertex(pose, buffer, choice, color, ax, ay, az, normal);
        addVertex(pose, buffer, choice, color, bx, by, bz, normal);
        addVertex(pose, buffer, choice, color, cx, cy, cz, normal);
        addVertex(pose, buffer, choice, color, dx, dy, dz, normal);
    }

    private static void addTriangle(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice, int color,
                                    Vec3 a, double az, Vec3 b, double bz, Vec3 c, double cz, Vec3 normal) {
        addVertex(pose, buffer, choice, color, a.x, a.y, az, normal);
        addVertex(pose, buffer, choice, color, b.x, b.y, bz, normal);
        addVertex(pose, buffer, choice, color, c.x, c.y, cz, normal);
        addVertex(pose, buffer, choice, color, c.x, c.y, cz, normal);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer buffer, ChoiceMesh choice, int color, double localX, double localY, double localZ, Vec3 normal) {
        Vec3 world = choice.center()
                .add(choice.right().scale(localX * choice.radius()))
                .add(choice.up().scale(localY * choice.radius()))
                .add(choice.normal().scale(localZ));
        buffer.addVertex(pose, (float) world.x, (float) world.y, (float) world.z)
                .setColor(color);
    }

    private static BillboardFrame billboardFrame(ClientGazeChoiceController.RenderChoice choice, Vec3 camera) {
        Vec3 normal = safeNormalize(camera.subtract(choice.position()), choice.forward());
        Vec3 upHint = projectOntoPlane(choice.up(), normal, new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 right = safeNormalize(normal.cross(upHint), choice.right());
        Vec3 up = safeNormalize(right.cross(normal), upHint);
        return new BillboardFrame(right, up, normal);
    }

    private static Vec3 arrowDirectionInBillboard(ClientGazeChoiceController.RenderChoice choice, BillboardFrame frame) {
        Vec3 local = choice.shape().arrowDirectionLocal();
        Vec3 world = choice.right().scale(local.x).add(choice.up().scale(local.y)).add(choice.forward().scale(local.z));
        Vec3 projected = projectOntoPlane(world, frame.normal(), frame.right());
        return safeNormalize(new Vec3(projected.dot(frame.right()), projected.dot(frame.up()), 0.0D), new Vec3(1.0D, 0.0D, 0.0D));
    }

    private static Vec3 projectOntoPlane(Vec3 vector, Vec3 normal, Vec3 fallback) {
        Vec3 projected = vector.subtract(normal.scale(vector.dot(normal)));
        return safeNormalize(projected, fallback);
    }

    private static Vec3 safeNormalize(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() < 1.0E-6D ? fallback : vector.normalize();
    }

    private static int colorWithState(int argb, ChoiceVisualState state, double focusProgress) {
        if (state == ChoiceVisualState.ACCEPTED) {
            return 0xFFE6FFF0;
        }
        if (state == ChoiceVisualState.REJECTED) {
            return 0xFFFFB49E;
        }
        if (state == ChoiceVisualState.SUBMITTED) {
            return 0xFFD8F4FF;
        }
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int alpha = switch (state) {
            case READY -> 0xFF;
            case FOCUSED -> 0xF0;
            case IDLE -> 0xCC;
            default -> 0xFF;
        };
        double boost = 1.0D + focusProgress * 0.12D + (state == ChoiceVisualState.READY ? 0.08D : 0.0D);
        r = Math.min(255, (int) Math.round(r * boost));
        g = Math.min(255, (int) Math.round(g * boost));
        b = Math.min(255, (int) Math.round(b * boost));
        return alpha << 24 | r << 16 | g << 8 | b;
    }

    private static int glowColor(ChoiceMesh choice) {
        if (choice.visualState() == ChoiceVisualState.ACCEPTED) {
            return 0xD065FF9B;
        }
        if (choice.visualState() == ChoiceVisualState.REJECTED) {
            return 0xD0FF6F54;
        }
        if (choice.visualState() == ChoiceVisualState.SUBMITTED) {
            return 0xD0D8F4FF;
        }
        int argb = choice.color();
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        double boost = 1.22D + choice.focusProgress() * 0.18D + (choice.visualState() == ChoiceVisualState.READY ? 0.16D : 0.0D);
        r = Math.min(255, (int) Math.round(r * boost + 24.0D));
        g = Math.min(255, (int) Math.round(g * boost + 24.0D));
        b = Math.min(255, (int) Math.round(b * boost + 24.0D));
        return 0xD0000000 | r << 16 | g << 8 | b;
    }

    private static int outlineAlpha(ChoiceMesh choice) {
        return switch (choice.visualState()) {
            case READY -> 0xD8;
            case FOCUSED -> 0xA0 + (int) Math.round(choice.focusProgress() * 48.0D);
            case SUBMITTED -> 0xC8;
            case ACCEPTED, REJECTED -> 0xE0;
            case IDLE -> 0x70;
        };
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    private record RenderData(List<ChoiceMesh> choices) {
    }

    private record ChoiceMesh(Vec3 center, Vec3 right, Vec3 up, Vec3 normal, double radius, double thickness,
                              GazeChoiceShapeType shapeType, Vec3 arrowDirection, List<Integer> colors, boolean recommended, int color,
                              double focusProgress, double resultProgress, ChoiceVisualState visualState) {
    }

    private record BillboardFrame(Vec3 right, Vec3 up, Vec3 normal) {
    }
}
