package dev.marblegate.superpipeslide.client.fullmap.model.search;

import dev.marblegate.superpipeslide.client.fullmap.ui.DisplayNameStack;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record SearchResult(SearchKind kind, UUID id, ResourceKey<Level> levelKey, DisplayNameStack title, String subtitle) {}
