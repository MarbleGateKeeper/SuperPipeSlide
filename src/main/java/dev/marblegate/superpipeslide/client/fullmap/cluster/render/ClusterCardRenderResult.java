package dev.marblegate.superpipeslide.client.fullmap.cluster.render;

import dev.marblegate.superpipeslide.client.fullmap.cluster.hit.ClusterCardHit;
import dev.marblegate.superpipeslide.client.gui.base.SPSGui;

public record ClusterCardRenderResult(SPSGui.Rect mapBounds, SPSGui.Rect fitViewportBounds, ClusterCardHit hover) {}
