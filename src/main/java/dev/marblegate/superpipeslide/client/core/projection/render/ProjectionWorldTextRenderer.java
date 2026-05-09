package dev.marblegate.superpipeslide.client.core.projection.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProjectionWorldTextRenderer {
    private static final Matrix4f IDENTITY_POSE = new Matrix4f();
    private static final int MAX_PREPARED_TEXT_ENTRIES = 4096;
    private static final Map<PreparedTextKey, PreparedTextBatch> PREPARED_TEXT = new LinkedHashMap<>(256, 0.75F, true);

    private ProjectionWorldTextRenderer() {
    }

    public static void clear() {
        synchronized (PREPARED_TEXT) {
            PREPARED_TEXT.clear();
        }
    }

    public static void drawClipped(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text,
            float x, float topY, float scale, int color, boolean shadow, float clipMinX, float clipMaxX) {
        drawInternal(poseStack, collector, font, text, x, topY, scale, color, shadow, true, clipMinX, clipMaxX, false, null, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public static void drawCanvasClipped(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text,
            float x, float topY, float scale, int color, boolean shadow, Matrix4fc worldToCanvas, float canvasMinX, float canvasMinY, float canvasMaxX, float canvasMaxY) {
        drawInternal(poseStack, collector, font, text, x, topY, scale, color, shadow, false, 0.0F, 0.0F, true, worldToCanvas, canvasMinX, canvasMinY, canvasMaxX, canvasMaxY);
    }

    public static void drawClippedToCanvas(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text,
            float x, float topY, float scale, int color, boolean shadow, float clipMinX, float clipMaxX, Matrix4fc worldToCanvas, float canvasMinX, float canvasMinY, float canvasMaxX, float canvasMaxY) {
        drawInternal(poseStack, collector, font, text, x, topY, scale, color, shadow, true, clipMinX, clipMaxX, true, worldToCanvas, canvasMinX, canvasMinY, canvasMaxX, canvasMaxY);
    }

    private static void drawInternal(PoseStack poseStack, SubmitNodeCollector collector, Font font, String text,
            float x, float topY, float scale, int color, boolean shadow, boolean localClip, float clipMinX, float clipMaxX,
            boolean canvasClip, Matrix4fc worldToCanvas, float canvasMinX, float canvasMinY, float canvasMaxX, float canvasMaxY) {
        String value = text == null ? "" : text;
        if (value.isEmpty() || scale <= 0.0F || (localClip && clipMaxX <= clipMinX) || (canvasClip && (worldToCanvas == null || canvasMaxX <= canvasMinX || canvasMaxY <= canvasMinY))) {
            return;
        }

        float minX = localClip ? (clipMinX - x) / scale : 0.0F;
        float maxX = localClip ? (clipMaxX - x) / scale : 0.0F;
        PreparedTextBatch prepared = prepare(font, value, color, shadow);
        if (prepared.empty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(x, topY, 0.0F);
        poseStack.scale(scale, -scale, scale);
        for (Map.Entry<RenderType, List<TextRenderable>> entry : prepared.byType().entrySet()) {
            List<TextRenderable> typedRenderables = entry.getValue();
            collector.submitCustomGeometry(poseStack, entry.getKey(), (pose, buffer) -> {
                ClippedTextVertexConsumer clipped = new ClippedTextVertexConsumer(buffer, pose.pose(), localClip, minX, maxX, canvasClip, worldToCanvas, canvasMinX, canvasMinY, canvasMaxX, canvasMaxY);
                for (TextRenderable renderable : typedRenderables) {
                    renderable.render(IDENTITY_POSE, clipped, LightCoordsUtil.FULL_BRIGHT, false);
                }
            });
        }
        poseStack.popPose();
    }

    private static PreparedTextBatch prepare(Font font, String text, int color, boolean shadow) {
        PreparedTextKey key = new PreparedTextKey(System.identityHashCode(font), text, color, shadow);
        synchronized (PREPARED_TEXT) {
            PreparedTextBatch cached = PREPARED_TEXT.get(key);
            if (cached != null) {
                return cached;
            }
        }
        List<TextRenderable> renderables = new ArrayList<>();
        Font.PreparedText prepared = font.prepareText(Component.literal(text).getVisualOrderText(), 0.0F, 0.0F, color, shadow, false, 0);
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled glyph) {
                renderables.add(glyph);
            }

            @Override
            public void acceptEffect(TextRenderable effect) {
                renderables.add(effect);
            }
        });
        Map<RenderType, List<TextRenderable>> byType = new LinkedHashMap<>();
        for (TextRenderable renderable : renderables) {
            byType.computeIfAbsent(renderable.renderType(Font.DisplayMode.NORMAL, false), ignored -> new ArrayList<>()).add(renderable);
        }
        Map<RenderType, List<TextRenderable>> immutable = new LinkedHashMap<>();
        for (Map.Entry<RenderType, List<TextRenderable>> entry : byType.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        PreparedTextBatch batch = new PreparedTextBatch(java.util.Collections.unmodifiableMap(immutable));
        synchronized (PREPARED_TEXT) {
            PREPARED_TEXT.put(key, batch);
            trimPreparedTextLocked();
        }
        return batch;
    }

    private static void trimPreparedTextLocked() {
        while (PREPARED_TEXT.size() > MAX_PREPARED_TEXT_ENTRIES) {
            var iterator = PREPARED_TEXT.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static final class ClippedTextVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Matrix4fc transform;
        private final boolean localClip;
        private final float minX;
        private final float maxX;
        private final boolean canvasClip;
        private final Matrix4fc worldToCanvas;
        private final float canvasMinX;
        private final float canvasMinY;
        private final float canvasMaxX;
        private final float canvasMaxY;
        private final GlyphVertex[] quad = new GlyphVertex[] {
                new GlyphVertex(), new GlyphVertex(), new GlyphVertex(), new GlyphVertex()
        };
        private final Vector3f transformed = new Vector3f();
        private final Vector3f canvasPoint = new Vector3f();
        private GlyphVertex current;
        private int vertexCount;

        private ClippedTextVertexConsumer(VertexConsumer delegate, Matrix4fc transform, boolean localClip, float minX, float maxX,
                boolean canvasClip, Matrix4fc worldToCanvas, float canvasMinX, float canvasMinY, float canvasMaxX, float canvasMaxY) {
            this.delegate = delegate;
            this.transform = transform;
            this.localClip = localClip;
            this.minX = minX;
            this.maxX = maxX;
            this.canvasClip = canvasClip;
            this.worldToCanvas = worldToCanvas;
            this.canvasMinX = canvasMinX;
            this.canvasMinY = canvasMinY;
            this.canvasMaxX = canvasMaxX;
            this.canvasMaxY = canvasMaxY;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.current = this.quad[this.vertexCount % 4];
            this.current.x = x;
            this.current.y = y;
            this.current.z = z;
            this.current.clipX = x;
            this.current.clipY = y;
            this.current.color = 0xFFFFFFFF;
            this.current.u = 0.0F;
            this.current.v = 0.0F;
            this.current.light = LightCoordsUtil.FULL_BRIGHT;
            return this;
        }

        @Override
        public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
            return this.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (this.current != null) {
                this.current.color = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (this.current != null) {
                this.current.color = color;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (this.current != null) {
                this.current.u = u;
                this.current.v = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (this.current != null) {
                this.current.light = u & 0xFFFF | (v & 0xFFFF) << 16;
                this.finishVertex();
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        private void finishVertex() {
            this.vertexCount++;
            if (this.vertexCount % 4 == 0) {
                this.flushQuad();
            }
            this.current = null;
        }

        private void flushQuad() {
            if (!this.localClip) {
                List<GlyphVertex> transformedVertices = new ArrayList<>(4);
                boolean canvasInside = true;
                for (GlyphVertex vertex : this.quad) {
                    GlyphVertex transformedVertex = this.transform(vertex);
                    transformedVertices.add(transformedVertex);
                    if (this.canvasClip && (transformedVertex.clipX < this.canvasMinX
                            || transformedVertex.clipX > this.canvasMaxX
                            || transformedVertex.clipY < this.canvasMinY
                            || transformedVertex.clipY > this.canvasMaxY)) {
                        canvasInside = false;
                    }
                }
                if (!this.canvasClip || canvasInside) {
                    this.emitPolygon(transformedVertices);
                    return;
                }
                transformedVertices = clipTop(clipBottom(clipRight(clipLeft(transformedVertices, this.canvasMinX), this.canvasMaxX), this.canvasMinY), this.canvasMaxY);
                if (transformedVertices.size() >= 3) {
                    this.emitPolygon(transformedVertices);
                }
                return;
            }
            List<GlyphVertex> vertices = new ArrayList<>(4);
            for (GlyphVertex vertex : this.quad) {
                vertices.add(vertex.copy());
            }
            if (this.localClip) {
                vertices = clipRight(clipLeft(vertices, this.minX), this.maxX);
                if (vertices.size() < 3) {
                    return;
                }
            }
            List<GlyphVertex> transformedVertices = new ArrayList<>(vertices.size());
            for (GlyphVertex vertex : vertices) {
                transformedVertices.add(this.transform(vertex));
            }
            if (this.canvasClip) {
                transformedVertices = clipTop(clipBottom(clipRight(clipLeft(transformedVertices, this.canvasMinX), this.canvasMaxX), this.canvasMinY), this.canvasMaxY);
                if (transformedVertices.size() < 3) {
                    return;
                }
            }
            this.emitPolygon(transformedVertices);
        }

        private GlyphVertex transform(GlyphVertex vertex) {
            this.transform.transformPosition(vertex.x, vertex.y, vertex.z, this.transformed);
            if (this.canvasClip) {
                this.worldToCanvas.transformPosition(this.transformed.x(), this.transformed.y(), this.transformed.z(), this.canvasPoint);
                return vertex.withPositionAndClip(this.transformed.x(), this.transformed.y(), this.transformed.z(), this.canvasPoint.x(), this.canvasPoint.y());
            }
            return vertex.withPositionAndClip(this.transformed.x(), this.transformed.y(), this.transformed.z(), this.transformed.x(), this.transformed.y());
        }

        private void emitPolygon(List<GlyphVertex> vertices) {
            if (vertices.size() < 3) {
                return;
            }
            if (vertices.size() == 4) {
                for (GlyphVertex vertex : vertices) {
                    this.emit(vertex);
                }
                return;
            }
            GlyphVertex first = vertices.getFirst();
            for (int i = 1; i < vertices.size() - 1; i++) {
                this.emit(first);
                this.emit(vertices.get(i));
                this.emit(vertices.get(i + 1));
                this.emit(first);
            }
        }

        private void emit(GlyphVertex vertex) {
            this.delegate.addVertex(vertex.x, vertex.y, vertex.z)
                    .setColor(vertex.color)
                    .setUv(vertex.u, vertex.v)
                    .setLight(vertex.light);
        }
    }

    private static List<GlyphVertex> clipLeft(List<GlyphVertex> input, float minX) {
        return clip(input, vertex -> vertex.clipX >= minX, (from, to) -> interpolateForX(from, to, minX));
    }

    private static List<GlyphVertex> clipRight(List<GlyphVertex> input, float maxX) {
        return clip(input, vertex -> vertex.clipX <= maxX, (from, to) -> interpolateForX(from, to, maxX));
    }

    private static List<GlyphVertex> clipBottom(List<GlyphVertex> input, float minY) {
        return clip(input, vertex -> vertex.clipY >= minY, (from, to) -> interpolateForY(from, to, minY));
    }

    private static List<GlyphVertex> clipTop(List<GlyphVertex> input, float maxY) {
        return clip(input, vertex -> vertex.clipY <= maxY, (from, to) -> interpolateForY(from, to, maxY));
    }

    private static List<GlyphVertex> clip(List<GlyphVertex> input, Inside inside, Intersector intersector) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<GlyphVertex> output = new ArrayList<>(input.size() + 2);
        GlyphVertex previous = input.getLast();
        boolean previousInside = inside.test(previous);
        for (GlyphVertex current : input) {
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

    private static GlyphVertex interpolateForX(GlyphVertex from, GlyphVertex to, float targetX) {
        GlyphVertex result = interpolate(from, to, ratio(from.clipX, to.clipX, targetX));
        result.clipX = targetX;
        return result;
    }

    private static GlyphVertex interpolateForY(GlyphVertex from, GlyphVertex to, float targetY) {
        GlyphVertex result = interpolate(from, to, ratio(from.clipY, to.clipY, targetY));
        result.clipY = targetY;
        return result;
    }

    private static GlyphVertex interpolate(GlyphVertex from, GlyphVertex to, float ratio) {
        GlyphVertex result = new GlyphVertex();
        result.x = lerp(from.x, to.x, ratio);
        result.y = lerp(from.y, to.y, ratio);
        result.z = lerp(from.z, to.z, ratio);
        result.clipX = lerp(from.clipX, to.clipX, ratio);
        result.clipY = lerp(from.clipY, to.clipY, ratio);
        result.u = lerp(from.u, to.u, ratio);
        result.v = lerp(from.v, to.v, ratio);
        result.color = from.color;
        result.light = from.light;
        return result;
    }

    private static float lerp(float from, float to, float ratio) {
        return from + (to - from) * ratio;
    }

    private interface Inside {
        boolean test(GlyphVertex vertex);
    }

    private interface Intersector {
        GlyphVertex intersect(GlyphVertex from, GlyphVertex to);
    }

    private record PreparedTextKey(int fontIdentity, String text, int color, boolean shadow) {
        private PreparedTextKey {
            text = Objects.requireNonNullElse(text, "");
        }
    }

    private record PreparedTextBatch(Map<RenderType, List<TextRenderable>> byType) {
        private boolean empty() {
            return this.byType.isEmpty();
        }
    }

    private static final class GlyphVertex {
        private float x;
        private float y;
        private float z;
        private float clipX;
        private float clipY;
        private int color;
        private float u;
        private float v;
        private int light;

        private GlyphVertex copy() {
            GlyphVertex copy = new GlyphVertex();
            copy.x = this.x;
            copy.y = this.y;
            copy.z = this.z;
            copy.clipX = this.clipX;
            copy.clipY = this.clipY;
            copy.color = this.color;
            copy.u = this.u;
            copy.v = this.v;
            copy.light = this.light;
            return copy;
        }

        private GlyphVertex withPositionAndClip(float x, float y, float z, float clipX, float clipY) {
            GlyphVertex copy = this.copy();
            copy.x = x;
            copy.y = y;
            copy.z = z;
            copy.clipX = clipX;
            copy.clipY = clipY;
            return copy;
        }
    }
}
