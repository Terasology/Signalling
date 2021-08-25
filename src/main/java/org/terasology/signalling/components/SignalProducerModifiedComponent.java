// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.world.block.ForceBlockActive;
import org.terasology.gestalt.entitysystem.component.EmptyComponent;

/**
 * Responsible for keeping track of whether a signal producer has been modified.
 * The presence or abscence of this component is used to store this data.
 */
@ForceBlockActive
public class SignalProducerModifiedComponent extends EmptyComponent<SignalProducerModifiedComponent> {
}
