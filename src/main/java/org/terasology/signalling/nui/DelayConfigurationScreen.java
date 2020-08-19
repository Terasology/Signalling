// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.nui;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.rendering.nui.CoreScreenLayer;
import org.terasology.nui.UIWidget;
import org.terasology.nui.WidgetUtil;
import org.terasology.nui.widgets.ActivateEventListener;
import org.terasology.nui.widgets.UILabel;
import org.terasology.nui.widgets.UIText;
import org.terasology.signalling.components.SignalTimeDelayComponent;

/**
 * Created by adeon on 03.06.14.
 */
public class DelayConfigurationScreen extends CoreScreenLayer {
    private static final long MINIMUM_DELAY = 500;
    private static final long DELAY_STEP = 100;

    private EntityRef blockEntity;

    private long timeMs;

    /**
    * Initializes the program.
    */
    @Override
    public void initialise() {
        WidgetUtil.trySubscribe(this, "delay-decrease", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                decrease();
            }
        });
        WidgetUtil.trySubscribe(this, "delay-increase", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                increase();
            }
        });
    }
    
    /**
    * Method sets UILabel called label equal to title parameter. If the entity's signal time delay component is not null,
    * it sets timeMS to timeDelay.delaySetting.
    *
    * @param title The title associated the label.
    * @param entity The entity being attached.
    */
    public void attachToEntity(String title, EntityRef entity) {
        this.blockEntity = entity;
        UIText time = find("delay-value", UIText.class);
        UILabel label = find("delay-title", UILabel.class);
        label.setText(title);
        SignalTimeDelayComponent timeDelay = entity.getComponent(SignalTimeDelayComponent.class);
        if (timeDelay != null) {
            timeMs = timeDelay.delaySetting;
            time.setText(String.valueOf(timeMs) + "ms");
        }
    }

    /**
    * Sets the time to a specified value.
    *
    * @param timeToSet the time that should be set.
    */
    private void setTime(long timeToSet) {
        timeMs = timeToSet;
        UIText time = find("delay-value", UIText.class);
        time.setText(String.valueOf(timeMs) + "ms");
        this.blockEntity.send(new SetSignalDelayEvent(timeMs));
    }
    
    /**
    * Increases the time by DELAY_STEP.
    */
    private void increase() {
        setTime(timeMs + DELAY_STEP);
    }
    
    /**
    * This method decreases the time by comparing MINIMUM_DELAY and timeMS - DELAY_STEP. If the MINIMUM_DELAY value is greater,
    * time is set to MINIMUM_DELAY. If timeMS - DELAY_STEP is greater, time is set to timeMS - DELAY_STEP.
    */
    private void decrease() {
        setTime(Math.max(MINIMUM_DELAY, timeMs - DELAY_STEP));
    }
}
