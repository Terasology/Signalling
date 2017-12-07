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
package org.terasology.signalling.components;

import com.google.common.collect.Lists;
import org.terasology.entitySystem.Component;
import org.terasology.math.Side;

import java.util.List;

/**
 * A Component that adds logic gate functionality to an Entity.
 * The gateType determines the function, and can either be AND, OR, XOR, or NAND.
 * functionalSides represents the sides that can affect the gate. There is only one output side, the rest can be input.
 */
public class SignalGateComponent implements Component {
    public String gateType;
    public List<Side> functionalSides = Lists.newArrayList();
}
