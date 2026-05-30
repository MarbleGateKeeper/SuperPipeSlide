package dev.marblegate.superpipeslide.client.fullmap.model;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MapTransferHint(String id, ResourceKey<Level> levelKey, NodeId from, NodeId to, double distance) {}
