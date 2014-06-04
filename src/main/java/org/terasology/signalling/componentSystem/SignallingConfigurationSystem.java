package org.terasology.signalling.componentSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.registry.In;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.signalling.components.SignalTimeDelayComponent;
import org.terasology.signalling.nui.DelayConfigurationScreen;
import org.terasology.world.block.BlockComponent;

/**
 * @author Marcin Sciesinski <marcins78@gmail.com>
 */
@RegisterSystem(RegisterMode.CLIENT)
public class SignallingConfigurationSystem extends BaseComponentSystem {
    private static final Logger logger = LoggerFactory.getLogger(SignallingConfigurationSystem.class);
    @In
    private NUIManager nuiManager;

    @ReceiveEvent(components = {BlockComponent.class, SignalTimeDelayComponent.class})
    public void openDelayConfiguration(ActivateEvent event, EntityRef entity) {
        nuiManager.toggleScreen("signalling:delayConfigurationScreen");
        DelayConfigurationScreen layer = (DelayConfigurationScreen)nuiManager.getScreen("signalling:delayConfigurationScreen");
        layer.attachToEntity("Delay configuration", entity);
    }
}
