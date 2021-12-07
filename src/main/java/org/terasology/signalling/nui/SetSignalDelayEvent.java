// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.nui;

import org.terasology.engine.network.NetworkEvent;
import org.terasology.engine.network.ServerEvent;

@ServerEvent
public class SetSignalDelayEvent extends NetworkEvent {
    private long time;

    /**
     * Creates a new event from the NetworkEvent constructor.
     */
    public SetSignalDelayEvent() {
    }

    /**
     * Sets the amount of time this event will delay the signal by.
     * @param time The time in milliseconds to delay the signal by.
     */
    public SetSignalDelayEvent(long time) {
        this.time = time;
    }

    /**
     * @return the amount of time in milliseconds
     */
    public long getTime() {
        return time;
    }
}
