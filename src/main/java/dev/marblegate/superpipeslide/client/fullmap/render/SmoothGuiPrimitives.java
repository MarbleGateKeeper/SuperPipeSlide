package dev.marblegate.superpipeslide.client.fullmap.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SmoothGuiPrimitives {
    private static final double EDGE_AA_PX = 0.9D;

    private SmoothGuiPrimitives() {
    }

    public static void line(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, double width, int color) {
        if (a == null || b == null || alpha(color) <= 0 || width <= 0.0D) {
            return;
        }
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.capsule(a, b, width + EDGE_AA_PX, withScaledAlpha(color, 0.32D));
        builder.capsule(a, b, width, color);
        builder.submit(graphics);
    }

    public static void polyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color) {
        polyline(graphics, points, width, color, true);
    }

    public static void polyline(GuiGraphicsExtractor graphics, List<Vec2> points, double width, int color, boolean roundCaps) {
        if (points == null || points.size() < 2 || alpha(color) <= 0 || width <= 0.0D) {
            return;
        }
        List<Vec2> cleaned = cleanPolyline(points);
        if (cleaned.size() < 2) {
            return;
        }
        List<Vec2> path = roundedPolyline(cleaned, width);
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.stroke(path, width + EDGE_AA_PX, withScaledAlpha(color, 0.32D), roundCaps, roundCaps);
        builder.stroke(path, width, color, roundCaps, roundCaps);
        builder.submit(graphics);
    }

    public static void circle(GuiGraphicsExtractor graphics, Vec2 center, double radius, int color) {
        if (center == null || alpha(color) <= 0 || radius <= 0.0D) {
            return;
        }
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.circle(center, radius + EDGE_AA_PX, withScaledAlpha(color, 0.28D));
        builder.circle(center, radius, color);
        builder.submit(graphics);
    }

    public static void ring(GuiGraphicsExtractor graphics, Vec2 center, double outerRadius, double thickness, int color) {
        if (center == null || alpha(color) <= 0 || outerRadius <= 0.0D || thickness <= 0.0D) {
            return;
        }
        double safeThickness = Math.min(thickness, outerRadius);
        double innerRadius = Math.max(0.0D, outerRadius - safeThickness);
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.ring(center, outerRadius + EDGE_AA_PX, Math.max(0.0D, innerRadius - EDGE_AA_PX), withScaledAlpha(color, 0.28D));
        builder.ring(center, outerRadius, innerRadius, color);
        builder.submit(graphics);
    }

    public static void capsule(GuiGraphicsExtractor graphics, Vec2 center, double width, double height, int color) {
        if (center == null || alpha(color) <= 0 || width <= 0.0D || height <= 0.0D) {
            return;
        }
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.capsuleShape(center, width + EDGE_AA_PX * 2.0D, height + EDGE_AA_PX * 2.0D, withScaledAlpha(color, 0.28D));
        builder.capsuleShape(center, width, height, color);
        builder.submit(graphics);
    }

    public static void capsule(GuiGraphicsExtractor graphics, Vec2 center, Vec2 axis, double length, double height, int color) {
        if (center == null || axis == null || alpha(color) <= 0 || length <= 0.0D || height <= 0.0D) {
            return;
        }
        double axisLength = Math.hypot(axis.x(), axis.y());
        if (axisLength <= 0.001D || length <= height * 1.04D) {
            circle(graphics, center, Math.max(length, height) * 0.5D, color);
            return;
        }
        double ux = axis.x() / axisLength;
        double uy = axis.y() / axisLength;
        double halfSegment = Math.max(0.0D, (length - height) * 0.5D);
        Vec2 a = new Vec2(center.x() - ux * halfSegment, center.y() - uy * halfSegment);
        Vec2 b = new Vec2(center.x() + ux * halfSegment, center.y() + uy * halfSegment);
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.capsule(a, b, height + EDGE_AA_PX, withScaledAlpha(color, 0.28D));
        builder.capsule(a, b, height, color);
        builder.submit(graphics);
    }

    public static void diamond(GuiGraphicsExtractor graphics, Vec2 center, double radius, int color) {
        if (center == null || alpha(color) <= 0 || radius <= 0.0D) {
            return;
        }
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.diamond(center, radius + EDGE_AA_PX, withScaledAlpha(color, 0.24D));
        builder.diamond(center, radius, color);
        builder.submit(graphics);
    }

    public static void quad(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, Vec2 c, Vec2 d, int color) {
        quad(graphics, a, b, c, d, color, color, color, color);
    }

    public static void quad(GuiGraphicsExtractor graphics, Vec2 a, Vec2 b, Vec2 c, Vec2 d, int c1, int c2, int c3, int c4) {
        if (a == null || b == null || c == null || d == null || alpha(c1 | c2 | c3 | c4) <= 0) {
            return;
        }
        PrimitiveBuilder builder = new PrimitiveBuilder();
        builder.quad(a.x(), a.y(), c1, b.x(), b.y(), c2, c.x(), c.y(), c3, d.x(), d.y(), c4);
        builder.submit(graphics);
    }

    public static void quads(GuiGraphicsExtractor graphics, List<GradientQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return;
        }
        PrimitiveBuilder builder = new PrimitiveBuilder();
        for (GradientQuad quad : quads) {
            if (quad == null || quad.a() == null || quad.b() == null || quad.c() == null || quad.d() == null || alpha(quad.c1() | quad.c2() | quad.c3() | quad.c4()) <= 0) {
                continue;
            }
            builder.quad(
                    quad.a().x(), quad.a().y(), quad.c1(),
                    quad.b().x(), quad.b().y(), quad.c2(),
                    quad.c().x(), quad.c().y(), quad.c3(),
                    quad.d().x(), quad.d().y(), quad.c4()
            );
        }
        builder.submit(graphics);
    }

    public static void texturedQuad(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite, Vec2 a, Vec2 b, Vec2 c, Vec2 d, int color, float u0, float u1, float v0, float v1) {
        texturedQuad(graphics, RenderPipelines.GUI_TEXTURED, sprite, a, b, c, d, color, u0, u1, v0, v1);
    }

    public static void texturedQuad(GuiGraphicsExtractor graphics, RenderPipeline pipeline, TextureAtlasSprite sprite, Vec2 a, Vec2 b, Vec2 c, Vec2 d, int color, float u0, float u1, float v0, float v1) {
        if (sprite == null || a == null || b == null || c == null || d == null || alpha(color) <= 0) {
            return;
        }
        texturedQuad(graphics, pipeline, sprite.atlasLocation(), a, b, c, d, color, u0, u1, v0, v1);
    }

    public static void texturedQuad(GuiGraphicsExtractor graphics, Identifier textureId, Vec2 a, Vec2 b, Vec2 c, Vec2 d, int color, float u0, float u1, float v0, float v1) {
        texturedQuad(graphics, RenderPipelines.GUI_TEXTURED, textureId, a, b, c, d, color, u0, u1, v0, v1);
    }

    public static void texturedQuad(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier textureId, Vec2 a, Vec2 b, Vec2 c, Vec2 d, int color, float u0, float u1, float v0, float v1) {
        if (textureId == null || a == null || b == null || c == null || d == null || alpha(color) <= 0) {
            return;
        }
        double minX = Math.min(Math.min(a.x(), b.x()), Math.min(c.x(), d.x()));
        double minY = Math.min(Math.min(a.y(), b.y()), Math.min(c.y(), d.y()));
        double maxX = Math.max(Math.max(a.x(), b.x()), Math.max(c.x(), d.x()));
        double maxY = Math.max(Math.max(a.y(), b.y()), Math.max(c.y(), d.y()));
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle localBounds = new ScreenRectangle((int) Math.floor(minX - 2.0D), (int) Math.floor(minY - 2.0D), Math.max(1, (int) Math.ceil(maxX - minX + 4.0D)), Math.max(1, (int) Math.ceil(maxY - minY + 4.0D)));
        ScreenRectangle transformed = localBounds.transformMaxBounds(pose);
        ScreenRectangle scissor = graphics.peekScissorStack();
        ScreenRectangle bounds = scissor == null ? transformed : scissor.intersection(transformed);
        if (bounds == null) {
            return;
        }
        TexturedQuad quad = signedArea(a.x(), a.y(), b.x(), b.y(), c.x(), c.y(), d.x(), d.y()) > 0.0D
                ? new TexturedQuad(d.x(), d.y(), u0, v1, c.x(), c.y(), u1, v1, b.x(), b.y(), u1, v0, a.x(), a.y(), u0, v0, color)
                : new TexturedQuad(a.x(), a.y(), u0, v0, b.x(), b.y(), u1, v0, c.x(), c.y(), u1, v1, d.x(), d.y(), u0, v1, color);
        AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(textureId);
        graphics.submitGuiElementRenderState(new SmoothTexturedQuadRenderState(
                pipeline,
                TextureSetup.singleTexture(atlas.getTextureView(), atlas.getSampler()),
                pose,
                quad,
                scissor,
                bounds
        ));
    }

    private static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    private static int withScaledAlpha(int color, double scale) {
        int scaled = Math.max(0, Math.min(255, (int) Math.round(alpha(color) * scale)));
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    private static double signedArea(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        return x1 * y2 - x2 * y1
                + x2 * y3 - x3 * y2
                + x3 * y4 - x4 * y3
                + x4 * y1 - x1 * y4;
    }

    private static List<Vec2> cleanPolyline(List<Vec2> points) {
        if (points.size() < 3) {
            return points;
        }
        List<Vec2> deduped = new ArrayList<>();
        for (Vec2 point : points) {
            if (deduped.isEmpty() || deduped.getLast().distanceTo(point) > 0.20D) {
                deduped.add(point);
            }
        }
        if (deduped.size() < 3) {
            return deduped;
        }
        List<Vec2> result = new ArrayList<>();
        result.add(deduped.getFirst());
        for (int i = 1; i + 1 < deduped.size(); i++) {
            Vec2 previous = result.getLast();
            Vec2 current = deduped.get(i);
            Vec2 next = deduped.get(i + 1);
            double ax = current.x() - previous.x();
            double ay = current.y() - previous.y();
            double bx = next.x() - current.x();
            double by = next.y() - current.y();
            double al = Math.hypot(ax, ay);
            double bl = Math.hypot(bx, by);
            if (al <= 0.20D || bl <= 0.20D) {
                continue;
            }
            double cross = Math.abs(ax * by - ay * bx) / (al * bl);
            double dot = (ax * bx + ay * by) / (al * bl);
            if (cross <= 0.0025D && dot >= 0.995D) {
                continue;
            }
            result.add(current);
        }
        result.add(deduped.getLast());
        return result;
    }

    private static List<Vec2> roundedPolyline(List<Vec2> points, double width) {
        if (points.size() < 3) {
            return points;
        }
        double preferredRadius = Math.max(4.0D, Math.min(18.0D, width * 3.2D));
        List<Vec2> result = new ArrayList<>();
        result.add(points.getFirst());
        for (int i = 1; i + 1 < points.size(); i++) {
            Vec2 previous = points.get(i - 1);
            Vec2 corner = points.get(i);
            Vec2 next = points.get(i + 1);
            double prevLength = previous.distanceTo(corner);
            double nextLength = corner.distanceTo(next);
            if (prevLength <= 0.8D || nextLength <= 0.8D) {
                addPoint(result, corner);
                continue;
            }

            double inX = (corner.x() - previous.x()) / prevLength;
            double inY = (corner.y() - previous.y()) / prevLength;
            double outX = (next.x() - corner.x()) / nextLength;
            double outY = (next.y() - corner.y()) / nextLength;
            double dot = inX * outX + inY * outY;
            if (Math.abs(dot) >= 0.985D) {
                addPoint(result, corner);
                continue;
            }

            double trim = Math.min(preferredRadius, Math.min(prevLength, nextLength) * 0.42D);
            if (trim <= 1.5D) {
                addPoint(result, corner);
                continue;
            }
            Vec2 curveStart = new Vec2(corner.x() - inX * trim, corner.y() - inY * trim);
            Vec2 curveEnd = new Vec2(corner.x() + outX * trim, corner.y() + outY * trim);
            addPoint(result, curveStart);
            int samples = Math.max(3, Math.min(10, (int) Math.ceil(trim / 3.0D)));
            for (int sample = 1; sample <= samples; sample++) {
                double t = sample / (double) samples;
                double omt = 1.0D - t;
                addPoint(result, new Vec2(
                        omt * omt * curveStart.x() + 2.0D * omt * t * corner.x() + t * t * curveEnd.x(),
                        omt * omt * curveStart.y() + 2.0D * omt * t * corner.y() + t * t * curveEnd.y()
                ));
            }
        }
        addPoint(result, points.getLast());
        return result;
    }

    private static void addPoint(List<Vec2> points, Vec2 point) {
        if (points.isEmpty() || points.getLast().distanceTo(point) > 0.25D) {
            points.add(point);
        }
    }

    private record Quad(double x1, double y1, int c1, double x2, double y2, int c2, double x3, double y3, int c3, double x4, double y4, int c4) {
    }

    public record GradientQuad(Vec2 a, Vec2 b, Vec2 c, Vec2 d, int c1, int c2, int c3, int c4) {
    }

    private record TexturedQuad(double x1, double y1, float u1, float v1, double x2, double y2, float u2, float v2, double x3, double y3, float u3, float v3, double x4, double y4, float u4, float v4, int color) {
    }

    private static final class PrimitiveBuilder {
        private final List<Quad> quads = new ArrayList<>();
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        void capsule(Vec2 a, Vec2 b, double width, int color) {
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double length = Math.hypot(dx, dy);
            if (length <= 0.001D) {
                this.circle(a, width * 0.5D, color);
                return;
            }
            double radius = width * 0.5D;
            double nx = -dy / length * radius;
            double ny = dx / length * radius;
            this.quad(
                    a.x() + nx, a.y() + ny, color,
                    b.x() + nx, b.y() + ny, color,
                    b.x() - nx, b.y() - ny, color,
                    a.x() - nx, a.y() - ny, color
            );
            this.circle(a, radius, color);
            this.circle(b, radius, color);
        }

        void stroke(List<Vec2> points, double width, int color, boolean startCap, boolean endCap) {
            if (points.size() < 2 || width <= 0.0D || alpha(color) <= 0) {
                return;
            }
            double radius = width * 0.5D;
            boolean drewSegment = false;
            for (int i = 0; i + 1 < points.size(); i++) {
                Vec2 a = points.get(i);
                Vec2 b = points.get(i + 1);
                double dx = b.x() - a.x();
                double dy = b.y() - a.y();
                double length = Math.hypot(dx, dy);
                if (length <= 0.001D) {
                    continue;
                }
                double nx = -dy / length * radius;
                double ny = dx / length * radius;
                this.quad(
                        a.x() + nx, a.y() + ny, color,
                        b.x() + nx, b.y() + ny, color,
                        b.x() - nx, b.y() - ny, color,
                        a.x() - nx, a.y() - ny, color
                );
                drewSegment = true;
            }
            if (!drewSegment) {
                if (startCap || endCap) {
                    this.circle(points.getFirst(), radius, color);
                }
                return;
            }
            for (int i = 1; i + 1 < points.size(); i++) {
                this.circle(points.get(i), radius, color);
            }
            if (startCap) {
                this.circle(points.getFirst(), radius, color);
            }
            if (endCap) {
                this.circle(points.getLast(), radius, color);
            }
        }

        void capsuleShape(Vec2 center, double width, double height, int color) {
            if (width <= height) {
                this.circle(center, Math.max(width, height) * 0.5D, color);
                return;
            }
            double radius = height * 0.5D;
            double left = center.x() - width * 0.5D + radius;
            double right = center.x() + width * 0.5D - radius;
            this.quad(left, center.y() - radius, color, right, center.y() - radius, color, right, center.y() + radius, color, left, center.y() + radius, color);
            this.circle(new Vec2(left, center.y()), radius, color);
            this.circle(new Vec2(right, center.y()), radius, color);
        }

        void circle(Vec2 center, double radius, int color) {
            int slices = Math.max(12, Math.min(96, (int) Math.ceil(radius * 4.0D)));
            double diameter = radius * 2.0D;
            for (int i = 0; i < slices; i++) {
                double x0 = -radius + diameter * i / slices;
                double x1 = -radius + diameter * (i + 1) / slices;
                double y0 = Math.sqrt(Math.max(0.0D, radius * radius - x0 * x0));
                double y1 = Math.sqrt(Math.max(0.0D, radius * radius - x1 * x1));
                this.quad(
                        center.x() + x0, center.y() - y0, color,
                        center.x() + x1, center.y() - y1, color,
                        center.x() + x1, center.y() + y1, color,
                        center.x() + x0, center.y() + y0, color
                );
            }
        }

        void ring(Vec2 center, double outerRadius, double innerRadius, int color) {
            if (outerRadius <= 0.0D || innerRadius >= outerRadius) {
                return;
            }
            if (innerRadius <= 0.001D) {
                this.circle(center, outerRadius, color);
                return;
            }
            int slices = Math.max(32, Math.min(160, (int) Math.ceil(outerRadius * 5.5D)));
            for (int i = 0; i < slices; i++) {
                double a0 = Math.PI * 2.0D * i / slices;
                double a1 = Math.PI * 2.0D * (i + 1) / slices;
                double outerX0 = center.x() + Math.cos(a0) * outerRadius;
                double outerY0 = center.y() + Math.sin(a0) * outerRadius;
                double outerX1 = center.x() + Math.cos(a1) * outerRadius;
                double outerY1 = center.y() + Math.sin(a1) * outerRadius;
                double innerX1 = center.x() + Math.cos(a1) * innerRadius;
                double innerY1 = center.y() + Math.sin(a1) * innerRadius;
                double innerX0 = center.x() + Math.cos(a0) * innerRadius;
                double innerY0 = center.y() + Math.sin(a0) * innerRadius;
                this.quad(outerX0, outerY0, color, outerX1, outerY1, color, innerX1, innerY1, color, innerX0, innerY0, color);
            }
        }

        void diamond(Vec2 center, double radius, int color) {
            int slices = Math.max(4, (int) Math.ceil(radius * 2.0D));
            double diameter = radius * 2.0D;
            for (int i = 0; i < slices; i++) {
                double y0 = -radius + diameter * i / slices;
                double y1 = -radius + diameter * (i + 1) / slices;
                double span0 = Math.max(0.0D, radius - Math.abs(y0));
                double span1 = Math.max(0.0D, radius - Math.abs(y1));
                this.quad(
                        center.x() - span0, center.y() + y0, color,
                        center.x() + span0, center.y() + y0, color,
                        center.x() + span1, center.y() + y1, color,
                        center.x() - span1, center.y() + y1, color
                );
            }
        }

        void quad(double x1, double y1, int c1, double x2, double y2, int c2, double x3, double y3, int c3, double x4, double y4, int c4) {
            if (SmoothGuiPrimitives.signedArea(x1, y1, x2, y2, x3, y3, x4, y4) > 0.0D) {
                this.quads.add(new Quad(x4, y4, c4, x3, y3, c3, x2, y2, c2, x1, y1, c1));
            } else {
                this.quads.add(new Quad(x1, y1, c1, x2, y2, c2, x3, y3, c3, x4, y4, c4));
            }
            this.include(x1, y1);
            this.include(x2, y2);
            this.include(x3, y3);
            this.include(x4, y4);
        }

        void include(double x, double y) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
        }

        void submit(GuiGraphicsExtractor graphics) {
            if (this.quads.isEmpty()) {
                return;
            }
            Matrix3x2f pose = new Matrix3x2f(graphics.pose());
            ScreenRectangle localBounds = new ScreenRectangle((int) Math.floor(this.minX - 2.0D), (int) Math.floor(this.minY - 2.0D), Math.max(1, (int) Math.ceil(this.maxX - this.minX + 4.0D)), Math.max(1, (int) Math.ceil(this.maxY - this.minY + 4.0D)));
            ScreenRectangle transformed = localBounds.transformMaxBounds(pose);
            ScreenRectangle scissor = graphics.peekScissorStack();
            ScreenRectangle bounds = scissor == null ? transformed : scissor.intersection(transformed);
            if (bounds == null) {
                return;
            }
            graphics.submitGuiElementRenderState(new SmoothPrimitiveRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), pose, List.copyOf(this.quads), scissor, bounds));
        }
    }

    private record SmoothPrimitiveRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            List<Quad> quads,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            for (Quad quad : this.quads) {
                vertexConsumer.addVertexWith2DPose(this.pose, (float) quad.x1(), (float) quad.y1()).setColor(quad.c1());
                vertexConsumer.addVertexWith2DPose(this.pose, (float) quad.x2(), (float) quad.y2()).setColor(quad.c2());
                vertexConsumer.addVertexWith2DPose(this.pose, (float) quad.x3(), (float) quad.y3()).setColor(quad.c3());
                vertexConsumer.addVertexWith2DPose(this.pose, (float) quad.x4(), (float) quad.y4()).setColor(quad.c4());
            }
        }
    }

    private record SmoothTexturedQuadRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            TexturedQuad quad,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            vertexConsumer.addVertexWith2DPose(this.pose, (float) this.quad.x1(), (float) this.quad.y1()).setUv(this.quad.u1(), this.quad.v1()).setColor(this.quad.color());
            vertexConsumer.addVertexWith2DPose(this.pose, (float) this.quad.x2(), (float) this.quad.y2()).setUv(this.quad.u2(), this.quad.v2()).setColor(this.quad.color());
            vertexConsumer.addVertexWith2DPose(this.pose, (float) this.quad.x3(), (float) this.quad.y3()).setUv(this.quad.u3(), this.quad.v3()).setColor(this.quad.color());
            vertexConsumer.addVertexWith2DPose(this.pose, (float) this.quad.x4(), (float) this.quad.y4()).setUv(this.quad.u4(), this.quad.v4()).setColor(this.quad.color());
        }
    }
}
