// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.entitySystem.Component;
import org.terasology.engine.world.block.RequiresBlockLifecycleEvents;

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
