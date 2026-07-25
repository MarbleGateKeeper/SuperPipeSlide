package dev.marblegate.superpipeslide.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("detached")
    boolean superpipeslide$isDetached();

    @Accessor("detached")
    void superpipeslide$setDetached(boolean detached);

    @Invoker("setPosition")
    void superpipeslide$invokeSetPosition(net.minecraft.world.phys.Vec3 position);

    @Invoker("setRotation")
    void superpipeslide$invokeSetRotation(float yaw, float pitch, float roll);
}
