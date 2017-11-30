/*
 * Copyright 2014 MovingBlocks
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
package org.terasology.signalling.components;

import org.terasology.entitySystem.Component;
import org.terasology.math.Side;
import org.terasology.world.block.RequiresBlockLifecycleEvents;

import java.util.Set;
/**
 * The component that is added to an entity to allow it to produce a signal.
 * The connection sides are the sides that the signal can flow through.
 * The signal strength can be either finite or infinite.
 * Infinite is represented by -1, 0 represents no signal.
 */
@RequiresBlockLifecycleEvents
public class SignalProducerComponent implements Component {
    public byte connectionSides;
    public int signalStrength;
}
