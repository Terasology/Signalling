// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.componentSystem;

import org.terasology.engine.entitySystem.entity.EntityRef;

/**
 * A callback that is notified when an {@link EntityRef}'s state changes.
 */
public interface GateSignalChangeHandler {
    /**
     * Called when gate's incoming signal has been changed.
     *
     * @param entity The entity undergoing a gate signal change
     */
    void handleGateSignalChange(EntityRef entity);

    /**
     * Called when delayed trigger event is being called for this gate with the specified actionId.
     *
     * @param actionId The type of action the entity has performed
     * @param entity The entity performing the action
     */
    void handleDelayedTrigger(String actionId, EntityRef entity);
}
