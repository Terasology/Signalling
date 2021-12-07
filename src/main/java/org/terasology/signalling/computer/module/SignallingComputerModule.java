// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.computer.module;

import org.terasology.computer.module.DefaultComputerModule;
import org.terasology.computer.system.server.lang.ComputerModule;

import java.util.Collection;

public class SignallingComputerModule extends DefaultComputerModule {
    private String moduleType;

    public SignallingComputerModule(String moduleType, String moduleName) {
        super(moduleType, moduleName);
        this.moduleType = moduleType;

        addMethod("readSignal", new ReadSignalMethod("readSignal"));
        addMethod("emitSignal", new EmitSignalMethod("emitSignal"));
    }

    @Override
    public boolean canBePlacedInComputer(Collection<ComputerModule> computerModulesInstalled) {
        // Only one signalling module can be stored in a computer
        for (ComputerModule computerModule : computerModulesInstalled) {
            if (computerModule.getModuleType().equals(moduleType)) {
                return false;
            }
        }

        return true;
    }
}
