// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.entitySystem.Component;

/**
 * Responsible for keeping track of the time delay in a signal
 */
public class SignalTimeDelayComponent implements Component {
    /**
     * delaySetting is the amount of time it is being delayed in milliseconds
     */
    public long delaySetting;
}
