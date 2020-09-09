// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.entitySystem.Component;
import org.terasology.engine.world.block.RequiresBlockLifecycleEvents;

/**
 * The component that is added to an entity to allow it to produce a signal. The connection sides are the sides that the
 * signal can flow through. The signal strength can be either finite or infinite. Infinite is represented by -1, 0
 * represents no signal.
 */
@RequiresBlockLifecycleEvents
public class SignalProducerComponent implements Component {
    public byte connectionSides;
    public int signalStrength;
}
