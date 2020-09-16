// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.computer.module;

import org.terasology.engine.math.Direction;
import org.terasology.modularcomputers.FunctionParamValidationUtil;
import org.terasology.modularcomputers.context.ComputerCallback;
import org.terasology.modularcomputers.shadedlibs.com.gempukku.lang.ExecutionException;
import org.terasology.modularcomputers.shadedlibs.com.gempukku.lang.Variable;
import org.terasology.modularcomputers.system.server.lang.AbstractModuleMethodExecutable;
import org.terasology.signalling.components.SignalConsumerAdvancedStatusComponent;

import java.util.Map;

/**
 * Defines a computer module method that reads a signal from the specified side.
 * <p>
 * Parameters: direction Type: Direction Description: Direction from which to read the signal.
 * <p>
 * Returns: Type: Number Description: Signal strength on that side: 0 means no signal on that side, -1 means infinite.
 */
public class ReadSignalMethod extends AbstractModuleMethodExecutable<Object> {
    private final String methodName;

    public ReadSignalMethod(String methodName) {
        super("Reads signal from the specified side.<l>Note, that you cannot read a signal from a side that this " +
                "computer is emitting signal on.", "Number", "Signal strength on that side: 0 means no signal on that" +
                " side, -1 means infinite.");
        this.methodName = methodName;

        addParameter("direction", "Direction", "Direction from which to read the signal.");
    }

    @Override
    public int getCpuCycleDuration() {
        return 50;
    }

    @Override
    public Object onFunctionEnd(int line, ComputerCallback computer, Map<String, Variable> parameters,
                                Object onFunctionStartResult) throws ExecutionException {
        Direction direction = FunctionParamValidationUtil.validateDirectionParameter(line, parameters, "direction",
                methodName);

        SignalConsumerAdvancedStatusComponent component =
                computer.getComputerEntity().getComponent(SignalConsumerAdvancedStatusComponent.class);
        Integer strength = component.signalStrengths.get(direction.toSide().name());
        if (strength == null) {
            return 0;
        } else {
            return strength;
        }
    }
}
