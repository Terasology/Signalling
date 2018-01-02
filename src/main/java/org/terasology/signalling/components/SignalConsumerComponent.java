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
package org.terasology.signalling.components;

import org.terasology.entitySystem.Component;
import org.terasology.math.Side;
import org.terasology.world.block.RequiresBlockLifecycleEvents;

import java.util.Set;

/**
 * Indicates that an Entity can recieve a Signal
 */
@RequiresBlockLifecycleEvents
public class SignalConsumerComponent implements Component {
    //Represents which sides can be connected to, use the SideBitFlag class to interpret this value
    public byte connectionSides;
    //Represents the operation used for a logic gate
    public SignalConsumerComponent.Mode mode = Mode.AT_LEAST_ONE;
    
    public enum Mode {
        AT_LEAST_ONE, ALL_CONNECTED, EXACTLY_ONE, SPECIAL
    }
}
