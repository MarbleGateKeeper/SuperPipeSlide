package dev.marblegate.superpipeslide.integration.iris.client;

import dev.marblegate.superpipeslide.client.renderer.fold.ClientFoldTraversalPostEffectRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.vertices.ImmediateState;

public final class IrisFoldTraversalPostEffectExtension implements ClientFoldTraversalPostEffectRenderer.FoldTraversalPostEffectExtension {
    @Override
    public boolean deferUntilExternalFinalPass() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    @Override
    public Scope pipelineOverrideScope() {
        boolean wasBypass = ImmediateState.bypass;
        ImmediateState.bypass = true;
        return () -> ImmediateState.bypass = wasBypass;
    }
}
