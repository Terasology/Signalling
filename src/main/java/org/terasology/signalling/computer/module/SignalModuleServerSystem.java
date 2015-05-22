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

import org.terasology.computer.component.ComputerComponent;
import org.terasology.computer.component.ComputerModuleComponent;
import org.terasology.computer.system.common.ComputerModuleRegistry;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.EventPriority;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.inventory.InventoryManager;
import org.terasology.logic.inventory.events.InventorySlotChangedEvent;
import org.terasology.math.Side;
import org.terasology.mobileBlocks.server.AfterBlockMovedEvent;
import org.terasology.mobileBlocks.server.BeforeBlockMovesEvent;
import org.terasology.mobileBlocks.server.BlockTransitionDuringMoveEvent;
import org.terasology.registry.In;
import org.terasology.signalling.components.SignalConsumerAdvancedStatusComponent;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalProducerComponent;
import org.terasology.world.BlockEntityRegistry;

import java.util.EnumSet;
import java.util.Set;

@RegisterSystem(RegisterMode.AUTHORITY)
public class SignalModuleServerSystem extends BaseComponentSystem {
    @In
    private ComputerModuleRegistry computerModuleRegistry;
    @In
    private EntityManager entityManager;
    @In
    private InventoryManager inventoryManager;
    @In
    private BlockEntityRegistry blockEntityRegistry;

    private boolean computerIsMoving;

    @ReceiveEvent
    public void computerModuleSlotChanged(InventorySlotChangedEvent event, EntityRef computerEntity, ComputerComponent computer) {
        // If computer is moving, we have to preserve the component to be able to copy it's data, so do not remove the
        // component at this point, the entity will be destroyed upon movement finish anyway
        if (!computerIsMoving) {
            ComputerModuleComponent oldModule = event.getOldItem().getComponent(ComputerModuleComponent.class);
            if (oldModule != null && oldModule.moduleType.equals(SignalModuleCommonSystem.COMPUTER_SIGNALLING_MODULE_TYPE)) {
                computerEntity.removeComponent(SignalProducerComponent.class);
                computerEntity.removeComponent(SignalConsumerComponent.class);
                computerEntity.removeComponent(SignalConsumerAdvancedStatusComponent.class);
            }
        }

        ComputerModuleComponent newModule = event.getNewItem().getComponent(ComputerModuleComponent.class);
        if (newModule != null && newModule.moduleType.equals(SignalModuleCommonSystem.COMPUTER_SIGNALLING_MODULE_TYPE)) {
            SignalConsumerComponent consumer = new SignalConsumerComponent();
            consumer.connectionSides = 63;
            consumer.mode = SignalConsumerComponent.Mode.SPECIAL;

            computerEntity.addComponent(new SignalProducerComponent());
            computerEntity.addComponent(new SignalConsumerAdvancedStatusComponent());
            computerEntity.addComponent(consumer);
        }
    }

    @ReceiveEvent
    public void beforeComputerMoveSetFlag(BeforeBlockMovesEvent event, EntityRef entity, ComputerComponent component) {
        computerIsMoving = true;
    }

    @ReceiveEvent
    public void afterComputerMoveResetFlag(AfterBlockMovedEvent event, EntityRef entity, ComputerComponent component) {
        computerIsMoving = false;
    }

    @ReceiveEvent(priority = EventPriority.PRIORITY_TRIVIAL)
    public void computerMovedCopyProduceSettings(BlockTransitionDuringMoveEvent event, EntityRef entity, SignalProducerComponent producer, SignalConsumerComponent consumer) {
        EntityRef newEntity = event.getIntoEntity();

        SignalConsumerComponent newConsumer = newEntity.getComponent(SignalConsumerComponent.class);

        newConsumer.connectionSides = consumer.connectionSides;
        newConsumer.mode = consumer.mode;

        newEntity.saveComponent(newConsumer);

        SignalProducerComponent newProducer = newEntity.getComponent(SignalProducerComponent.class);

        newProducer.connectionSides = producer.connectionSides;
        newProducer.signalStrength = producer.signalStrength;

        newEntity.saveComponent(newProducer);
    }
}
