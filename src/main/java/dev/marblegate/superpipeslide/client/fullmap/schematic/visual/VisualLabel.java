package dev.marblegate.superpipeslide.client.fullmap.schematic.visual;

import dev.marblegate.superpipeslide.client.fullmap.model.NodeId;

public record VisualLabel(NodeId nodeId, String text, double x, double z, int priority, double scale, boolean fallback) {}
