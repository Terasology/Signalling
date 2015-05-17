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
import org.terasology.computer.module.ComputerDirection;
import org.terasology.computer.system.server.lang.AbstractModuleMethodExecutable;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Direction;
import org.terasology.math.SideBitFlag;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalProducerComponent;

import java.util.List;
import java.util.Map;

public class EmitSignalMethod extends AbstractModuleMethodExecutable<Object> {
    private String methodName;

    public EmitSignalMethod(String methodName) {
        super("Emits signal from the specified sides only.");
        this.methodName = methodName;

        addParameter("directions", "Array of Direction", "Directions from which to emit signal.");
        addParameter("value", "Number", "Value of the signal - any positive number for signal distance, or -1 for unlimited, 0 to reset signal.");
    }

    @Override
    public int getCpuCycleDuration() {
        return 100;
    }

    @Override
    public int getMinimumExecutionTime(int line, ComputerCallback computer, Map<String, Variable> parameters) throws ExecutionException {
        return 100;
    }

    @Override
    public Object onFunctionEnd(int line, ComputerCallback computer, Map<String, Variable> parameters, Object onFunctionStartResult) throws ExecutionException {
        final Variable conditionsVar = parameters.get("directions");
        if (conditionsVar.getType() != Variable.Type.LIST) {
            throw new ExecutionException(line, "Expected an Array of Directions in " + methodName + "()");
        }

        List<Variable> directions = (List<Variable>) conditionsVar.getValue();

        byte value = 0;
        for (Variable directionVar : directions) {
            Direction direction = ComputerDirection.getDirection((String) directionVar.getValue());
            if (direction == null) {
                throw new ExecutionException(line, "Invalid directions in " + methodName + "()");
            }
            value = SideBitFlag.addSide(value, direction.toSide());
        }

        int signalStrength = FunctionParamValidationUtil.validateIntParameter(line, parameters, "value", methodName);
        if (signalStrength < -1) {
            throw new ExecutionException(line, "Invalid value in " + methodName + "()");
        }

        if (signalStrength == 0) {
            // This means "reset", no matter which sides are passed, so no producer is needed on those sides
            value = 0;
        }

        EntityRef computerEntity = computer.getComputerEntity();
        SignalConsumerComponent consumerComponent = computerEntity.getComponent(SignalConsumerComponent.class);
        consumerComponent.connectionSides = 0;
        computerEntity.saveComponent(consumerComponent);

        SignalProducerComponent producerComponent = computerEntity.getComponent(SignalProducerComponent.class);
        producerComponent.connectionSides = value;
        producerComponent.signalStrength = signalStrength;

        computerEntity.saveComponent(producerComponent);

        consumerComponent.connectionSides = (byte) (63 - value);
        computerEntity.saveComponent(consumerComponent);

        return null;
    }
}
