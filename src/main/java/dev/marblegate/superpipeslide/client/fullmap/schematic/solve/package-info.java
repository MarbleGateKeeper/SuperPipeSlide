/**
 * Solver backends for the schematic pipeline. {@code HeuristicGlobalSolver} handles the
 * geographic-style modes with a bounded force-directed relaxation;
 * {@code MetroMapSchematicSolver} produces the order-preserving octilinear pure line
 * diagram. Backends plug into {@code FullRouteMapCache} through
 * {@code SchematicSolverBackend} and run on the single map builder thread.
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package dev.marblegate.superpipeslide.client.fullmap.schematic.solve;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import javax.annotation.ParametersAreNonnullByDefault;
