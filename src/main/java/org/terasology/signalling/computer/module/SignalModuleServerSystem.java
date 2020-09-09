// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.computer.module;

import org.terasology.computer.component.ComputerComponent;
import org.terasology.computer.component.ComputerModuleComponent;
import org.terasology.computer.system.common.ComputerModuleRegistry;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.event.EventPriority;
import org.terasology.engine.entitySystem.event.ReceiveEvent;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.registry.In;
import org.terasology.engine.world.BlockEntityRegistry;
import org.terasology.inventory.logic.InventoryManager;
import org.terasology.inventory.logic.events.InventorySlotChangedEvent;
import org.terasology.mobileBlocks.server.AfterBlockMovedEvent;
import org.terasology.mobileBlocks.server.BeforeBlockMovesEvent;
import org.terasology.mobileBlocks.server.BlockTransitionDuringMoveEvent;
import org.terasology.signalling.components.SignalConsumerAdvancedStatusComponent;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalProducerComponent;

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
    public void computerModuleSlotChanged(InventorySlotChangedEvent event, EntityRef computerEntity,
                                          ComputerComponent computer) {
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
    public void computerMovedCopyProduceSettings(BlockTransitionDuringMoveEvent event, EntityRef entity,
                                                 SignalProducerComponent producer, SignalConsumerComponent consumer) {
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
