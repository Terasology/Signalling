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
package org.terasology.signalling.computer.module;

import com.gempukku.lang.ExecutionException;
import com.gempukku.lang.Variable;
import org.terasology.computer.FunctionParamValidationUtil;
import org.terasology.computer.context.ComputerCallback;
import org.terasology.computer.system.server.lang.AbstractModuleMethodExecutable;
import org.terasology.engine.math.Direction;
import org.terasology.signalling.components.SignalConsumerAdvancedStatusComponent;

import java.util.Map;

/**
 * Defines a computer module method that reads a signal from the specified side.
 *
 * Parameters:
 *  direction
 *  Type: Direction
 *  Description: Direction from which to read the signal.
 *
 * Returns:
 *  Type: Number
 *  Description: Signal strength on that side: 0 means no signal on that side, -1 means infinite.
 */
public class ReadSignalMethod extends AbstractModuleMethodExecutable<Object> {
    private String methodName;

    public ReadSignalMethod(String methodName) {
        super("Reads signal from the specified side.<l>Note, that you cannot read a signal from a side that this computer is emitting signal on.", "Number", "Signal strength on that side: 0 means no signal on that side, -1 means infinite.");
        this.methodName = methodName;

        addParameter("direction", "Direction", "Direction from which to read the signal.");
    }

    @Override
    public int getCpuCycleDuration() {
        return 50;
    }

    @Override
    public Object onFunctionEnd(int line, ComputerCallback computer, Map<String, Variable> parameters, Object onFunctionStartResult) throws ExecutionException {
        Direction direction = FunctionParamValidationUtil.validateDirectionParameter(line, parameters, "direction", methodName);

        SignalConsumerAdvancedStatusComponent component = computer.getComputerEntity().getComponent(SignalConsumerAdvancedStatusComponent.class);
        Integer strength = component.signalStrengths.get(direction.toSide().name());
        if (strength == null) {
            return 0;
        } else {
            return strength;
        }
    }
}
