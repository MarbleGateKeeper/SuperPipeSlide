package dev.marblegate.superpipeslide.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;

public final class SubmitTextRenderer {
    private SubmitTextRenderer() {}

    public static void submitText(SubmitNodeCollector collector, PoseStack poseStack, Font font, float x, float y,
            FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode, int lightCoords,
            int color, int backgroundColor, int outlineColor) {
        RecordingBufferSource bufferSource = new RecordingBufferSource();
        if (outlineColor == 0) {
            font.drawInBatch(string, x, y, color, dropShadow, poseStack.last().pose(), bufferSource, displayMode, backgroundColor, lightCoords);
        } else {
            font.drawInBatch8xOutline(string, x, y, color, outlineColor, poseStack.last().pose(), bufferSource, lightCoords);
        }
        bufferSource.submit(collector, poseStack);
    }

    private static final class RecordingBufferSource implements MultiBufferSource {
        private final Map<RenderType, RecordingVertexConsumer> consumers = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this.consumers.computeIfAbsent(ClientRenderCompatibility.world(renderType), ignored -> new RecordingVertexConsumer());
        }

        private void submit(SubmitNodeCollector collector, PoseStack poseStack) {
            for (Map.Entry<RenderType, RecordingVertexConsumer> entry : this.consumers.entrySet()) {
                List<VertexData> vertices = entry.getValue().vertices();
                if (vertices.isEmpty()) {
                    continue;
                }
                ClientRenderCompatibility.submitCustomGeometry(collector, poseStack, entry.getKey(), (pose, buffer) -> {
                    for (VertexData vertex : vertices) {
                        vertex.emit(buffer);
                    }
                });
            }
        }
    }

    private static final class RecordingVertexConsumer implements VertexConsumer {
        private final List<MutableVertex> vertices = new ArrayList<>();
        private MutableVertex current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.current = new MutableVertex(x, y, z);
            this.vertices.add(this.current);
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
                this.current.hasUv = true;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (this.current != null) {
                this.current.overlay = u & 0xFFFF | (v & 0xFFFF) << 16;
                this.current.hasOverlay = true;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (this.current != null) {
                this.current.light = u & 0xFFFF | (v & 0xFFFF) << 16;
                this.current.hasLight = true;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (this.current != null) {
                this.current.normalX = x;
                this.current.normalY = y;
                this.current.normalZ = z;
                this.current.hasNormal = true;
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            if (this.current != null) {
                this.current.lineWidth = width;
                this.current.hasLineWidth = true;
            }
            return this;
        }

        private List<VertexData> vertices() {
            List<VertexData> immutable = new ArrayList<>(this.vertices.size());
            for (MutableVertex vertex : this.vertices) {
                immutable.add(vertex.freeze());
            }
            return List.copyOf(immutable);
        }
    }

    private static final class MutableVertex {
        private final float x;
        private final float y;
        private final float z;
        private int color = 0xFFFFFFFF;
        private float u;
        private float v;
        private int overlay;
        private int light = LightCoordsUtil.FULL_BRIGHT;
        private float normalX;
        private float normalY = 1.0F;
        private float normalZ;
        private float lineWidth = 1.0F;
        private boolean hasUv;
        private boolean hasOverlay;
        private boolean hasLight;
        private boolean hasNormal;
        private boolean hasLineWidth;

        private MutableVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private VertexData freeze() {
            return new VertexData(this.x, this.y, this.z, this.color, this.u, this.v, this.overlay, this.light,
                    this.normalX, this.normalY, this.normalZ, this.lineWidth, this.hasUv, this.hasOverlay,
                    this.hasLight, this.hasNormal, this.hasLineWidth);
        }
    }

    private record VertexData(float x, float y, float z, int color, float u, float v, int overlay, int light,
            float normalX, float normalY, float normalZ, float lineWidth, boolean hasUv, boolean hasOverlay,
            boolean hasLight, boolean hasNormal, boolean hasLineWidth) {
        private void emit(VertexConsumer buffer) {
            VertexConsumer consumer = buffer.addVertex(this.x, this.y, this.z).setColor(this.color);
            if (this.hasUv) {
                consumer.setUv(this.u, this.v);
            }
            if (this.hasOverlay) {
                consumer.setOverlay(this.overlay);
            }
            if (this.hasLight) {
                consumer.setLight(this.light);
            }
            if (this.hasNormal) {
                consumer.setNormal(this.normalX, this.normalY, this.normalZ);
            }
            if (this.hasLineWidth) {
                consumer.setLineWidth(this.lineWidth);
            }
        }
    }
}
