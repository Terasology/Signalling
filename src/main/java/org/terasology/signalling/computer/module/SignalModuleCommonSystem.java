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

import org.terasology.computer.system.common.ComputerModuleRegistry;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.config.ModuleConfigManager;
import org.terasology.registry.In;

@RegisterSystem(RegisterMode.ALWAYS)
public class SignalModuleCommonSystem extends BaseComponentSystem {
    public static final String COMPUTER_SIGNALLING_MODULE_TYPE = "Signalling";

    @In
    private ComputerModuleRegistry computerModuleRegistry;
    @In
    private ModuleConfigManager moduleConfigManager;

    @Override
    public void preBegin() {
        if (moduleConfigManager.getBooleanVariable("Signalling", "registerModule.signalling", true)) {
            computerModuleRegistry.registerComputerModule(
                    COMPUTER_SIGNALLING_MODULE_TYPE,
                    new SignallingComputerModule(COMPUTER_SIGNALLING_MODULE_TYPE, "Signalling"),
                    "This module allows to interact with Signalling networks.",
                    null);
        }
    }

}
