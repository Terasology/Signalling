// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.components;

import com.google.common.collect.Lists;
import org.terasology.engine.entitySystem.Component;
import org.terasology.engine.math.Side;

import java.util.List;

/**
 * A Component that adds logic gate functionality to an Entity. The gateType determines the function, and can either be
 * AND, OR, XOR, or NAND. functionalSides represents the sides that can affect the gate. There is only one output side,
 * the rest can be input.
 */
public class SignalGateComponent implements Component {
    public String gateType;
    public List<Side> functionalSides = Lists.newArrayList();
}
