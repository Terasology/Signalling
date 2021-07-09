// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.gestalt.entitysystem.component.Component;
/**
 * Responsible for keeping track of the time delay in a signal
 */
public class SignalTimeDelayComponent implements Component<SignalTimeDelayComponent> {
    /** delaySetting is the amount of time it is being delayed in milliseconds */
    public long delaySetting;

    @Override
    public void copy(SignalTimeDelayComponent other) {
        this.delaySetting = other.delaySetting;
    }
}
