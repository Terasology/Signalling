// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.componentSystem;

import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.event.ReceiveEvent;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.common.ActivateEvent;
import org.terasology.engine.registry.In;
import org.terasology.engine.rendering.nui.NUIManager;
import org.terasology.engine.world.block.BlockComponent;
import org.terasology.signalling.components.SignalTimeDelayComponent;
import org.terasology.signalling.nui.DelayConfigurationScreen;

/**
 * A marker class that indicates a system that handles signalling configurations.
 */
@RegisterSystem(RegisterMode.CLIENT)
public class SignallingConfigurationSystem extends BaseComponentSystem {
    @In
    private NUIManager nuiManager;

    /**
     * Attaches the given entity to this system's screen.
     *
     * @param entity An {@link EntityRef} to attach
     */
    @ReceiveEvent(components = {BlockComponent.class, SignalTimeDelayComponent.class})
    public void openDelayConfiguration(ActivateEvent event, EntityRef entity) {
        nuiManager.toggleScreen("signalling:delayConfigurationScreen");
        DelayConfigurationScreen layer = (DelayConfigurationScreen) nuiManager.getScreen("signalling" +
                ":delayConfigurationScreen");
        layer.attachToEntity("Delay configuration", entity);
    }
}
