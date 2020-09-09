// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.computer.module;

import org.terasology.computer.system.common.ComputerModuleRegistry;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.config.ModuleConfigManager;
import org.terasology.engine.registry.In;

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
