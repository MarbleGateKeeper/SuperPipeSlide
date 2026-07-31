package dev.marblegate.superpipeslide.client.fullmap.export;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * Minimal off-screen re-implementation of the vanilla GUI renderer pipeline (see
 * {@link net.minecraft.client.gui.render.GuiRenderer}) that draws a {@link GuiRenderState} frame
 * into a private {@link TextureTarget} and reads the pixels back into a {@link NativeImage}.
 *
 * <p>
 * Only the paths the full map uses are supported: textured blits, plain fills, and glyph text.
 * Items and picture-in-picture states are never submitted by the schematic export path (schematic
 * tiles are abstracted items, not real ones).
 *
 * <p>
 * The render target is {@code ceil(logical * pixelScale)} physical pixels while the projection is
 * set up for the logical size, so the GUI-space layout is identical to the on-screen map but every
 * element — lines, icons, and glyph quads — is rasterized at {@code pixelScale} times the density.
 * Using the window's GUI scale as the baseline pixel scale makes the export exactly as crisp as the
 * map on screen.
 *
 * <p>
 * Two environment deviations from the on-screen path are applied deliberately:
 * <ul>
 * <li>the fog UBO is initialized with vanilla's "no fog" sentinel values (zero color +
 * {@link Float#MAX_VALUE} distances), mirroring {@code FogRenderer}'s empty buffer. Binding an
 * uninitialized buffer is not an option — the text shader derives its fragment color from the fog
 * UBO, so garbage contents tint every glyph;</li>
 * <li>{@code Sampler2} (the lightmap sampled by the GUI text shader, see
 * {@code assets/minecraft/shaders/core/rendertype_text.vsh}) is ALWAYS bound to a private 1×1 white
 * texture instead of the live dimension lightmap that {@link GlyphRenderState} provides. Exports
 * must be readable regardless of the dimension the player is standing in — an offline export made
 * from the Nether must not bake the Nether's warm tint into the text.</li>
 * </ul>
 *
 * <p>
 * All methods must be called on the render thread (the PNG write itself happens on the IO pool).
 * {@link #close()} must be called once done; it is idempotent.
 */
public final class OffscreenGuiRenderer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<ScreenRectangle> SCISSOR_COMPARATOR = Comparator.nullsFirst(
            Comparator.comparing(ScreenRectangle::top).thenComparing(ScreenRectangle::bottom).thenComparing(ScreenRectangle::left)
                    .thenComparing(ScreenRectangle::right));
    private static final Comparator<TextureSetup> TEXTURE_COMPARATOR = Comparator.nullsFirst(Comparator.comparing(TextureSetup::getSortKey));
    private static final Comparator<GuiElementRenderState> ELEMENT_SORT_COMPARATOR = Comparator
            .comparing(GuiElementRenderState::scissorArea, SCISSOR_COMPARATOR)
            .thenComparing(GuiElementRenderState::pipeline, Comparator.comparing(RenderPipeline::getSortKey))
            .thenComparing(GuiElementRenderState::textureSetup, TEXTURE_COMPARATOR);

    private final TextureTarget target;
    private final int logicalWidth;
    private final int logicalHeight;
    private final double pixelScale;
    private final GuiRenderState renderState = new GuiRenderState();
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionMatrixBuffer = new ProjectionMatrixBuffer("sps-map-export");
    private final ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(786432);
    private final Map<VertexFormat, MappableRingBuffer> vertexBuffers = new Object2ObjectOpenHashMap<>();
    private final GpuBuffer emptyFogBuffer;
    private final GpuTexture neutralLightmapTexture;
    private final GpuTextureView neutralLightmapView;
    private final GpuSampler neutralLightmapSampler;
    private final List<MeshToDraw> meshesToDraw = new ArrayList<>();
    private final List<Draw> draws = new ArrayList<>();
    private @Nullable ScreenRectangle previousScissorArea;
    private @Nullable RenderPipeline previousPipeline;
    private @Nullable TextureSetup previousTextureSetup;
    private @Nullable BufferBuilder bufferBuilder;
    private boolean closed;

    public OffscreenGuiRenderer(int logicalWidth, int logicalHeight, double pixelScale) {
        RenderSystem.assertOnRenderThread();
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.pixelScale = pixelScale;
        int targetWidth = Math.max(1, (int) Math.ceil(logicalWidth * pixelScale));
        int targetHeight = Math.max(1, (int) Math.ceil(logicalHeight * pixelScale));
        this.target = new TextureTarget("sps-map-export", targetWidth, targetHeight, false);
        GpuDevice device = RenderSystem.getDevice();
        // Initialize the fog UBO with vanilla's "no fog" sentinel values — never bind an
        // uninitialized uniform buffer (see class javadoc).
        ByteBuffer fogData = ByteBuffer.allocateDirect(FogRenderer.FOG_UBO_SIZE);
        fogData.putFloat(0.0F).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
        for (int i = 0; i < 6; i++) {
            fogData.putFloat(Float.MAX_VALUE);
        }
        fogData.flip();
        this.emptyFogBuffer = device.createBuffer(() -> "SPS map export fog", 128, fogData);
        // A 1x1 white texture stands in for the lightmap so text renders fully lit everywhere.
        this.neutralLightmapTexture = device.createTexture("SPS map export neutral lightmap",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, 1, 1, 1, 1);
        this.neutralLightmapView = device.createTextureView(this.neutralLightmapTexture);
        try (NativeImage white = new NativeImage(1, 1, false)) {
            white.setPixelABGR(0, 0, 0xFFFFFFFF);
            device.createCommandEncoder().writeToTexture(this.neutralLightmapTexture, white);
        }
        // Owned by the sampler cache — must NOT be closed here.
        this.neutralLightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    /** Logical (GUI-space) width the map content is laid out for. */
    public int logicalWidth() {
        return this.logicalWidth;
    }

    /** Logical (GUI-space) height the map content is laid out for. */
    public int logicalHeight() {
        return this.logicalHeight;
    }

    /** Render-target pixel width (logical width × pixel scale, rounded up). */
    public int targetWidth() {
        return this.target.width;
    }

    /** Render-target pixel height (logical height × pixel scale, rounded up). */
    public int targetHeight() {
        return this.target.height;
    }

    /** Starts a frame: resets the GUI state and returns the extractor used to submit elements. */
    public GuiGraphicsExtractor beginFrame() {
        this.renderState.reset();
        return new GuiGraphicsExtractor(Minecraft.getInstance(), this.renderState, -1, -1);
    }

    /**
     * Flushes the submitted GUI elements into the target, reads the pixels back preserving alpha,
     * and writes them as a PNG on the IO pool. {@code onDone} receives the write outcome and runs
     * on the IO pool thread — hop back to the render thread before touching GPU state.
     */
    public void flushToPng(Path outFile, Consumer<Boolean> onDone) {
        prepareText();
        this.renderState.sortElements(ELEMENT_SORT_COMPARATOR);
        addElementsToMeshes();
        recordDraws();
        draw();
        for (MappableRingBuffer buffer : this.vertexBuffers.values()) {
            buffer.rotate();
        }
        this.draws.clear();
        this.renderState.reset();
        takeScreenshotPreserveAlpha(this.target, image -> Util.ioPool().execute(() -> {
            boolean success = false;
            try (image) {
                Files.createDirectories(outFile.toAbsolutePath().getParent());
                image.writeToFile(outFile.toFile());
                success = true;
            } catch (Exception exception) {
                LOGGER.warn("Failed to write map export PNG {}", outFile, exception);
            }
            onDone.accept(success);
        }));
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        for (MeshToDraw meshToDraw : this.meshesToDraw) {
            meshToDraw.close();
        }
        this.meshesToDraw.clear();
        this.byteBufferBuilder.close();
        this.projectionMatrixBuffer.close();
        this.emptyFogBuffer.close();
        this.neutralLightmapView.close();
        this.neutralLightmapTexture.close();
        for (MappableRingBuffer buffer : this.vertexBuffers.values()) {
            buffer.close();
        }
        this.vertexBuffers.clear();
        this.target.destroyBuffers();
    }

    private void prepareText() {
        this.renderState.forEachText(text -> {
            final Matrix3x2fc pose = text.pose;
            final ScreenRectangle scissor = text.scissor;
            text.ensurePrepared().visit(new Font.GlyphVisitor() {
                @Override
                public void acceptGlyph(TextRenderable.Styled glyph) {
                    this.accept(glyph);
                }

                @Override
                public void acceptEffect(TextRenderable effect) {
                    this.accept(effect);
                }

                private void accept(TextRenderable glyph) {
                    OffscreenGuiRenderer.this.renderState.addGlyphToCurrentLayer(new GlyphRenderState(pose, glyph, scissor));
                }
            });
        });
    }

    private void addElementsToMeshes() {
        this.previousScissorArea = null;
        this.previousPipeline = null;
        this.previousTextureSetup = null;
        this.bufferBuilder = null;
        this.renderState.forEachElement(this::addElementToMesh, GuiRenderState.TraverseRange.ALL);
        if (this.bufferBuilder != null) {
            this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea);
        }
    }

    private void addElementToMesh(GuiElementRenderState elementState) {
        RenderPipeline pipeline = elementState.pipeline();
        TextureSetup textureSetup = elementState.textureSetup();
        ScreenRectangle scissorArea = elementState.scissorArea();
        if (pipeline != this.previousPipeline || this.scissorChanged(scissorArea, this.previousScissorArea) || !textureSetup.equals(this.previousTextureSetup)
                || pipeline.getVertexFormatMode().connectedPrimitives // Neo: flush elements with connected primitives individually
        ) {
            if (this.bufferBuilder != null) {
                this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea);
            }
            this.bufferBuilder = new BufferBuilder(this.byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            this.previousPipeline = pipeline;
            this.previousTextureSetup = textureSetup;
            this.previousScissorArea = scissorArea;
        }
        elementState.buildVertices(this.bufferBuilder);
    }

    private boolean scissorChanged(@Nullable ScreenRectangle newScissor, @Nullable ScreenRectangle oldScissor) {
        if (newScissor == oldScissor) {
            return false;
        } else {
            return newScissor != null ? !newScissor.equals(oldScissor) : true;
        }
    }

    private void recordMesh(BufferBuilder bufferBuilder, RenderPipeline pipeline, TextureSetup textureSetup, @Nullable ScreenRectangle scissorArea) {
        MeshData mesh = bufferBuilder.build();
        if (mesh != null) {
            this.meshesToDraw.add(new MeshToDraw(mesh, pipeline, textureSetup, scissorArea));
        }
    }

    private void recordDraws() {
        this.ensureVertexBufferSizes();
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        Object2IntMap<VertexFormat> offsets = new Object2IntOpenHashMap<>();
        for (MeshToDraw meshToDraw : this.meshesToDraw) {
            MeshData mesh = meshToDraw.mesh;
            MeshData.DrawState drawState = mesh.drawState();
            VertexFormat format = drawState.format();
            MappableRingBuffer vertexBuffer = this.vertexBuffers.get(format);
            if (!offsets.containsKey(format)) {
                offsets.put(format, 0);
            }
            ByteBuffer meshVertexBuffer = mesh.vertexBuffer();
            int meshBufferSize = meshVertexBuffer.remaining();
            int offset = offsets.getInt(format);
            try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer().slice(offset, meshBufferSize), false, true)) {
                MemoryUtil.memCopy(meshVertexBuffer, mappedView.data());
            }
            offsets.put(format, offset + meshBufferSize);
            this.draws.add(new Draw(vertexBuffer.currentBuffer(), offset / format.getVertexSize(), drawState.indexCount(), meshToDraw.pipeline,
                    meshToDraw.textureSetup, meshToDraw.scissorArea));
            meshToDraw.close();
        }
        this.meshesToDraw.clear();
    }

    private void ensureVertexBufferSizes() {
        Object2IntMap<VertexFormat> requiredSizes = new Object2IntOpenHashMap<>();
        for (MeshToDraw meshToDraw : this.meshesToDraw) {
            MeshData.DrawState drawState = meshToDraw.mesh.drawState();
            VertexFormat format = drawState.format();
            requiredSizes.put(format, requiredSizes.getInt(format) + drawState.vertexCount() * format.getVertexSize());
        }
        for (Object2IntMap.Entry<VertexFormat> entry : requiredSizes.object2IntEntrySet()) {
            VertexFormat format = entry.getKey();
            int requiredSize = entry.getIntValue();
            MappableRingBuffer vertexBuffer = this.vertexBuffers.get(format);
            if (vertexBuffer == null || vertexBuffer.size() < requiredSize) {
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                }
                this.vertexBuffers.put(format, new MappableRingBuffer(() -> "GUI vertex buffer for " + format, 34, requiredSize));
            }
        }
    }

    private void draw() {
        if (this.draws.isEmpty()) {
            return;
        }
        // The projection maps GUI units onto the logical size; with a pixelScale times larger
        // target this rasterizes every element at pixelScale times the on-screen density.
        this.projection.setupOrtho(1000.0F, 11000.0F, this.logicalWidth, this.logicalHeight, true);
        RenderSystem.setProjectionMatrix(this.projectionMatrixBuffer.getBuffer(this.projection), ProjectionType.ORTHOGRAPHIC);
        int maxIndexCount = 0;
        for (Draw draw : this.draws) {
            maxIndexCount = Math.max(maxIndexCount, draw.indexCount());
        }
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = autoIndices.getBuffer(maxIndexCount);
        VertexFormat.IndexType indexType = autoIndices.type();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(),
                        new Matrix4f());
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SPS map export",
                this.target.getColorTextureView(), OptionalInt.of(0), null, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("Fog", this.emptyFogBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE));
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            for (Draw draw : this.draws) {
                this.executeDraw(draw, renderPass, indexBuffer, indexType);
            }
        }
    }

    private void executeDraw(Draw draw, RenderPass renderPass, GpuBuffer indexBuffer, VertexFormat.IndexType indexType) {
        RenderPipeline pipeline = draw.pipeline();
        renderPass.setPipeline(pipeline);
        renderPass.setVertexBuffer(0, draw.vertexBuffer());
        ScreenRectangle scissorArea = draw.scissorArea();
        if (scissorArea != null) {
            int left = Math.round(scissorArea.left() * (float) this.pixelScale);
            int bottom = Math.round(scissorArea.bottom() * (float) this.pixelScale);
            int width = Math.max(0, Math.round(scissorArea.width() * (float) this.pixelScale));
            int height = Math.max(0, Math.round(scissorArea.height() * (float) this.pixelScale));
            renderPass.enableScissor(left, this.target.height - bottom, width, height);
        } else {
            renderPass.disableScissor();
        }
        if (draw.textureSetup().texure0() != null) {
            renderPass.bindTexture("Sampler0", draw.textureSetup().texure0(), draw.textureSetup().sampler0());
        }
        if (draw.textureSetup().texure1() != null) {
            renderPass.bindTexture("Sampler1", draw.textureSetup().texure1(), draw.textureSetup().sampler1());
        }
        // Deliberate deviation from vanilla (see class javadoc): whenever the pipeline declares the
        // lightmap sampler, bind the 1x1 white stand-in instead of the element's live lightmap.
        if (pipeline.getSamplers().contains("Sampler2")) {
            renderPass.bindTexture("Sampler2", this.neutralLightmapView, this.neutralLightmapSampler);
        }
        if (pipeline.getVertexFormatMode() != VertexFormat.Mode.QUADS) {
            RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indexBuffer = autoStorageIndexBuffer.getBuffer(draw.indexCount());
            indexType = autoStorageIndexBuffer.type();
        }
        renderPass.setIndexBuffer(indexBuffer, indexType);
        renderPass.drawIndexed(draw.baseVertex(), 0, draw.indexCount(), 1);
    }

    /**
     * Copy of {@link net.minecraft.client.Screenshot#takeScreenshot(RenderTarget, Consumer)} with a
     * fixed downscale of 1 that preserves the alpha channel — vanilla forces alpha to 255, which
     * would erase the transparent background of background-less exports.
     */
    private static void takeScreenshotPreserveAlpha(RenderTarget target, Consumer<NativeImage> callback) {
        int width = target.width;
        int height = target.height;
        GpuTexture sourceTexture = target.getColorTexture();
        if (sourceTexture == null) {
            throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
        }
        GpuBuffer buffer = RenderSystem.getDevice()
                .createBuffer(() -> "Screenshot buffer", 9, (long) width * (long) height * (long) sourceTexture.getFormat().pixelSize());
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(sourceTexture, buffer, 0L, () -> {
            try (GpuBuffer.MappedView read = commandEncoder.mapBuffer(buffer, true, false)) {
                int pixelSize = sourceTexture.getFormat().pixelSize();
                NativeImage image = new NativeImage(width, height, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = read.data().getInt((x + y * width) * pixelSize);
                        image.setPixelABGR(x, height - y - 1, argb);
                    }
                }
                callback.accept(image);
            }
            buffer.close();
        }, 0);
    }

    private record Draw(GpuBuffer vertexBuffer, int baseVertex, int indexCount, RenderPipeline pipeline, TextureSetup textureSetup,
            @Nullable ScreenRectangle scissorArea) {}

    private record MeshToDraw(MeshData mesh, RenderPipeline pipeline, TextureSetup textureSetup, @Nullable ScreenRectangle scissorArea)
            implements AutoCloseable {
        @Override
        public void close() {
            this.mesh.close();
        }
    }
}
