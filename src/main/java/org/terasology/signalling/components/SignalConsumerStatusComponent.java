// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.entitySystem.Component;

/**
 * Keeps track of whether or not the entity is recieving a signal.
 */
public class SignalConsumerStatusComponent implements Component {
    public boolean hasSignal;
}
