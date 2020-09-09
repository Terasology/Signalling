// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import org.terasology.engine.entitySystem.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps track of the signal strength on each side of an entity Used in SetResetGate
 */
public class SignalConsumerAdvancedStatusComponent implements Component {
    /**
     * To get the String representing the side, use BlockNetworkUtil.getResultSide(Block, Side).name() -1 is infinite
     * signal, 0 is no signal
     */
    public Map<String, Integer> signalStrengths = new HashMap<>();
}
