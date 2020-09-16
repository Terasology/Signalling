// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.computer.module;

import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.math.Direction;
import org.terasology.engine.math.SideBitFlag;
import org.terasology.modularcomputers.FunctionParamValidationUtil;
import org.terasology.modularcomputers.context.ComputerCallback;
import org.terasology.modularcomputers.module.ComputerDirection;
import org.terasology.modularcomputers.shadedlibs.com.gempukku.lang.ExecutionException;
import org.terasology.modularcomputers.shadedlibs.com.gempukku.lang.Variable;
import org.terasology.modularcomputers.system.server.lang.AbstractModuleMethodExecutable;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalProducerComponent;

import java.util.List;
import java.util.Map;

/**
 * Defines a computer module method that emits a signal from the specified sides.
 * <p>
 * Parameters: directions Type: Array of Direction Description: Directions from which to emit the signal.
 * <p>
 * values Type: Number Description: Value of the signal - any positive number for signal distance, or -1 for unlimited,
 * 0 to reset signal.
 */
public class EmitSignalMethod extends AbstractModuleMethodExecutable<Object> {
    private final String methodName;

    public EmitSignalMethod(String methodName) {
        super("Emits signal from the specified sides only.");
        this.methodName = methodName;

        addParameter("directions", "Array of Direction", "Directions from which to emit signal.");
        addParameter("value", "Number", "Value of the signal - any positive number for signal distance, or -1 for " +
                "unlimited, 0 to reset signal.");
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
    public Object onFunctionEnd(int line, ComputerCallback computer, Map<String, Variable> parameters,
                                Object onFunctionStartResult) throws ExecutionException {
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
