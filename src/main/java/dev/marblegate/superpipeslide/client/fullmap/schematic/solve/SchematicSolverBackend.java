package dev.marblegate.superpipeslide.client.fullmap.schematic.solve;

import dev.marblegate.superpipeslide.client.fullmap.schematic.SchematicLayoutConfig;
import dev.marblegate.superpipeslide.client.fullmap.schematic.model.SchematicInputGraph;
import java.util.Optional;

public interface SchematicSolverBackend {
    SchematicLayoutResult solve(SchematicInputGraph input, SchematicLayoutConfig config, Optional<VisualRouteMapGraphSnapshot> previous);
}
