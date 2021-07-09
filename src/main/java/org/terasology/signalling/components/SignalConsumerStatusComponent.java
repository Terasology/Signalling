// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.gestalt.entitysystem.component.Component;

/**
 * Keeps track of whether or not the entity is recieving a signal.
 */
public class SignalConsumerStatusComponent implements Component<SignalConsumerStatusComponent> {
    public boolean hasSignal;

    @Override
    public void copy(SignalConsumerStatusComponent other) {
        this.hasSignal = other.hasSignal;
    }
}
