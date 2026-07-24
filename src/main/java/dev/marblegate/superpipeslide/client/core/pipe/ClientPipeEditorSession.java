package dev.marblegate.superpipeslide.client.core.pipe;

import dev.marblegate.superpipeslide.common.core.geometry.AnchorAttachOffsets;
import dev.marblegate.superpipeslide.common.core.geometry.CurveSpec;
import dev.marblegate.superpipeslide.common.core.geometry.PathCurves;
import dev.marblegate.superpipeslide.common.core.geometry.PipeAnchorId;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnection;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionLengthPolicy;
import dev.marblegate.superpipeslide.common.core.geometry.PipeConnectionRaycast;
import dev.marblegate.superpipeslide.common.core.geometry.PipePathNode;
import dev.marblegate.superpipeslide.common.core.networkgraph.model.PipeNode;
import dev.marblegate.superpipeslide.common.item.pipe.PipeEditorItem;
import dev.marblegate.superpipeslide.config.Config;
import dev.marblegate.superpipeslide.network.editor.ServerboundUpdateAnchorOffsetPayload;
import dev.marblegate.superpipeslide.network.editor.ServerboundUpdatePipeGeometryPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client-side in-world pipe shape editing session. Opened by right-clicking an anchor
 * (move its attach point) or a pipe (node-based curve editing) while holding a pipe
 * editor item. All edits only affect a local working copy shown as ghost preview lines;
 * nothing changes until the player confirms, which sends a single commit payload to the
 * server. Cancelling (sneak + right-click, or switching away from the tool) discards the
 * working copy without any network traffic.
 *
 * <p>Path editing follows the PS pen-tool model: nodes split the curve into cubic
 * segments. Right-click and hold to drag a node, or drag a segment to bend it towards the
 * cursor (which pins the segment's end handles, i.e. makes them manual). Sneak +
 * right-click inserts a node on the aimed segment or deletes the aimed node. Scrolling on
 * a node toggles it between automatic (neighbours follow edits) and manual (pinned).
 */
public final class ClientPipeEditorSession {
    private static final double PICK_REACH = 8.0D;
    private static final double PIPE_PICK_RADIUS = 0.55D;
    private static final double NODE_PICK_RADIUS = 0.14D;
    private static final double SEGMENT_PICK_RADIUS = 0.20D;
    private static final double SCROLL_DEPTH_STEP = 0.25D;
    private static final int MAX_PATH_NODES = 16;

    private static final int GHOST_VALID_COLOR = 0xE040D8FF;
    private static final int GHOST_INVALID_COLOR = 0xE0FF5050;
    private static final int NODE_AUTO_COLOR = 0xE060C8FF;
    private static final int NODE_MANUAL_COLOR = 0xE0FFD85A;
    private static final int NODE_AIMED_COLOR = 0xF0FFFFFF;
    private static final int HANDLE_COLOR = 0xE0FF9040;
    private static final int CELL_FRAME_COLOR = 0x80555555;
    private static final int AXIS_X_COLOR = 0xE0FF5555;
    private static final int AXIS_Y_COLOR = 0xE055FF55;
    private static final int AXIS_Z_COLOR = 0xE05555FF;
    private static final int SEGMENT_AIM_COLOR = 0xF0FFFFFF;

    private static ClientPipeEditorSession active;

    private final boolean anchorMode;

    // Anchor session state
    private final PipeAnchorId anchor;
    private Vec3 workingOffset;
    private int activeAxis;

    // Path session state
    private final UUID connectionId;
    private PipeAnchorId fromAnchor;
    private PipeAnchorId toAnchor;
    private Vec3 fromPoint;
    private Vec3 toPoint;
    private Optional<Vec3> startTangent;
    private Optional<Vec3> endTangent;
    private List<PipePathNode> nodes;
    private Drag drag;
    private Aim highlightedAim = Aim.None.INSTANCE;

    private ClientPipeEditorSession(PipeAnchorId anchor, Vec3 workingOffset) {
        this.anchorMode = true;
        this.anchor = anchor;
        this.workingOffset = workingOffset;
        this.activeAxis = 1;
        this.connectionId = null;
    }

    private ClientPipeEditorSession(PipeConnection target) {
        this.anchorMode = false;
        this.anchor = null;
        this.connectionId = target.id();
        this.fromAnchor = target.fromAnchor();
        this.toAnchor = target.toAnchor();
        this.fromPoint = target.fromSurface();
        this.toPoint = target.toSurface();
        CurveSpec pathSpec = target.curveSpec().asPath();
        this.startTangent = pathSpec.startTangent();
        this.endTangent = pathSpec.endTangent();
        this.nodes = new ArrayList<>(pathSpec.pathNodes());
    }

    public record GhostLine(Vec3 from, Vec3 to, int color, float width) {}

    private sealed interface Aim {
        record Node(int index) implements Aim {}

        record Segment(int segment, double t) implements Aim {}

        enum None implements Aim {
            INSTANCE
        }
    }

    private static final class Drag {
        final boolean segmentDrag;
        final int index;
        final double tHat;
        final Vec3 p0;
        final Vec3 p3;
        final Vec3 dir0;
        final Vec3 dir1;
        double depth;

        Drag(boolean segmentDrag, int index, double tHat, Vec3 p0, Vec3 p3, Vec3 dir0, Vec3 dir1, double depth) {
            this.segmentDrag = segmentDrag;
            this.index = index;
            this.tHat = tHat;
            this.p0 = p0;
            this.p3 = p3;
            this.dir0 = dir0;
            this.dir1 = dir1;
            this.depth = depth;
        }
    }

    public static boolean isActive() {
        return active != null;
    }

    public static void clear() {
        active = null;
    }

    /**
     * Handles a use (right) click while the editor item is held. Returns true when the
     * click was consumed by the session and must not reach vanilla interactions.
     */
    public static boolean onUseClick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.level() == null || !(player.getMainHandItem().getItem() instanceof PipeEditorItem)) {
            return false;
        }
        if (active == null) {
            return tryOpen(minecraft, player);
        }
        if (active.drag != null) {
            return true;
        }
        active.handleClick(minecraft, player);
        return true;
    }

    public static boolean onScroll(double deltaY) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (active == null || player == null || Math.abs(deltaY) < 1.0E-6D) {
            return false;
        }
        active.handleScroll(player, deltaY > 0.0D);
        return true;
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (active == null) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof PipeEditorItem)) {
            overlay(player, "message.superpipeslide.pipe_editor.cancelled", ChatFormatting.YELLOW);
            active = null;
            return;
        }
        if (active.anchorMode ? ClientPipeNetworkCache.node(active.anchor).isEmpty() : ClientPipeNetworkCache.globalConnection(active.connectionId).isEmpty()) {
            overlay(player, "message.superpipeslide.pipe_editor.target_lost", ChatFormatting.RED);
            active = null;
            return;
        }
        if (active.drag != null) {
            if (!minecraft.options.keyUse.isDown()) {
                active.drag = null;
                overlay(player, "message.superpipeslide.pipe_editor.dropped", ChatFormatting.GRAY);
            } else {
                active.updateDrag(player);
            }
        }
        active.highlightedAim = active.drag == null && !active.anchorMode ? active.computePathAim(player) : Aim.None.INSTANCE;
    }

    public static List<GhostLine> collectGhostLines() {
        if (active == null) {
            return List.of();
        }
        return active.buildGhostLines();
    }

    private static boolean tryOpen(Minecraft minecraft, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        if (minecraft.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            PipeAnchorId anchorId = new PipeAnchorId(player.level().dimension(), blockHit.getBlockPos());
            Optional<PipeNode> node = ClientPipeNetworkCache.node(anchorId);
            if (node.isPresent()) {
                Vec3 offset = node.get().attachPoint().subtract(Vec3.atCenterOf(anchorId.blockPos()));
                active = new ClientPipeEditorSession(anchorId, offset);
                overlay(player, "message.superpipeslide.pipe_editor.opened_anchor", ChatFormatting.GREEN);
                return true;
            }
        }
        Optional<PipeConnectionRaycast.Hit> hit = PipeConnectionRaycast.find(
                ClientPipeNetworkCache.connections(player.level().dimension()), eye, look, PICK_REACH, PIPE_PICK_RADIUS);
        if (hit.isPresent()) {
            active = new ClientPipeEditorSession(hit.get().connection());
            overlay(player, "message.superpipeslide.pipe_editor.opened_path", ChatFormatting.GREEN);
            return true;
        }
        return false;
    }

    private void handleClick(Minecraft minecraft, LocalPlayer player) {
        boolean sneaking = player.isShiftKeyDown();
        if (this.anchorMode) {
            if (sneaking) {
                cancel(player);
            } else {
                confirm(player);
            }
            return;
        }
        Aim aim = computePathAim(player);
        if (aim instanceof Aim.Node nodeAim) {
            if (sneaking) {
                deleteNode(player, nodeAim.index());
            } else {
                startNodeDrag(player, nodeAim.index());
            }
        } else if (aim instanceof Aim.Segment segmentAim) {
            if (sneaking) {
                insertNode(player, segmentAim);
            } else {
                startSegmentDrag(player, segmentAim);
            }
        } else if (sneaking) {
            cancel(player);
        } else {
            confirm(player);
        }
    }

    private void handleScroll(LocalPlayer player, boolean up) {
        boolean sneaking = player.isShiftKeyDown();
        if (this.anchorMode) {
            if (sneaking) {
                this.activeAxis = (this.activeAxis + (up ? 1 : 2)) % 3;
                overlay(player, "message.superpipeslide.pipe_editor.axis_" + "xyz".charAt(this.activeAxis), ChatFormatting.GRAY);
            } else {
                double delta = (up ? 1.0D : -1.0D) * AnchorAttachOffsets.STEP;
                this.workingOffset = AnchorAttachOffsets.sanitize(this.workingOffset.add(axisVector(this.activeAxis).scale(delta)));
                overlay(player, "message.superpipeslide.pipe_editor.offset", ChatFormatting.GRAY, formatOffset(this.workingOffset));
            }
            return;
        }
        if (this.drag != null) {
            this.drag.depth = Math.max(1.0D, this.drag.depth + (up ? 1.0D : -1.0D) * SCROLL_DEPTH_STEP);
            updateDrag(player);
            return;
        }
        Aim aim = computePathAim(player);
        if (aim instanceof Aim.Node nodeAim) {
            toggleNodeMode(player, nodeAim.index());
        }
    }

    private void confirm(LocalPlayer player) {
        if (this.anchorMode) {
            ClientPacketDistributor.sendToServer(new ServerboundUpdateAnchorOffsetPayload(this.anchor, this.workingOffset));
        } else {
            ClientPacketDistributor.sendToServer(new ServerboundUpdatePipeGeometryPayload(this.connectionId, CurveSpec.path(this.nodes, this.startTangent, this.endTangent)));
        }
        overlay(player, "message.superpipeslide.pipe_editor.submitted", ChatFormatting.GREEN);
        active = null;
    }

    private void cancel(LocalPlayer player) {
        overlay(player, "message.superpipeslide.pipe_editor.cancelled", ChatFormatting.YELLOW);
        active = null;
    }

    private void startNodeDrag(LocalPlayer player, int index) {
        Vec3 eye = player.getEyePosition();
        this.drag = new Drag(false, index, 0.0D, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, eye.distanceTo(this.nodes.get(index).position()));
        overlay(player, "message.superpipeslide.pipe_editor.dragging", ChatFormatting.GRAY);
    }

    private void startSegmentDrag(LocalPlayer player, Aim.Segment aim) {
        Vec3 p0 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, aim.segment());
        Vec3 p3 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, aim.segment() + 1);
        Vec3 p1 = PathCurves.outHandle(this.fromPoint, this.toPoint, this.nodes, this.startTangent, aim.segment());
        Vec3 p2 = PathCurves.inHandle(this.fromPoint, this.toPoint, this.nodes, this.endTangent, aim.segment() + 1);
        Vec3 chord = p3.subtract(p0);
        Vec3 dir0 = p1.subtract(p0);
        dir0 = dir0.lengthSqr() < 1.0E-6D ? chord : dir0;
        Vec3 dir1 = p3.subtract(p2);
        dir1 = dir1.lengthSqr() < 1.0E-6D ? chord : dir1;
        if (chord.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 grab = cubic(p0, p1, p2, p3, aim.t());
        this.drag = new Drag(true, aim.segment(), aim.t(), p0, p3, dir0.normalize(), dir1.normalize(), player.getEyePosition().distanceTo(grab));
        overlay(player, "message.superpipeslide.pipe_editor.dragging", ChatFormatting.GRAY);
    }

    private void updateDrag(LocalPlayer player) {
        Vec3 target = player.getEyePosition().add(player.getViewVector(1.0F).scale(this.drag.depth));
        if (!this.drag.segmentDrag) {
            this.nodes.set(this.drag.index, this.nodes.get(this.drag.index).withPosition(target));
            return;
        }
        double t = this.drag.tHat;
        double u = 1.0D - t;
        double b1 = 3.0D * u * u * t;
        double b2 = 3.0D * u * t * t;
        double b0 = u * u * u;
        double b3 = t * t * t;
        Vec3 base = this.drag.p0.scale(b0 + b1).add(this.drag.p3.scale(b2 + b3));
        Vec3 residual = target.subtract(base);
        double a11 = b1 * b1;
        double a22 = b2 * b2;
        double a12 = -b1 * b2 * this.drag.dir0.dot(this.drag.dir1);
        double r1 = b1 * this.drag.dir0.dot(residual);
        double r2 = -b2 * this.drag.dir1.dot(residual);
        double determinant = a11 * a22 - a12 * a12;
        if (Math.abs(determinant) < 1.0E-9D) {
            return;
        }
        double maxHandle = 3.0D * Math.max(0.1D, this.drag.p0.distanceTo(this.drag.p3));
        double h1 = Mth.clamp((r1 * a22 - a12 * r2) / determinant, 0.0D, maxHandle);
        double h2 = Mth.clamp((a11 * r2 - a12 * r1) / determinant, 0.0D, maxHandle);
        int segment = this.drag.index;
        if (segment == 0) {
            this.startTangent = Optional.of(this.drag.dir0);
        } else {
            PipePathNode left = this.nodes.get(segment - 1);
            this.nodes.set(segment - 1, left.withHandles(left.inHandle(), Optional.of(this.drag.p0.add(this.drag.dir0.scale(h1)))));
        }
        if (segment == this.nodes.size()) {
            this.endTangent = Optional.of(this.drag.dir1);
        } else {
            PipePathNode right = this.nodes.get(segment);
            this.nodes.set(segment, right.withHandles(Optional.of(this.drag.p3.subtract(this.drag.dir1.scale(h2))), right.outHandle()));
        }
    }

    private void insertNode(LocalPlayer player, Aim.Segment aim) {
        if (this.nodes.size() >= MAX_PATH_NODES) {
            overlay(player, "message.superpipeslide.pipe_editor.node_limit", ChatFormatting.RED);
            return;
        }
        Vec3 p0 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, aim.segment());
        Vec3 p1 = PathCurves.outHandle(this.fromPoint, this.toPoint, this.nodes, this.startTangent, aim.segment());
        Vec3 p2 = PathCurves.inHandle(this.fromPoint, this.toPoint, this.nodes, this.endTangent, aim.segment() + 1);
        Vec3 p3 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, aim.segment() + 1);
        double t = aim.t();
        Vec3 q0 = p0.lerp(p1, t);
        Vec3 q1 = p1.lerp(p2, t);
        Vec3 q2 = p2.lerp(p3, t);
        Vec3 r0 = q0.lerp(q1, t);
        Vec3 r1 = q1.lerp(q2, t);
        Vec3 split = r0.lerp(r1, t);
        // Pin the boundary handles so the split preserves the current shape. Endpoint
        // tangents only carry a direction, so near an endpoint the shape is approximated.
        if (aim.segment() == 0 && q0.subtract(p0).lengthSqr() >= 1.0E-6D) {
            this.startTangent = Optional.of(q0.subtract(p0).normalize());
        } else if (aim.segment() > 0) {
            PipePathNode left = this.nodes.get(aim.segment() - 1);
            this.nodes.set(aim.segment() - 1, left.withHandles(left.inHandle(), Optional.of(q0)));
        }
        if (aim.segment() == this.nodes.size() && p3.subtract(q2).lengthSqr() >= 1.0E-6D) {
            this.endTangent = Optional.of(p3.subtract(q2).normalize());
        } else if (aim.segment() < this.nodes.size()) {
            PipePathNode right = this.nodes.get(aim.segment());
            this.nodes.set(aim.segment(), right.withHandles(Optional.of(q2), right.outHandle()));
        }
        this.nodes.add(aim.segment(), new PipePathNode(split, Optional.of(r0), Optional.of(r1)));
        overlay(player, "message.superpipeslide.pipe_editor.node_inserted", ChatFormatting.GRAY);
    }

    private void deleteNode(LocalPlayer player, int index) {
        this.nodes.remove(index);
        if (index - 1 >= 0) {
            PipePathNode left = this.nodes.get(index - 1);
            this.nodes.set(index - 1, left.withHandles(left.inHandle(), Optional.empty()));
        }
        if (index < this.nodes.size()) {
            PipePathNode right = this.nodes.get(index);
            this.nodes.set(index, right.withHandles(Optional.empty(), right.outHandle()));
        }
        overlay(player, "message.superpipeslide.pipe_editor.node_deleted", ChatFormatting.GRAY);
    }

    private void toggleNodeMode(LocalPlayer player, int index) {
        PipePathNode node = this.nodes.get(index);
        if (node.isAutomatic()) {
            Vec3 in = PathCurves.inHandle(this.fromPoint, this.toPoint, this.nodes, this.endTangent, index + 1);
            Vec3 out = PathCurves.outHandle(this.fromPoint, this.toPoint, this.nodes, this.startTangent, index + 1);
            this.nodes.set(index, node.withHandles(Optional.of(in), Optional.of(out)));
            overlay(player, "message.superpipeslide.pipe_editor.node_manual", ChatFormatting.GRAY);
        } else {
            this.nodes.set(index, node.asAutomatic());
            overlay(player, "message.superpipeslide.pipe_editor.node_auto", ChatFormatting.GRAY);
        }
    }

    private Aim computePathAim(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        int bestNode = -1;
        double bestNodeDistance = NODE_PICK_RADIUS;
        for (int i = 0; i < this.nodes.size(); i++) {
            Vec3 position = this.nodes.get(i).position();
            if (eye.distanceTo(position) > PICK_REACH) {
                continue;
            }
            double distance = rayPointDistance(eye, look, position);
            if (distance < bestNodeDistance) {
                bestNode = i;
                bestNodeDistance = distance;
            }
        }
        if (bestNode >= 0) {
            return new Aim.Node(bestNode);
        }

        int bestSegment = -1;
        double bestSegmentT = 0.0D;
        double bestSegmentDistance = SEGMENT_PICK_RADIUS;
        int segmentSamples = 8;
        for (int segment = 0; segment <= this.nodes.size(); segment++) {
            Vec3 p0 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, segment);
            Vec3 p1 = PathCurves.outHandle(this.fromPoint, this.toPoint, this.nodes, this.startTangent, segment);
            Vec3 p2 = PathCurves.inHandle(this.fromPoint, this.toPoint, this.nodes, this.endTangent, segment + 1);
            Vec3 p3 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, segment + 1);
            Vec3 previous = p0;
            for (int i = 1; i <= segmentSamples; i++) {
                double t = (double) i / segmentSamples;
                Vec3 current = cubic(p0, p1, p2, p3, t);
                double[] result = raySegmentClosest(eye, look, previous, current);
                if (result[0] < bestSegmentDistance && result[1] <= PICK_REACH) {
                    bestSegment = segment;
                    bestSegmentT = t;
                    bestSegmentDistance = result[0];
                }
                previous = current;
            }
        }
        if (bestSegment >= 0) {
            return new Aim.Segment(bestSegment, bestSegmentT);
        }
        return Aim.None.INSTANCE;
    }

    private List<GhostLine> buildGhostLines() {
        List<GhostLine> lines = new ArrayList<>();
        if (this.anchorMode) {
            buildAnchorGhostLines(lines);
        } else {
            buildPathGhostLines(lines);
        }
        return lines;
    }

    private void buildAnchorGhostLines(List<GhostLine> lines) {
        BlockPos pos = this.anchor.blockPos();
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 attach = center.add(this.workingOffset);
        addCellFrame(lines, pos);
        for (int axis = 0; axis < 3; axis++) {
            int color = axis == 0 ? AXIS_X_COLOR : axis == 1 ? AXIS_Y_COLOR : AXIS_Z_COLOR;
            float width = axis == this.activeAxis ? 3.0F : 1.0F;
            Vec3 axisVec = axisVector(axis).scale(0.5D);
            lines.add(new GhostLine(attach.subtract(axisVec), attach.add(axisVec), color, width));
        }
        addCross(lines, attach, 0.12D, NODE_AIMED_COLOR, 2.0F);
        for (PipeConnection connection : ClientPipeNetworkCache.connectionsTouching(this.anchor)) {
            addPolyline(lines, connection.withEndpointAt(this.anchor, attach), GHOST_VALID_COLOR, 3.0F);
        }
    }

    private void buildPathGhostLines(List<GhostLine> lines) {
        PipeConnection working = workingConnection();
        boolean valid = !PipeConnectionLengthPolicy.exceedsLimit(working, Config.MAX_CONNECTION_LENGTH.getAsDouble());
        addPolyline(lines, working, valid ? GHOST_VALID_COLOR : GHOST_INVALID_COLOR, 3.0F);
        if (this.highlightedAim instanceof Aim.Segment segmentAim) {
            addSegmentPolyline(lines, segmentAim.segment(), SEGMENT_AIM_COLOR, 4.0F);
        }
        for (int i = 0; i < this.nodes.size(); i++) {
            PipePathNode node = this.nodes.get(i);
            boolean aimed = this.highlightedAim instanceof Aim.Node nodeAim && nodeAim.index() == i;
            int color = aimed ? NODE_AIMED_COLOR : node.isAutomatic() ? NODE_AUTO_COLOR : NODE_MANUAL_COLOR;
            addCross(lines, node.position(), aimed ? 0.16D : node.isAutomatic() ? 0.08D : 0.12D, color, 2.0F);
            node.inHandle().ifPresent(handle -> {
                lines.add(new GhostLine(node.position(), handle, HANDLE_COLOR, 1.0F));
                addCross(lines, handle, 0.05D, HANDLE_COLOR, 1.0F);
            });
            node.outHandle().ifPresent(handle -> {
                lines.add(new GhostLine(node.position(), handle, HANDLE_COLOR, 1.0F));
                addCross(lines, handle, 0.05D, HANDLE_COLOR, 1.0F);
            });
        }
        this.startTangent.ifPresent(tangent -> {
            if (tangent.lengthSqr() >= 1.0E-6D) {
                lines.add(new GhostLine(this.fromPoint, this.fromPoint.add(tangent.normalize().scale(PathCurves.endHandleLength(this.fromPoint, this.toPoint))), HANDLE_COLOR, 1.0F));
            }
        });
        this.endTangent.ifPresent(tangent -> {
            if (tangent.lengthSqr() >= 1.0E-6D) {
                lines.add(new GhostLine(this.toPoint, this.toPoint.subtract(tangent.normalize().scale(PathCurves.endHandleLength(this.fromPoint, this.toPoint))), HANDLE_COLOR, 1.0F));
            }
        });
        if (this.drag != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                Vec3 target = minecraft.player.getEyePosition().add(minecraft.player.getViewVector(1.0F).scale(this.drag.depth));
                addCross(lines, target, 0.10D, NODE_AIMED_COLOR, 2.0F);
            }
        }
    }

    private PipeConnection workingConnection() {
        return PipeConnection.withCurve(this.fromAnchor, this.toAnchor, CurveSpec.path(this.nodes, this.startTangent, this.endTangent))
                .withEndpoints(this.fromPoint, this.toPoint);
    }

    private void addPolyline(List<GhostLine> lines, PipeConnection connection, int color, float width) {
        double length = connection.length();
        int samples = Mth.clamp((int) Math.ceil(length * 2.0D), 8, 96);
        Vec3 previous = connection.positionAt(0.0D);
        for (int i = 1; i <= samples; i++) {
            Vec3 point = connection.positionAt(length * i / samples);
            lines.add(new GhostLine(previous, point, color, width));
            previous = point;
        }
    }

    private void addSegmentPolyline(List<GhostLine> lines, int segment, int color, float width) {
        Vec3 p0 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, segment);
        Vec3 p1 = PathCurves.outHandle(this.fromPoint, this.toPoint, this.nodes, this.startTangent, segment);
        Vec3 p2 = PathCurves.inHandle(this.fromPoint, this.toPoint, this.nodes, this.endTangent, segment + 1);
        Vec3 p3 = PathCurves.pointAt(this.fromPoint, this.toPoint, this.nodes, segment + 1);
        Vec3 previous = p0;
        for (int i = 1; i <= 12; i++) {
            Vec3 point = cubic(p0, p1, p2, p3, (double) i / 12);
            lines.add(new GhostLine(previous, point, color, width));
            previous = point;
        }
    }

    private static void addCross(List<GhostLine> lines, Vec3 center, double halfSize, int color, float width) {
        lines.add(new GhostLine(center.subtract(halfSize, 0.0D, 0.0D), center.add(halfSize, 0.0D, 0.0D), color, width));
        lines.add(new GhostLine(center.subtract(0.0D, halfSize, 0.0D), center.add(0.0D, halfSize, 0.0D), color, width));
        lines.add(new GhostLine(center.subtract(0.0D, 0.0D, halfSize), center.add(0.0D, 0.0D, halfSize), color, width));
    }

    private static void addCellFrame(List<GhostLine> lines, BlockPos pos) {
        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();
        double x1 = x0 + 1.0D;
        double y1 = y0 + 1.0D;
        double z1 = z0 + 1.0D;
        Vec3[] corners = {
                new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1),
                new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1) };
        int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
        for (int[] edge : edges) {
            lines.add(new GhostLine(corners[edge[0]], corners[edge[1]], CELL_FRAME_COLOR, 1.0F));
        }
    }

    private static Vec3 axisVector(int axis) {
        return switch (axis) {
            case 0 -> new Vec3(1.0D, 0.0D, 0.0D);
            case 1 -> new Vec3(0.0D, 1.0D, 0.0D);
            default -> new Vec3(0.0D, 0.0D, 1.0D);
        };
    }

    private static Vec3 cubic(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double inverse = 1.0D - t;
        return p0.scale(inverse * inverse * inverse)
                .add(p1.scale(3.0D * inverse * inverse * t))
                .add(p2.scale(3.0D * inverse * t * t))
                .add(p3.scale(t * t * t));
    }

    private static double rayPointDistance(Vec3 rayOrigin, Vec3 rayDirection, Vec3 point) {
        Vec3 toPoint = point.subtract(rayOrigin);
        double along = Math.max(0.0D, toPoint.dot(rayDirection));
        return toPoint.subtract(rayDirection.scale(along)).length();
    }

    /**
     * Returns [distance between ray and segment, ray parameter of the closest point].
     */
    private static double[] raySegmentClosest(Vec3 rayOrigin, Vec3 rayDirection, Vec3 segmentStart, Vec3 segmentEnd) {
        Vec3 segment = segmentEnd.subtract(segmentStart);
        Vec3 originDelta = segmentStart.subtract(rayOrigin);
        double a = segment.dot(segment);
        double b = segment.dot(rayDirection);
        double d = segment.dot(originDelta);
        double e = rayDirection.dot(originDelta);
        double denominator = a - b * b;
        double segmentT = 0.0D;
        if (denominator > 1.0E-8D) {
            segmentT = Mth.clamp((b * e - d) / denominator, 0.0D, 1.0D);
        }
        double rayT = Math.max(0.0D, b * segmentT + e);
        if (a > 1.0E-8D) {
            segmentT = Mth.clamp((b * rayT - d) / a, 0.0D, 1.0D);
        }
        Vec3 onSegment = segmentStart.add(segment.scale(segmentT));
        Vec3 onRay = rayOrigin.add(rayDirection.scale(rayT));
        return new double[] { onSegment.distanceTo(onRay), rayT };
    }

    private static String formatOffset(Vec3 offset) {
        return String.format("%.3f, %.3f, %.3f", offset.x(), offset.y(), offset.z());
    }

    private static void overlay(LocalPlayer player, String key, ChatFormatting formatting, Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args).withStyle(formatting));
    }
}
