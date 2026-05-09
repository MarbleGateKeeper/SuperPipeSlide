package dev.marblegate.superpipeslide.common.core.appearance.model;


import dev.marblegate.superpipeslide.common.core.appearance.coating.PipeCoatingSelection;
import dev.marblegate.superpipeslide.common.core.appearance.material.MaterialSlotDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.storage.PipeAppearanceDefinitions;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeStyleDefinition;
import dev.marblegate.superpipeslide.common.core.appearance.style.PipeVariantDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PipeAppearanceProfile(
        int profileId,
        String styleId,
        String variantId,
        boolean glow,
        Map<String, PipeCoatingSelection> slotCoatings,
        Map<String, Double> styleParameters
) {
    private static final int MAX_SLOTS = 16;
    private static final int MAX_PARAMETERS = 16;
    public static final int DEFAULT_PROFILE_ID = 0;
    public static final String DEFAULT_STYLE_ID = "round_pipe";
    public static final String DEFAULT_VARIANT_ID = "round_basic";

    public static final Codec<Map<String, PipeCoatingSelection>> SLOT_COATINGS_CODEC = Codec.unboundedMap(Codec.STRING, PipeCoatingSelection.CODEC)
            .xmap(LinkedHashMap::new, LinkedHashMap::new);
    public static final Codec<Map<String, Double>> STYLE_PARAMETERS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
            .xmap(LinkedHashMap::new, LinkedHashMap::new);

    public static final Codec<PipeAppearanceProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("profile_id", DEFAULT_PROFILE_ID).forGetter(PipeAppearanceProfile::profileId),
            Codec.STRING.optionalFieldOf("style", DEFAULT_STYLE_ID).forGetter(PipeAppearanceProfile::styleId),
            Codec.STRING.optionalFieldOf("variant", DEFAULT_VARIANT_ID).forGetter(PipeAppearanceProfile::variantId),
            Codec.BOOL.optionalFieldOf("glow", false).forGetter(PipeAppearanceProfile::glow),
            SLOT_COATINGS_CODEC.optionalFieldOf("slot_coatings", Map.of()).forGetter(PipeAppearanceProfile::slotCoatings),
            STYLE_PARAMETERS_CODEC.optionalFieldOf("style_parameters", Map.of()).forGetter(PipeAppearanceProfile::styleParameters)
    ).apply(instance, PipeAppearanceProfile::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipeAppearanceProfile> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PipeAppearanceProfile decode(RegistryFriendlyByteBuf buffer) {
            int profileId = buffer.readVarInt();
            String styleId = buffer.readUtf();
            String variantId = buffer.readUtf();
            boolean glow = buffer.readBoolean();
            int slotCount = Math.min(MAX_SLOTS, buffer.readVarInt());
            Map<String, PipeCoatingSelection> slotCoatings = new LinkedHashMap<>();
            for (int i = 0; i < slotCount; i++) {
                slotCoatings.put(buffer.readUtf(), PipeCoatingSelection.STREAM_CODEC.decode(buffer));
            }
            int parameterCount = Math.min(MAX_PARAMETERS, buffer.readVarInt());
            Map<String, Double> styleParameters = new LinkedHashMap<>();
            for (int i = 0; i < parameterCount; i++) {
                styleParameters.put(buffer.readUtf(), buffer.readDouble());
            }
            return new PipeAppearanceProfile(profileId, styleId, variantId, glow, slotCoatings, styleParameters);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PipeAppearanceProfile profile) {
            buffer.writeVarInt(profile.profileId());
            buffer.writeUtf(profile.styleId());
            buffer.writeUtf(profile.variantId());
            buffer.writeBoolean(profile.glow());
            Map<String, PipeCoatingSelection> slotCoatings = profile.slotCoatings();
            buffer.writeVarInt(Math.min(MAX_SLOTS, slotCoatings.size()));
            int written = 0;
            for (Map.Entry<String, PipeCoatingSelection> entry : slotCoatings.entrySet()) {
                if (written++ >= MAX_SLOTS) {
                    break;
                }
                buffer.writeUtf(entry.getKey());
                PipeCoatingSelection.STREAM_CODEC.encode(buffer, entry.getValue());
            }
            Map<String, Double> styleParameters = profile.styleParameters();
            buffer.writeVarInt(Math.min(MAX_PARAMETERS, styleParameters.size()));
            written = 0;
            for (Map.Entry<String, Double> entry : styleParameters.entrySet()) {
                if (written++ >= MAX_PARAMETERS) {
                    break;
                }
                buffer.writeUtf(entry.getKey());
                buffer.writeDouble(entry.getValue());
            }
        }
    };

    public PipeAppearanceProfile {
        styleId = normalizeId(styleId, DEFAULT_STYLE_ID);
        variantId = normalizeId(variantId, DEFAULT_VARIANT_ID);
        slotCoatings = normalizeSlots(slotCoatings);
        styleParameters = normalizeRawParameters(styleParameters);
    }

    public static PipeAppearanceProfile defaultProfile() {
        return new PipeAppearanceProfile(DEFAULT_PROFILE_ID, DEFAULT_STYLE_ID, DEFAULT_VARIANT_ID, false, Map.of("body", PipeCoatingSelection.defaultSelection()), Map.of()).normalizedToDefinitions();
    }

    public PipeAppearanceProfile withProfileId(int profileId) {
        return new PipeAppearanceProfile(profileId, this.styleId, this.variantId, this.glow, this.slotCoatings, this.styleParameters);
    }

    public PipeAppearanceProfile withoutServerId() {
        return this.withProfileId(DEFAULT_PROFILE_ID);
    }

    public PipeAppearanceProfile normalizedToDefinitions() {
        PipeStyleDefinition style = PipeAppearanceDefinitions.style(this.styleId).orElse(PipeAppearanceDefinitions.defaultStyle());
        PipeVariantDefinition variant = PipeAppearanceDefinitions.variant(this.variantId)
                .filter(candidate -> candidate.styleId().equals(style.id()))
                .orElseGet(() -> PipeAppearanceDefinitions.variant(style.defaultVariantId()).orElse(PipeAppearanceDefinitions.defaultVariant()));
        Map<String, PipeCoatingSelection> normalizedSlots = new LinkedHashMap<>();
        for (MaterialSlotDefinition slot : PipeAppearanceDefinitions.slotsFor(style, variant)) {
            PipeCoatingSelection selection = this.slotCoatings.getOrDefault(slot.id(), PipeAppearanceDefinitions.defaultSelectionForSlot(slot.id()));
            normalizedSlots.put(slot.id(), PipeAppearanceDefinitions.normalizeSelection(selection, slot.id()));
        }
        Map<String, Double> normalizedParameters = style.normalizeParameters(this.styleParameters);
        return new PipeAppearanceProfile(this.profileId, style.id(), variant.id(), this.glow, normalizedSlots, normalizedParameters);
    }

    public boolean isDefaultAppearance() {
        return this.normalizedToDefinitions().contentEquals(defaultProfile().normalizedToDefinitions());
    }

    public boolean contentEquals(PipeAppearanceProfile other) {
        return other != null
                && Objects.equals(this.styleId, other.styleId)
                && Objects.equals(this.variantId, other.variantId)
                && this.glow == other.glow
                && Objects.equals(this.slotCoatings, other.slotCoatings)
                && Objects.equals(this.styleParameters, other.styleParameters);
    }

    public String contentKey() {
        StringBuilder builder = new StringBuilder(this.styleId)
                .append('|').append(this.variantId)
                .append("|glow=").append(this.glow);
        PipeAppearanceDefinitions.style(this.styleId).ifPresent(style -> style.parameters()
                .forEach(parameter -> builder.append('|').append(parameter.id()).append('=').append(this.styleParameters.getOrDefault(parameter.id(), parameter.defaultValue()))));
        this.slotCoatings.forEach((slot, coating) -> builder.append('|').append(slot).append('=').append(coating.contentKey()));
        return builder.toString();
    }

    private static String normalizeId(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static Map<String, PipeCoatingSelection> normalizeSlots(Map<String, PipeCoatingSelection> slots) {
        if (slots == null || slots.isEmpty()) {
            return Map.of();
        }
        Map<String, PipeCoatingSelection> normalized = new LinkedHashMap<>();
        slots.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .limit(MAX_SLOTS)
                .forEach(entry -> {
                    String key = entry.getKey().trim();
                    if (!key.isEmpty()) {
                        normalized.put(key, entry.getValue());
                    }
                });
        return Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }

    private static Map<String, Double> normalizeRawParameters(Map<String, Double> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        parameters.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && Double.isFinite(entry.getValue()))
                .limit(MAX_PARAMETERS)
                .forEach(entry -> {
                    String key = entry.getKey().trim();
                    if (!key.isEmpty()) {
                        normalized.put(key, entry.getValue());
                    }
                });
        return Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }
}
