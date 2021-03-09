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

import org.terasology.engine.network.NetworkEvent;
import org.terasology.engine.network.ServerEvent;

/**
 * @author Marcin Sciesinski <marcins78@gmail.com>
 */
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
