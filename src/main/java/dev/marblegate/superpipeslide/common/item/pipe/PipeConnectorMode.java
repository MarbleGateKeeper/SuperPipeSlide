package dev.marblegate.superpipeslide.common.item.pipe;

import com.mojang.serialization.Codec;

import dev.marblegate.superpipeslide.common.core.geometry.CurveType;
import net.minecraft.util.StringRepresentable;

public enum PipeConnectorMode implements StringRepresentable {
    LINE("line", CurveType.LINE),
    AUTO_CURVE("auto_curve", CurveType.AUTO_CURVE),
    GAZE_CURVE("gaze_curve", CurveType.GAZE_CURVE),
    CONTROLLED("controlled", CurveType.CONTROLLED);

    public static final Codec<PipeConnectorMode> CODEC = StringRepresentable.fromEnum(PipeConnectorMode::values);

    private final String serializedName;
    private final CurveType curveType;

    PipeConnectorMode(String serializedName, CurveType curveType) {
        this.serializedName = serializedName;
        this.curveType = curveType;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public CurveType curveType() {
        return this.curveType;
    }

    public PipeConnectorMode next() {
        PipeConnectorMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

}
