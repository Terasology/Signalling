// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.world.block.RequiresBlockLifecycleEvents;
import org.terasology.gestalt.entitysystem.component.Component;

/**
 * Indicates that an Entity can recieve a Signal
 */
@RequiresBlockLifecycleEvents
public class SignalConsumerComponent implements Component<SignalConsumerComponent> {
    //Represents which sides can be connected to, use the SideBitFlag class to interpret this value
    public byte connectionSides;
    //Represents the operation used for a logic gate
    public SignalConsumerComponent.Mode mode = Mode.AT_LEAST_ONE;

    @Override
    public void copy(SignalConsumerComponent other) {
        this.connectionSides = other.connectionSides;
        this.mode = other.mode;
    }

    public enum Mode {
        AT_LEAST_ONE, ALL_CONNECTED, EXACTLY_ONE, SPECIAL
    }
}
