package dev.marblegate.superpipeslide.integration.distanthorizons.client;

import java.util.Locale;

public record PipeFarLodGroupKey(int sectionX, int sectionY, int sectionZ,
                                 PipeFarLodMaterial material, boolean emissive,
                                 boolean translucent) {
    public PipeFarLodGroupKey {
        material = material == null ? PipeFarLodMaterial.STONE : material;
    }

    public String pathKey() {
        return this.sectionX + "/" + this.sectionY + "/" + this.sectionZ + "/" + this.material.name().toLowerCase(Locale.ROOT) + "/" + (this.emissive ? "emissive" : "lit") + "/" + (this.translucent ? "translucent" : "opaque");
    }

    public double centerX() {
        return (this.sectionX + 0.5D) * ClientPipeFarLodProxyProvider.GROUP_SECTION_SIZE;
    }

    public double centerY() {
        return (this.sectionY + 0.5D) * ClientPipeFarLodProxyProvider.GROUP_SECTION_SIZE;
    }

    public double centerZ() {
        return (this.sectionZ + 0.5D) * ClientPipeFarLodProxyProvider.GROUP_SECTION_SIZE;
    }
}
