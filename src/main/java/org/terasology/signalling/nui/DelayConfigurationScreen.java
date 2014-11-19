/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.signalling.nui;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.rendering.nui.CoreScreenLayer;
import org.terasology.rendering.nui.UIWidget;
import org.terasology.rendering.nui.WidgetUtil;
import org.terasology.rendering.nui.widgets.ActivateEventListener;
import org.terasology.rendering.nui.widgets.UILabel;
import org.terasology.rendering.nui.widgets.UIText;
import org.terasology.signalling.components.SignalTimeDelayComponent;

/**
 * Created by adeon on 03.06.14.
 */
public class DelayConfigurationScreen extends CoreScreenLayer {
    private static final long MINIMUM_DELAY = 500;
    private static final long DELAY_STEP = 100;

    private EntityRef blockEntity;

    private long timeMs;

    @Override
    protected void initialise() {
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

    private void setTime(long timeToSet) {
        timeMs = timeToSet;
        UIText time = find("delay-value", UIText.class);
        time.setText(String.valueOf(timeMs) + "ms");
        this.blockEntity.send(new SetSignalDelayEvent(timeMs));
    }

    private void increase() {
        setTime(timeMs + DELAY_STEP);
    }

    private void decrease() {
        setTime(Math.max(MINIMUM_DELAY, timeMs - DELAY_STEP));
    }
}
