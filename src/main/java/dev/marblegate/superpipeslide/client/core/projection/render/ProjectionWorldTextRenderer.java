package dev.marblegate.superpipeslide.client.core.projection.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marblegate.superpipeslide.client.renderer.ClientRenderCompatibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class ProjectionWorldTextRenderer {
    private static final Matrix4f IDENTITY_POSE = new Matrix4f();
    private static final GlyphVertex[] EMPTY_GLYPH_VERTICES = new GlyphVertex[0];
    private static final int MAX_PREPARED_TEXT_ENTRIES = 4096;
    private static final Map<PreparedTextKey, PreparedTextBatch> PREPARED_TEXT = new LinkedHashMap<>(256, 0.75F, true);
    private static final ThreadLocal<TextRenderScratch> TEXT_RENDER_SCRATCH = ThreadLocal.withInitial(TextRenderScratch::new);
    private static final ThreadLocal<PreparedTextLookupKey> PREPARED_TEXT_LOOKUP = ThreadLocal.withInitial(PreparedTextLookupKey::new);

    private ProjectionWorldTextRenderer() {}

    public static void clear() {
        synchronized (PREPARED_TEXT) {
            PREPARED_TEXT.clear();
        }
        TEXT_RENDER_SCRATCH.remove();
        PREPARED_TEXT_LOOKUP.remove();
        ClientRenderCompatibility.clearCaches();
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
        PreparedTextLayer[] layers = prepared.layers();
        for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
            PreparedTextLayer layer = layers[layerIndex];
            TextGeometryRenderer renderer = TEXT_RENDER_SCRATCH.get().renderer;
            renderer.setup(layer.vertices(), localClip, minX, maxX, canvasClip, worldToCanvas, canvasMinX, canvasMinY, canvasMaxX, canvasMaxY);
            try {
                ClientRenderCompatibility.submitCustomGeometry(collector, poseStack, ClientRenderCompatibility.text(layer.renderType()), renderer);
            } finally {
                renderer.reset();
            }
        }
        poseStack.popPose();
    }

    private static PreparedTextBatch prepare(Font font, String text, int color, boolean shadow) {
        int fontIdentity = System.identityHashCode(font);
        PreparedTextLookupKey lookupKey = PREPARED_TEXT_LOOKUP.get().set(fontIdentity, text, color, shadow);
        try {
            synchronized (PREPARED_TEXT) {
                PreparedTextBatch cached = PREPARED_TEXT.get(lookupKey);
                if (cached != null) {
                    return cached;
                }
            }
            TextCaptureBufferSource bufferSource = new TextCaptureBufferSource();
            font.drawInBatch(Component.literal(text).getVisualOrderText(), 0.0F, 0.0F, color, shadow, IDENTITY_POSE, bufferSource, Font.DisplayMode.NORMAL, 0, LightCoordsUtil.FULL_BRIGHT);
            Map<RenderType, List<GlyphVertex>> byType = bufferSource.verticesByType();
            PreparedTextLayer[] layers = new PreparedTextLayer[byType.size()];
            int layerIndex = 0;
            for (Map.Entry<RenderType, List<GlyphVertex>> entry : byType.entrySet()) {
                layers[layerIndex] = new PreparedTextLayer(entry.getKey(), entry.getValue().toArray(GlyphVertex[]::new));
                layerIndex++;
            }
            PreparedTextBatch batch = new PreparedTextBatch(layers);
            synchronized (PREPARED_TEXT) {
                PreparedTextBatch cached = PREPARED_TEXT.get(lookupKey);
                if (cached != null) {
                    return cached;
                }
                PREPARED_TEXT.put(new PreparedTextKey(fontIdentity, text, color, shadow), batch);
                trimPreparedTextLocked();
            }
            return batch;
        } finally {
            lookupKey.clear();
        }
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

    private static final class TextCaptureBufferSource implements MultiBufferSource {
        private final Map<RenderType, CapturingTextVertexConsumer> buffers = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this.buffers.computeIfAbsent(renderType, ignored -> new CapturingTextVertexConsumer());
        }

        private Map<RenderType, List<GlyphVertex>> verticesByType() {
            Map<RenderType, List<GlyphVertex>> result = new LinkedHashMap<>();
            for (Map.Entry<RenderType, CapturingTextVertexConsumer> entry : this.buffers.entrySet()) {
                if (!entry.getValue().vertices().isEmpty()) {
                    result.put(entry.getKey(), entry.getValue().vertices());
                }
            }
            return result;
        }
    }

    private static class TextVertexConsumerBase implements VertexConsumer {
        private final Vector3f transformedPosition = new Vector3f();
        protected GlyphVertex current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.current = new GlyphVertex();
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
            this.addVertex(x, y, z);
            if (this.current != null) {
                pose.transformPosition(x, y, z, this.transformedPosition);
                this.current.x = this.transformedPosition.x();
                this.current.y = this.transformedPosition.y();
                this.current.z = this.transformedPosition.z();
                this.current.clipX = this.transformedPosition.x();
                this.current.clipY = this.transformedPosition.y();
            }
            return this;
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

        protected void finishVertex() {
            this.current = null;
        }
    }

    private static final class CapturingTextVertexConsumer extends TextVertexConsumerBase {
        private final List<GlyphVertex> vertices = new ArrayList<>();

        private List<GlyphVertex> vertices() {
            return this.vertices;
        }

        @Override
        protected void finishVertex() {
            if (this.current != null) {
                this.vertices.add(this.current);
            }
            super.finishVertex();
        }
    }

    private static final class TextRenderScratch {
        private final ClippedTextVertexConsumer clipped = new ClippedTextVertexConsumer();
        private final TextGeometryRenderer renderer = new TextGeometryRenderer(this.clipped);
    }

    private static final class TextGeometryRenderer implements SubmitNodeCollector.CustomGeometryRenderer {
        private final ClippedTextVertexConsumer clipped;
        private GlyphVertex[] vertices = EMPTY_GLYPH_VERTICES;
        private boolean localClip;
        private float minX;
        private float maxX;
        private boolean canvasClip;
        private Matrix4fc worldToCanvas;
        private float canvasMinX;
        private float canvasMinY;
        private float canvasMaxX;
        private float canvasMaxY;

        private TextGeometryRenderer(ClippedTextVertexConsumer clipped) {
            this.clipped = clipped;
        }

        private void setup(GlyphVertex[] vertices, boolean localClip, float minX, float maxX,
                boolean canvasClip, Matrix4fc worldToCanvas, float canvasMinX, float canvasMinY, float canvasMaxX, float canvasMaxY) {
            this.vertices = vertices;
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

        private void reset() {
            this.vertices = EMPTY_GLYPH_VERTICES;
            this.localClip = false;
            this.minX = 0.0F;
            this.maxX = 0.0F;
            this.canvasClip = false;
            this.worldToCanvas = null;
            this.canvasMinX = 0.0F;
            this.canvasMinY = 0.0F;
            this.canvasMaxX = 0.0F;
            this.canvasMaxY = 0.0F;
        }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer buffer) {
            this.clipped.setup(buffer, pose.pose(), this.localClip, this.minX, this.maxX, this.canvasClip, this.worldToCanvas, this.canvasMinX, this.canvasMinY, this.canvasMaxX, this.canvasMaxY);
            try {
                for (int i = 0; i < this.vertices.length; i++) {
                    this.vertices[i].emitTo(this.clipped);
                }
            } finally {
                this.clipped.reset();
            }
        }
    }

    private static final class ClippedTextVertexConsumer extends TextVertexConsumerBase {
        private VertexConsumer delegate;
        private Matrix4fc transform;
        private boolean localClip;
        private float minX;
        private float maxX;
        private boolean canvasClip;
        private Matrix4fc worldToCanvas;
        private float canvasMinX;
        private float canvasMinY;
        private float canvasMaxX;
        private float canvasMaxY;
        private final GlyphVertex[] quad = newVertices(4);
        private final GlyphVertex[] scratchA = newVertices(16);
        private final GlyphVertex[] scratchB = newVertices(16);
        private final Vector3f transformed = new Vector3f();
        private final Vector3f canvasPoint = new Vector3f();
        private int vertexCount;

        private void setup(VertexConsumer delegate, Matrix4fc transform, boolean localClip, float minX, float maxX,
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
            this.vertexCount = 0;
        }

        private void reset() {
            this.delegate = null;
            this.transform = null;
            this.localClip = false;
            this.minX = 0.0F;
            this.maxX = 0.0F;
            this.canvasClip = false;
            this.worldToCanvas = null;
            this.canvasMinX = 0.0F;
            this.canvasMinY = 0.0F;
            this.canvasMaxX = 0.0F;
            this.canvasMaxY = 0.0F;
            this.current = null;
            this.vertexCount = 0;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.current = this.quad[this.vertexCount % 4];
            this.current.set(x, y, z, x, y, 0xFFFFFFFF, 0.0F, 0.0F, LightCoordsUtil.FULL_BRIGHT);
            return this;
        }

        @Override
        protected void finishVertex() {
            this.vertexCount++;
            if (this.vertexCount % 4 == 0) {
                this.flushQuad();
            }
            super.finishVertex();
        }

        private void flushQuad() {
            if (this.delegate == null || this.transform == null) {
                return;
            }
            if (!this.localClip) {
                int count = this.transformQuad(this.scratchA);
                if (!this.canvasClip || this.allCanvasInside(this.scratchA, count)) {
                    this.emitPolygon(this.scratchA, count);
                    return;
                }
                GlyphVertex[] input = this.scratchA;
                GlyphVertex[] output = this.scratchB;
                count = clipLeft(input, count, output, this.canvasMinX);
                GlyphVertex[] swap = input;
                input = output;
                output = swap;
                count = clipRight(input, count, output, this.canvasMaxX);
                swap = input;
                input = output;
                output = swap;
                count = clipBottom(input, count, output, this.canvasMinY);
                swap = input;
                input = output;
                output = swap;
                count = clipTop(input, count, output, this.canvasMaxY);
                swap = input;
                input = output;
                output = swap;
                if (count >= 3) {
                    this.emitPolygon(input, count);
                }
                return;
            }

            GlyphVertex[] input = this.scratchA;
            GlyphVertex[] output = this.scratchB;
            for (int i = 0; i < 4; i++) {
                input[i].set(this.quad[i]);
            }
            int count = clipLeft(input, 4, output, this.minX);
            GlyphVertex[] swap = input;
            input = output;
            output = swap;
            count = clipRight(input, count, output, this.maxX);
            swap = input;
            input = output;
            output = swap;
            if (count < 3) {
                return;
            }

            output = input == this.scratchA ? this.scratchB : this.scratchA;
            this.transformPolygon(input, count, output);
            input = output;
            if (this.canvasClip) {
                if (this.allCanvasInside(input, count)) {
                    this.emitPolygon(input, count);
                    return;
                }
                output = input == this.scratchA ? this.scratchB : this.scratchA;
                count = clipLeft(input, count, output, this.canvasMinX);
                swap = input;
                input = output;
                output = swap;
                count = clipRight(input, count, output, this.canvasMaxX);
                swap = input;
                input = output;
                output = swap;
                count = clipBottom(input, count, output, this.canvasMinY);
                swap = input;
                input = output;
                output = swap;
                count = clipTop(input, count, output, this.canvasMaxY);
                swap = input;
                input = output;
                output = swap;
                if (count < 3) {
                    return;
                }
            }
            this.emitPolygon(input, count);
        }

        private int transformQuad(GlyphVertex[] output) {
            for (int i = 0; i < 4; i++) {
                this.transformInto(this.quad[i], output[i]);
            }
            return 4;
        }

        private void transformPolygon(GlyphVertex[] input, int count, GlyphVertex[] output) {
            for (int i = 0; i < count; i++) {
                this.transformInto(input[i], output[i]);
            }
        }

        private void transformInto(GlyphVertex source, GlyphVertex target) {
            this.transform.transformPosition(source.x, source.y, source.z, this.transformed);
            if (this.canvasClip) {
                this.worldToCanvas.transformPosition(this.transformed.x(), this.transformed.y(), this.transformed.z(), this.canvasPoint);
                target.setPositionAndClipFrom(source, this.transformed.x(), this.transformed.y(), this.transformed.z(), this.canvasPoint.x(), this.canvasPoint.y());
                return;
            }
            target.setPositionAndClipFrom(source, this.transformed.x(), this.transformed.y(), this.transformed.z(), this.transformed.x(), this.transformed.y());
        }

        private boolean allCanvasInside(GlyphVertex[] vertices, int count) {
            for (int i = 0; i < count; i++) {
                GlyphVertex vertex = vertices[i];
                if (vertex.clipX < this.canvasMinX || vertex.clipX > this.canvasMaxX || vertex.clipY < this.canvasMinY || vertex.clipY > this.canvasMaxY) {
                    return false;
                }
            }
            return true;
        }

        private void emitPolygon(GlyphVertex[] vertices, int count) {
            if (count < 3) {
                return;
            }
            if (count == 4) {
                for (int i = 0; i < 4; i++) {
                    this.emit(vertices[i]);
                }
                return;
            }
            GlyphVertex first = vertices[0];
            for (int i = 1; i < count - 1; i++) {
                this.emit(first);
                this.emit(vertices[i]);
                this.emit(vertices[i + 1]);
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

    private static GlyphVertex[] newVertices(int count) {
        GlyphVertex[] vertices = new GlyphVertex[count];
        for (int i = 0; i < count; i++) {
            vertices[i] = new GlyphVertex();
        }
        return vertices;
    }

    private static int clipLeft(GlyphVertex[] input, int count, GlyphVertex[] output, float minX) {
        if (count <= 0) {
            return 0;
        }
        int out = 0;
        GlyphVertex previous = input[count - 1];
        boolean previousInside = previous.clipX >= minX;
        for (int i = 0; i < count; i++) {
            GlyphVertex current = input[i];
            boolean currentInside = current.clipX >= minX;
            if (currentInside) {
                if (!previousInside) {
                    output[out++].interpolateForXFrom(previous, current, minX);
                }
                output[out++].set(current);
            } else if (previousInside) {
                output[out++].interpolateForXFrom(previous, current, minX);
            }
            previous = current;
            previousInside = currentInside;
        }
        return out;
    }

    private static int clipRight(GlyphVertex[] input, int count, GlyphVertex[] output, float maxX) {
        if (count <= 0) {
            return 0;
        }
        int out = 0;
        GlyphVertex previous = input[count - 1];
        boolean previousInside = previous.clipX <= maxX;
        for (int i = 0; i < count; i++) {
            GlyphVertex current = input[i];
            boolean currentInside = current.clipX <= maxX;
            if (currentInside) {
                if (!previousInside) {
                    output[out++].interpolateForXFrom(previous, current, maxX);
                }
                output[out++].set(current);
            } else if (previousInside) {
                output[out++].interpolateForXFrom(previous, current, maxX);
            }
            previous = current;
            previousInside = currentInside;
        }
        return out;
    }

    private static int clipBottom(GlyphVertex[] input, int count, GlyphVertex[] output, float minY) {
        if (count <= 0) {
            return 0;
        }
        int out = 0;
        GlyphVertex previous = input[count - 1];
        boolean previousInside = previous.clipY >= minY;
        for (int i = 0; i < count; i++) {
            GlyphVertex current = input[i];
            boolean currentInside = current.clipY >= minY;
            if (currentInside) {
                if (!previousInside) {
                    output[out++].interpolateForYFrom(previous, current, minY);
                }
                output[out++].set(current);
            } else if (previousInside) {
                output[out++].interpolateForYFrom(previous, current, minY);
            }
            previous = current;
            previousInside = currentInside;
        }
        return out;
    }

    private static int clipTop(GlyphVertex[] input, int count, GlyphVertex[] output, float maxY) {
        if (count <= 0) {
            return 0;
        }
        int out = 0;
        GlyphVertex previous = input[count - 1];
        boolean previousInside = previous.clipY <= maxY;
        for (int i = 0; i < count; i++) {
            GlyphVertex current = input[i];
            boolean currentInside = current.clipY <= maxY;
            if (currentInside) {
                if (!previousInside) {
                    output[out++].interpolateForYFrom(previous, current, maxY);
                }
                output[out++].set(current);
            } else if (previousInside) {
                output[out++].interpolateForYFrom(previous, current, maxY);
            }
            previous = current;
            previousInside = currentInside;
        }
        return out;
    }

    private static float ratio(float from, float to, float target) {
        float delta = to - from;
        if (Math.abs(delta) <= 0.000001F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (target - from) / delta));
    }

    private static float lerp(float from, float to, float ratio) {
        return from + (to - from) * ratio;
    }

    private static int preparedTextHash(int fontIdentity, String text, int color, boolean shadow) {
        int result = Integer.hashCode(fontIdentity);
        result = 31 * result + text.hashCode();
        result = 31 * result + Integer.hashCode(color);
        result = 31 * result + Boolean.hashCode(shadow);
        return result;
    }

    private static boolean preparedTextEquals(int fontIdentity, String text, int color, boolean shadow, Object other) {
        if (other instanceof PreparedTextKey key) {
            return fontIdentity == key.fontIdentity && color == key.color && shadow == key.shadow && text.equals(key.text);
        }
        if (other instanceof PreparedTextLookupKey key) {
            return fontIdentity == key.fontIdentity && color == key.color && shadow == key.shadow && text.equals(key.text);
        }
        return false;
    }

    private static final class PreparedTextLookupKey {
        private int fontIdentity;
        private String text = "";
        private int color;
        private boolean shadow;
        private int hash;

        private PreparedTextLookupKey set(int fontIdentity, String text, int color, boolean shadow) {
            this.fontIdentity = fontIdentity;
            this.text = Objects.requireNonNullElse(text, "");
            this.color = color;
            this.shadow = shadow;
            this.hash = preparedTextHash(this.fontIdentity, this.text, this.color, this.shadow);
            return this;
        }

        private void clear() {
            this.fontIdentity = 0;
            this.text = "";
            this.color = 0;
            this.shadow = false;
            this.hash = 0;
        }

        @Override
        public boolean equals(Object other) {
            return preparedTextEquals(this.fontIdentity, this.text, this.color, this.shadow, other);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private static final class PreparedTextKey {
        private final int fontIdentity;
        private final String text;
        private final int color;
        private final boolean shadow;
        private final int hash;

        private PreparedTextKey(int fontIdentity, String text, int color, boolean shadow) {
            this.fontIdentity = fontIdentity;
            this.text = Objects.requireNonNullElse(text, "");
            this.color = color;
            this.shadow = shadow;
            this.hash = preparedTextHash(this.fontIdentity, this.text, this.color, this.shadow);
        }

        @Override
        public boolean equals(Object other) {
            return preparedTextEquals(this.fontIdentity, this.text, this.color, this.shadow, other);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private record PreparedTextLayer(RenderType renderType, GlyphVertex[] vertices) {}

    private record PreparedTextBatch(PreparedTextLayer[] layers) {
        private boolean empty() {
            return this.layers.length == 0;
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

        private GlyphVertex set(float x, float y, float z, float clipX, float clipY, int color, float u, float v, int light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.clipX = clipX;
            this.clipY = clipY;
            this.color = color;
            this.u = u;
            this.v = v;
            this.light = light;
            return this;
        }

        private GlyphVertex set(GlyphVertex other) {
            return this.set(other.x, other.y, other.z, other.clipX, other.clipY, other.color, other.u, other.v, other.light);
        }

        private GlyphVertex setPositionAndClipFrom(GlyphVertex source, float x, float y, float z, float clipX, float clipY) {
            return this.set(x, y, z, clipX, clipY, source.color, source.u, source.v, source.light);
        }

        private GlyphVertex interpolateForXFrom(GlyphVertex from, GlyphVertex to, float targetX) {
            this.interpolateFrom(from, to, ratio(from.clipX, to.clipX, targetX));
            this.clipX = targetX;
            return this;
        }

        private GlyphVertex interpolateForYFrom(GlyphVertex from, GlyphVertex to, float targetY) {
            this.interpolateFrom(from, to, ratio(from.clipY, to.clipY, targetY));
            this.clipY = targetY;
            return this;
        }

        private GlyphVertex interpolateFrom(GlyphVertex from, GlyphVertex to, float ratio) {
            this.x = lerp(from.x, to.x, ratio);
            this.y = lerp(from.y, to.y, ratio);
            this.z = lerp(from.z, to.z, ratio);
            this.clipX = lerp(from.clipX, to.clipX, ratio);
            this.clipY = lerp(from.clipY, to.clipY, ratio);
            this.color = from.color;
            this.u = lerp(from.u, to.u, ratio);
            this.v = lerp(from.v, to.v, ratio);
            this.light = from.light;
            return this;
        }

        private void emitTo(VertexConsumer consumer) {
            consumer.addVertex(this.x, this.y, this.z)
                    .setColor(this.color)
                    .setUv(this.u, this.v)
                    .setLight(this.light);
        }
    }
}
