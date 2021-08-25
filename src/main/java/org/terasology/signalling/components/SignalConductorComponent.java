// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.world.block.RequiresBlockLifecycleEvents;
import org.terasology.gestalt.entitysystem.component.Component;
import org.terasology.reflection.MappedContainer;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A component that indicates the {@link EntityRef} it is attached to is a signal conductor.
 * Holds information about conductor's input / output sides.
 */
@RequiresBlockLifecycleEvents
public class SignalConductorComponent implements Component<SignalConductorComponent> {
    public Set<ConnectionGroup> connectionGroups;

    @Override
    public void copyFrom(SignalConductorComponent other) {
        this.connectionGroups = other.connectionGroups.stream()
                .map(ConnectionGroup::copy)
                .collect(Collectors.toSet());
    }

    /**
     * Maps a signal conductor entity's input / output sides.
     */
    @MappedContainer
    public static class ConnectionGroup {
        public byte inputSides;
        public byte outputSides;

        ConnectionGroup copy() {
            ConnectionGroup newCG = new ConnectionGroup();
            newCG.inputSides = this.inputSides;
            newCG.outputSides = this.outputSides;
            return newCG;
        }
    }
}
