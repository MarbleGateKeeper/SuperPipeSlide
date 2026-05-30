package dev.marblegate.superpipeslide.client.fullmap.cluster.visual;

import dev.marblegate.superpipeslide.client.fullmap.cluster.model.ClusterCardNode;
import dev.marblegate.superpipeslide.client.fullmap.model.geom.Vec2;

public record ClusterCardVisualNode(ClusterCardNode node, Vec2 position, int priority) {}
