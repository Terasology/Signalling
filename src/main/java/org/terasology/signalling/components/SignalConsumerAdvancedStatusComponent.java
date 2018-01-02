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

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps track of the signal strength on each side of an entity
 * Used in SetResetGate
 */
public class SignalConsumerAdvancedStatusComponent implements Component {
    /**
     * To get the String representing the side, use BlockNetworkUtil.getResultSide(Block, Side).name()
     * -1 is infinite signal, 0 is no signal
     */
    public Map<String, Integer> signalStrengths = new HashMap<>();
}
