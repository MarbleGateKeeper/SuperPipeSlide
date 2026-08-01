package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;

/**
 * A placed station label. {@code x/z} is the top-left corner of the solver-side candidate
 * box in layout space and is still used for view culling and export bounds, but the
 * authoritative placement is {@code slot}: the renderer re-projects the slot relative to
 * the node's screen position every capture, keeping the chosen side stable across zoom.
 */
public record VisualLabel(NodeId nodeId, String text, double x, double z, int priority, double scale, boolean fallback, LabelSlot slot) {}
