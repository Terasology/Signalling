// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.world.block.ForceBlockActive;
import org.terasology.gestalt.entitysystem.component.EmptyComponent;

/**
 * Responsible for keeping track of whether a TimeDelay in a Signal has been modified.
 */
@ForceBlockActive
public class SignalTimeDelayModifiedComponent extends EmptyComponent<SignalTimeDelayModifiedComponent> {
}
