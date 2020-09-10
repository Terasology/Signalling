// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.entitySystem.Component;
import org.terasology.engine.world.block.RequiresBlockLifecycleEvents;
import org.terasology.nui.reflection.MappedContainer;

import java.util.Set;

/**
 * A component that indicates the {@link EntityRef} it is attached to is a signal conductor. Holds information about
 * conductor's input / output sides.
 */
@RequiresBlockLifecycleEvents
public class SignalConductorComponent implements Component {
    public Set<ConnectionGroup> connectionGroups;

    /**
     * Maps a signal conductor entity's input / output sides.
     */
    @MappedContainer
    public static class ConnectionGroup {
        public byte inputSides;
        public byte outputSides;
    }
}
