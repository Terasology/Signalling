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
import org.terasology.math.Direction;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.signalling.components.SignalConsumerAdvancedStatusComponent;

import java.util.EnumSet;
import java.util.Map;

public class ReadSignalMethod extends AbstractModuleMethodExecutable<Object> {
    private String methodName;

    public ReadSignalMethod(String methodName) {
        super("Reads signal from the specified side.", "Boolean", "If there is a signal coming in from the specified direction.");
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
        EnumSet<Side> signals = SideBitFlag.getSides(component.sidesWithSignals);

        return signals.contains(direction.toSide());
    }
}
