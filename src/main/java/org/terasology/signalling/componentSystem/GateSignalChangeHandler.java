/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.signalling.componentSystem;

import org.terasology.entitySystem.entity.EntityRef;

public interface GateSignalChangeHandler {
    /**
     * Called when gate's incoming signal has been changed.
     * @param entity
     * @return If this method returns a non-null value, it will be scheduled using DelayManager with that actionId.
     */
    void handleGateSignalChange(EntityRef entity);

    /**
     * Called when delayed trigger event is being called for this gate with the specified actionId.
     * @param actionId
     * @param entity
     */
    void handleDelayedTrigger(String actionId, EntityRef entity);
}
