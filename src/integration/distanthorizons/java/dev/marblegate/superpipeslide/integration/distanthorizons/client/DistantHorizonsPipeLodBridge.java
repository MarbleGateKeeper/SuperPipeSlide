package dev.marblegate.superpipeslide.integration.distanthorizons.client;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class DistantHorizonsPipeLodBridge {
    private static final int MAX_MC_LIGHT = 15;
    private static final int NULL_CLIENT_LEVEL_CLEAR_TICKS = 40;
    private static final double NEAR_PROXY_HIDE_MARGIN = 16.0D;
    private static final Map<String, IDhApiLevelWrapper> PENDING_LEVELS = new LinkedHashMap<>();
    private static final Map<String, LevelState> LEVELS = new LinkedHashMap<>();
    private static boolean initialized;
    private static int nullClientLevelTicks;

    private DistantHorizonsPipeLodBridge() {}

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerEvent(DhApiLevelLoadEvent.class, new LevelLoadHandler());
        registerEvent(DhApiLevelUnloadEvent.class, new LevelUnloadHandler());
    }

    public static void tick() {
        if (!initialized) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            if (++nullClientLevelTicks >= NULL_CLIENT_LEVEL_CLEAR_TICKS) {
                clear();
            }
            return;
        }
        nullClientLevelTicks = 0;
        if (DhApi.Delayed.customRenderObjectFactory == null) {
            return;
        }

        registerPendingLevels();
        for (LevelState state : List.copyOf(LEVELS.values())) {
            state.refresh();
        }
    }

    public static void clear() {
        for (LevelState state : List.copyOf(LEVELS.values())) {
            state.clear();
        }
        LEVELS.clear();
        PENDING_LEVELS.clear();
        nullClientLevelTicks = 0;
        ClientPipeFarLodProxyProvider.clear();
    }

    private static <T extends com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent<?>> void registerEvent(Class<T> eventType, T handler) {
        DhApiResult<Void> result = DhApiEventRegister.on(eventType, handler);
        if (!result.success) {
            SuperPipeSlide.LOGGER.warn("Unable to register Distant Horizons event handler {}: {}", eventType.getSimpleName(), result.message);
        }
    }

    private static void onLevelLoad(IDhApiLevelWrapper levelWrapper) {
        Minecraft.getInstance().execute(() -> {
            String levelId = levelWrapper.getDhIdentifier();
            PENDING_LEVELS.put(levelId, levelWrapper);
            registerPendingLevels();
        });
    }

    private static void onLevelUnload(IDhApiLevelWrapper levelWrapper) {
        Minecraft.getInstance().execute(() -> {
            String levelId = levelWrapper.getDhIdentifier();
            PENDING_LEVELS.remove(levelId);
            LevelState state = LEVELS.remove(levelId);
            if (state != null) {
                state.clear();
            }
        });
    }

    private static void registerPendingLevels() {
        Iterator<Map.Entry<String, IDhApiLevelWrapper>> iterator = PENDING_LEVELS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, IDhApiLevelWrapper> entry = iterator.next();
            ResourceKey<Level> levelKey = resolveLevelKey(entry.getValue());
            if (levelKey == null) {
                continue;
            }

            LevelState previous = LEVELS.put(entry.getKey(), new LevelState(entry.getValue(), levelKey));
            if (previous != null) {
                previous.clear();
            }
            iterator.remove();
        }
    }

    @Nullable
    private static ResourceKey<Level> resolveLevelKey(IDhApiLevelWrapper levelWrapper) {
        Object wrappedLevel = levelWrapper.getWrappedMcObject();
        if (wrappedLevel instanceof ClientLevel clientLevel) {
            return clientLevel.dimension();
        }
        ClientLevel currentLevel = Minecraft.getInstance().level;
        return currentLevel == null ? null : currentLevel.dimension();
    }

    private static IDhApiRenderableBoxGroup createGroup(LevelState state, PipeFarLodGroup group) {
        IDhApiCustomRenderObjectFactory factory = DhApi.Delayed.customRenderObjectFactory;
        List<DhApiRenderableBox> boxes = new ArrayList<>(group.boxes().size());
        for (PipeFarLodBox box : group.boxes()) {
            boxes.add(new DhApiRenderableBox(
                    new DhApiVec3d(box.minX(), box.minY(), box.minZ()),
                    new DhApiVec3d(box.maxX(), box.maxY(), box.maxZ()),
                    color(box.argb()),
                    material(group.key().material())));
        }

        IDhApiRenderableBoxGroup dhGroup = factory.createAbsolutePositionedGroup(resourceLocation(state, group.key()), boxes);
        dhGroup.setSkyLight(MAX_MC_LIGHT);
        dhGroup.setBlockLight(group.key().emissive() ? MAX_MC_LIGHT : 0);
        dhGroup.setSsaoEnabled(!group.key().emissive());
        dhGroup.setShading(group.key().emissive()
                ? DhApiRenderableBoxGroupShading.getUnshaded()
                : DhApiRenderableBoxGroupShading.getDefaultShaded());
        dhGroup.setPreRenderFunc(ignored -> dhGroup.setActive(shouldRenderFarProxy(group.key())));
        return dhGroup;
    }

    private static boolean shouldRenderFarProxy(PipeFarLodGroupKey key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double renderRadius = Math.max(16.0D, minecraft.options.renderDistance().get() * 16.0D);
        double activationRadius = Math.max(16.0D, renderRadius - NEAR_PROXY_HIDE_MARGIN);
        double dx = key.centerX() - camera.x;
        double dz = key.centerZ() - camera.z;
        return dx * dx + dz * dz >= activationRadius * activationRadius;
    }

    private static String resourceLocation(LevelState state, PipeFarLodGroupKey key) {
        String dimensionHash = Integer.toUnsignedString(state.levelKey.identifier().toString().hashCode(), 36);
        return SuperPipeSlide.MODID + ":pipe_lod/" + dimensionHash + "/" + key.pathKey();
    }

    private static Color color(int argb) {
        return new Color(
                argb >> 16 & 0xFF,
                argb >> 8 & 0xFF,
                argb & 0xFF,
                argb >>> 24);
    }

    private static EDhApiBlockMaterial material(PipeFarLodMaterial material) {
        return switch (material) {
            case STONE -> EDhApiBlockMaterial.STONE;
            case WOOD -> EDhApiBlockMaterial.WOOD;
            case METAL -> EDhApiBlockMaterial.METAL;
            case WATER -> EDhApiBlockMaterial.WATER;
            case ILLUMINATED -> EDhApiBlockMaterial.ILLUMINATED;
        };
    }

    private static final class LevelState {
        private final ResourceKey<Level> levelKey;
        private final IDhApiCustomRenderRegister renderRegister;
        private Map<PipeFarLodGroupKey, RegisteredGroup> groups = new LinkedHashMap<>();
        private long networkRevision = Long.MIN_VALUE;
        private long appearanceRevision = Long.MIN_VALUE;

        private LevelState(IDhApiLevelWrapper levelWrapper, ResourceKey<Level> levelKey) {
            this.levelKey = levelKey;
            this.renderRegister = levelWrapper.getRenderRegister();
        }

        private void refresh() {
            Optional<PipeFarLodSnapshot> optionalSnapshot = ClientPipeFarLodProxyProvider.snapshot(this.levelKey);
            if (optionalSnapshot.isEmpty()) {
                return;
            }
            PipeFarLodSnapshot snapshot = optionalSnapshot.get();
            if (snapshot.networkRevision() == this.networkRevision && snapshot.appearanceRevision() == this.appearanceRevision) {
                return;
            }
            if (this.apply(snapshot)) {
                this.networkRevision = snapshot.networkRevision();
                this.appearanceRevision = snapshot.appearanceRevision();
            }
        }

        private boolean apply(PipeFarLodSnapshot snapshot) {
            Map<PipeFarLodGroupKey, RegisteredGroup> next = new LinkedHashMap<>();
            boolean success = true;
            for (PipeFarLodGroup group : snapshot.groups()) {
                RegisteredGroup previous = this.groups.remove(group.key());
                int contentHash = group.contentHash();
                if (previous != null && previous.contentHash() == contentHash) {
                    next.put(group.key(), previous);
                    continue;
                }
                if (previous != null) {
                    this.remove(previous.group());
                }

                try {
                    IDhApiRenderableBoxGroup dhGroup = createGroup(this, group);
                    this.renderRegister.add(dhGroup);
                    next.put(group.key(), new RegisteredGroup(dhGroup, contentHash));
                } catch (RuntimeException exception) {
                    success = false;
                    SuperPipeSlide.LOGGER.warn("Unable to register Distant Horizons pipe LOD group {}", group.key(), exception);
                }
            }

            for (RegisteredGroup stale : this.groups.values()) {
                this.remove(stale.group());
            }
            this.groups = next;
            return success;
        }

        private void clear() {
            for (RegisteredGroup group : this.groups.values()) {
                this.remove(group.group());
            }
            this.groups.clear();
            this.networkRevision = Long.MIN_VALUE;
            this.appearanceRevision = Long.MIN_VALUE;
        }

        private void remove(IDhApiRenderableBoxGroup group) {
            try {
                this.renderRegister.remove(group.getId());
            } catch (RuntimeException exception) {
                SuperPipeSlide.LOGGER.warn("Unable to remove Distant Horizons pipe LOD group {}", group.getId(), exception);
            }
        }
    }

    private record RegisteredGroup(IDhApiRenderableBoxGroup group, int contentHash) {}

    private static final class LevelLoadHandler extends DhApiLevelLoadEvent {
        @Override
        public void onLevelLoad(DhApiEventParam<EventParam> event) {
            DistantHorizonsPipeLodBridge.onLevelLoad(event.value.levelWrapper);
        }
    }

    private static final class LevelUnloadHandler extends DhApiLevelUnloadEvent {
        @Override
        public void onLevelUnload(DhApiEventParam<EventParam> event) {
            DistantHorizonsPipeLodBridge.onLevelUnload(event.value.levelWrapper);
        }
    }
}
