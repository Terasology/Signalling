// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.componentSystem;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import gnu.trove.iterator.TObjectLongIterator;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import org.joml.RoundingMode;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockNetwork.BlockNetworkUtil;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.OnChangedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.logic.delay.DelayManager;
import org.terasology.logic.delay.DelayedActionTriggeredEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Side;
import org.terasology.registry.CoreRegistry;
import org.terasology.registry.In;
import org.terasology.signalling.components.SignalConsumerAdvancedStatusComponent;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalConsumerStatusComponent;
import org.terasology.signalling.components.SignalGateComponent;
import org.terasology.signalling.components.SignalProducerComponent;
import org.terasology.signalling.components.SignalProducerModifiedComponent;
import org.terasology.signalling.components.SignalTimeDelayComponent;
import org.terasology.signalling.components.SignalTimeDelayModifiedComponent;
import org.terasology.signalling.nui.SetSignalDelayEvent;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockManager;

import java.util.Map;
import java.util.Set;

/**
 * The system that manages switches and dictates their behaviour.
 *
 * @author Marcin Sciesinski <marcins78@gmail.com>
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class SignalSwitchBehaviourSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    /**
     * The time in ms it takes to change a signal for a gate.
     */
    public static final int GATE_MINIMUM_SIGNAL_CHANGE_INTERVAL = 500;
    /**
     * The time in ms it takes to register a button press.
     */
    public static final int BUTTON_PRESS_TIME = 500;

    private static final String BUTTON_RELEASE_ID = "Signalling:ButtonRelease";
    private static final String DELAYED_ON_GATE_ID = "Signalling:DelayedOnGate";
    private static final String DELAYED_OFF_GATE_ID = "Signalling:DelayedOffGate";
    private static final String NORMAL_GATE_ID = "Signalling:NormalGate";
    private static final String REVERTED_GATE_ID = "Signalling:RevertedGate";

    private static final Logger logger = LoggerFactory.getLogger(SignalSystem.class);
    private static final long SIGNAL_CLEANUP_INTERVAL = 10000;

    @In
    private Time time;
    @In
    private WorldProvider worldProvider;
    @In
    private EntityManager entityManager;
    @In
    private BlockEntityRegistry blockEntityRegistry;
    @In
    private DelayManager delayManager;


    private Set<Vector3i> activatedPressurePlates = Sets.newHashSet();

    private TObjectLongMap<Vector3i> gateLastSignalChangeTime = new TObjectLongHashMap<>();

    private long lastSignalCleanupExecuteTime;

    private Block lampTurnedOff;
    private Block lampTurnedOn;
    private Block signalTransformer;
    private Block signalPressurePlate;
    private Block signalSwitch;
    private Block signalLimitedSwitch;
    private Block signalButton;

    private Map<String, GateSignalChangeHandler> signalChangeHandlers = Maps.newHashMap();

    /**
     * Prepares signalling related blocks and SignalChangeHandlers for later event handling.
     * These are obtained from an instance of {@link org.terasology.world.block.BlockManager} and by implementing {@link GateSignalChangeHandler}, respectively.
     */
    @Override
    public void initialise() {
        final BlockManager blockManager = CoreRegistry.get(BlockManager.class);
        lampTurnedOff = blockManager.getBlock("signalling:SignalLampOff");
        lampTurnedOn = blockManager.getBlock("signalling:SignalLampOn");
        signalTransformer = blockManager.getBlock("signalling:SignalTransformer");
        signalPressurePlate = blockManager.getBlock("signalling:SignalPressurePlate");
        signalSwitch = blockManager.getBlock("signalling:SignalSwitch");
        signalLimitedSwitch = blockManager.getBlock("signalling:SignalLimitedSwitch");
        signalButton = blockManager.getBlock("signalling:SignalButton");

        GateSignalChangeHandler normalGateSignalChangeHandler = new GateSignalChangeHandler() {
            @Override
            public void handleGateSignalChange(EntityRef entity) {
                delayGateSignalChangeIfNeeded(entity, NORMAL_GATE_ID);
            }

            @Override
            public void handleDelayedTrigger(String actionId, EntityRef entity) {
                if (processOutputForNormalGate(entity)) {
                    BlockComponent block = entity.getComponent(BlockComponent.class);
                    gateLastSignalChangeTime.put(block.getPosition(new Vector3i()), time.getGameTimeInMs());
                }
            }
        };

        signalChangeHandlers.put("AND", normalGateSignalChangeHandler);
        signalChangeHandlers.put("OR", normalGateSignalChangeHandler);
        signalChangeHandlers.put("XOR", normalGateSignalChangeHandler);

        signalChangeHandlers.put("NAND",
                new GateSignalChangeHandler() {
                    @Override
                    public void handleGateSignalChange(EntityRef entity) {
                        delayGateSignalChangeIfNeeded(entity, NORMAL_GATE_ID);
                    }

                    @Override
                    public void handleDelayedTrigger(String actionId, EntityRef entity) {
                        if (processOutputForRevertedGate(entity)) {
                            BlockComponent block = entity.getComponent(BlockComponent.class);
                            gateLastSignalChangeTime.put(block.getPosition(new Vector3i()), time.getGameTimeInMs());
                        }
                    }
                });
        signalChangeHandlers.put("ON_DELAY",
                new GateSignalChangeHandler() {
                    @Override
                    public void handleGateSignalChange(EntityRef entity) {
                        SignalConsumerStatusComponent consumerStatusComponent = entity.getComponent(SignalConsumerStatusComponent.class);
                        signalChangedForDelayOnGate(entity, consumerStatusComponent);
                    }

                    @Override
                    public void handleDelayedTrigger(String actionId, EntityRef entity) {
                        startProducingSignal(entity, -1);

                    }
                });
        signalChangeHandlers.put("OFF_DELAY",
                new GateSignalChangeHandler() {
                    @Override
                    public void handleGateSignalChange(EntityRef entity) {
                        SignalConsumerStatusComponent consumerStatusComponent = entity.getComponent(SignalConsumerStatusComponent.class);
                        signalChangedForDelayOffGate(entity, consumerStatusComponent);
                    }

                    @Override
                    public void handleDelayedTrigger(String actionId, EntityRef entity) {
                        stopProducingSignal(entity);
                    }
                });
        signalChangeHandlers.put("SET_RESET",
                new GateSignalChangeHandler() {
                    @Override
                    public void handleGateSignalChange(EntityRef entity) {
                        delayGateSignalChangeIfNeeded(entity, NORMAL_GATE_ID);
                    }

                    @Override
                    public void handleDelayedTrigger(String actionId, EntityRef entity) {
                        if (processOutputForSetResetGate(entity)) {
                            BlockComponent block = entity.getComponent(BlockComponent.class);
                            gateLastSignalChangeTime.put(block.getPosition(new Vector3i()), time.getGameTimeInMs());
                        }
                    }
                });
    }

    /**
     * Updates the SignalSwitchBehaviorSystem.
     * Handles pressure plate events and deletes old signal gate signal changes.
     * Removes a Pressure Plate without a player from the Hashset activatedPressurePlates, stopping its signal.
     * Removes any Signal Changes that are at least GATE_MINIMUM_SIGNAL_CHANGE_INTERVAL old from gateLastSignalChangeTime
     * @param delta The time in milliseconds since the last update
     */
    @Override
    public void update(float delta) {
        handlePressurePlateEvents();
        deleteOldSignalChangesForGates();
    }

    /**
     * Event handler for when a delayed trigger fires with the intent to stop a producer's signal.
     * Mainly used for buttons releasing after BUTTON_PRESS_TIME.
     * @param event The DelayedActionTriggeredEvent that is stopping the producer's signal
     * @param entity The entity (most likely a button) that is producing the signal
     * @param signalProducer The signal producing component of the entity
     */
    @ReceiveEvent
    public void delayedTriggerOnProducer(DelayedActionTriggeredEvent event, EntityRef entity, SignalProducerComponent signalProducer) {
        if (event.getActionId().equals(BUTTON_RELEASE_ID)) {
            stopProducingSignal(entity);
        }
    }

    /**
     * Handles a delayed trigger on a signal gate by calling the gate signal change handler of the given gate type.
     * @param event The event, used to determine what action was performed
     * @param entity The entity the action was performed on
     * @param signalGate A SignalGate Component used to determine how to handle the action.
     */
    @ReceiveEvent
    public void delayedTriggerOnSignalGate(DelayedActionTriggeredEvent event, EntityRef entity, SignalGateComponent signalGate) {
        String gateType = signalGate.gateType;
        GateSignalChangeHandler gateSignalChangeHandler = signalChangeHandlers.get(gateType);
        if (gateSignalChangeHandler != null) {
            gateSignalChangeHandler.handleDelayedTrigger(event.getActionId(), entity);
        }
    }

    /**
     * Deletes signal changes that have passed if the time since the last signal change cleanup is greater
     * than {@link #SIGNAL_CLEANUP_INTERVAL}.
     */
    private void deleteOldSignalChangesForGates() {
        long worldTime = time.getGameTimeInMs();
        if (lastSignalCleanupExecuteTime + SIGNAL_CLEANUP_INTERVAL < worldTime) {
            final TObjectLongIterator<Vector3i> iterator = gateLastSignalChangeTime.iterator();
            while (iterator.hasNext()) {
                iterator.advance();
                if (iterator.value() + GATE_MINIMUM_SIGNAL_CHANGE_INTERVAL < worldTime) {
                    iterator.remove();
                }
            }
            lastSignalCleanupExecuteTime = worldTime;
        }
    }

    /**
     * Handles signal changes for a normal gate represented by {@code blockEntity}.
     *
     * A normal gate produces a signal if it has a signal. If it does not have a signal, signal
     * production is stopped.
     *
     * @param blockEntity The normal gate entity.
     * @return A boolean stating whether the signal change was done or not.
     */
    private boolean processOutputForNormalGate(EntityRef blockEntity) {
        boolean hasSignal = blockEntity.getComponent(SignalConsumerStatusComponent.class).hasSignal;
        logger.debug("Processing gate, hasSignal=" + hasSignal);
        if (hasSignal) {
            return startProducingSignal(blockEntity, -1);
        } else {
            return stopProducingSignal(blockEntity);
        }
    }

    /**
     * Handles signal changes for a reverted gate represented by {@code blockEntity}.
     *
     * A reverted gate produces a signal if it does not have a signal. If it has a signal, signal
     * production is stopped.
     *
     * @param blockEntity The reverted gate entity.
     * @return A boolean stating whether the signal change was done or not.
     */
    private boolean processOutputForRevertedGate(EntityRef blockEntity) {
        boolean hasSignal = blockEntity.getComponent(SignalConsumerStatusComponent.class).hasSignal;
        if (!hasSignal) {
            return startProducingSignal(blockEntity, -1);
        } else {
            return stopProducingSignal(blockEntity);
        }
    }

    /**
     * Handles signal changes for a set reset gate represented by {@code blockEntity}.
     *
     * A set reset gate produces a signal if it has a signal in any of its set sides. However, if it has a
     * signal in its reset side, signal production is stopped.
     *
     * @param blockEntity The set reset gate entity.
     * @return A boolean stating whether the signal change was done or not.
     */
    private boolean processOutputForSetResetGate(EntityRef blockEntity) {
        SignalGateComponent signalGateComponent = blockEntity.getComponent(SignalGateComponent.class);
        SignalConsumerAdvancedStatusComponent consumerAdvancedStatusComponent = blockEntity.getComponent(SignalConsumerAdvancedStatusComponent.class);
        Block block = blockEntity.getComponent(BlockComponent.class).getBlock();
        Side resetSide = signalGateComponent.functionalSides.get(0);
        Integer resetSignal = consumerAdvancedStatusComponent.signalStrengths.get(BlockNetworkUtil.getResultSide(block, resetSide).name());

        SignalProducerComponent producerComponent = blockEntity.getComponent(SignalProducerComponent.class);
        int resultSignal = producerComponent.signalStrength;
        if ((resetSignal != null && resetSignal != 0)) {
            resultSignal = 0;
        } else {
            int size = signalGateComponent.functionalSides.size();
            for (int i = 1; i < size; i++) {
                Side setSide = signalGateComponent.functionalSides.get(i);
                Integer setSignal = consumerAdvancedStatusComponent.signalStrengths.get(BlockNetworkUtil.getResultSide(block, setSide).name());
                if (setSignal != null && setSignal != 0) {
                    resultSignal = -1;
                    break;
                }
            }
        }
        if (producerComponent.signalStrength != resultSignal) {
            producerComponent.signalStrength = resultSignal;
            blockEntity.saveComponent(producerComponent);
            if (resultSignal != 0) {
                if (!blockEntity.hasComponent(SignalProducerModifiedComponent.class)) {
                    blockEntity.addComponent(new SignalProducerModifiedComponent());
                }
            } else if (blockEntity.hasComponent(SignalProducerModifiedComponent.class)) {
                blockEntity.removeComponent(SignalProducerModifiedComponent.class);
            }
            return true;
        }
        return false;
    }

    /**
     * Iterates through all characters and checks if they are standing on a pressure plate. If an entity
     * with a {@link CharacterComponent} is standing on a pressure plate, the pressure plate starts
     * producing a signal. If a character steps off a pressure plate, signal production is stopped.
     */
    private void handlePressurePlateEvents() {
        Set<Vector3i> toRemoveSignal = Sets.newHashSet(activatedPressurePlates);

        Iterable<EntityRef> players = entityManager.getEntitiesWith(CharacterComponent.class, LocationComponent.class);
        for (EntityRef player : players) {
            Vector3f playerLocation = player.getComponent(LocationComponent.class).getWorldPosition(new Vector3f());
            Vector3i locationBeneathPlayer = new Vector3i(playerLocation.add(0.5f, -0.5f, 0.5f), RoundingMode.FLOOR);
            Block blockBeneathPlayer = worldProvider.getBlock(locationBeneathPlayer);
            if (blockBeneathPlayer == signalPressurePlate) {
                EntityRef entityBeneathPlayer = blockEntityRegistry.getBlockEntityAt(locationBeneathPlayer);
                SignalProducerComponent signalProducer = entityBeneathPlayer.getComponent(SignalProducerComponent.class);
                if (signalProducer != null) {
                    if (signalProducer.signalStrength == 0) {
                        startProducingSignal(entityBeneathPlayer, -1);
                        activatedPressurePlates.add(locationBeneathPlayer);
                    } else {
                        toRemoveSignal.remove(locationBeneathPlayer);
                    }
                }
            }
        }

        for (Vector3i pressurePlateLocation : toRemoveSignal) {
            EntityRef pressurePlate = blockEntityRegistry.getBlockEntityAt(pressurePlateLocation);
            SignalProducerComponent signalProducer = pressurePlate.getComponent(SignalProducerComponent.class);
            if (signalProducer != null) {
                stopProducingSignal(pressurePlate);
                activatedPressurePlates.remove(pressurePlateLocation);
            }
        }
    }

    /**
     * Delays an Entity's Signal upon receiving a SignalDelayEvent.
     * <p>
     * Updates the entity's SignalTimeDelayComponent  to the minimum of 500 and the entity.timeDelay
     * Updates the SignalTimeDelayModified to record whether the SignalTimeDelay has been modified.
     * @param event The SignalTimeDelay Component with the
     * @param entity The entity whose signal is being delayed
     */
    @ReceiveEvent(components = {BlockComponent.class, SignalTimeDelayComponent.class})
    public void configureTimeDelay(SetSignalDelayEvent event, EntityRef entity) {
        SignalTimeDelayComponent timeDelayComponent = entity.getComponent(SignalTimeDelayComponent.class);
        timeDelayComponent.delaySetting = Math.min(500, event.getTime());

        entity.saveComponent(timeDelayComponent);
        if (timeDelayComponent.delaySetting == 1000 && entity.hasComponent(SignalTimeDelayModifiedComponent.class)) {
            entity.removeComponent(SignalTimeDelayModifiedComponent.class);
        } else if (!entity.hasComponent(SignalTimeDelayModifiedComponent.class)) {
            entity.addComponent(new SignalTimeDelayModifiedComponent());
        }
    }

    /**
     * Activates a producer
     * If entity is a transformer, switch, limited switch, or button, the entity (producer)
     * will start producing a signal.
     * LimitedSwitch produces a signal strength 5.
     * SignalSwitch produces an infinite signal strength.
     * Button will produce an signal strength infinite signal strength, and stop after BUTTON_PRESS_TIME
     * An activated transformer will increase the signal strength by 1, unless it is already equal to 10 or infinity,
     * in which case it will stop producing a signal.
     * @param event The ActivationEvent that activates the producer
     * @param entity The producer to activate
     */
    @ReceiveEvent(components = {BlockComponent.class, SignalProducerComponent.class})
    public void producerActivated(ActivateEvent event, EntityRef entity) {
        SignalProducerComponent producerComponent = entity.getComponent(SignalProducerComponent.class);
        Vector3i blockLocation = entity.getComponent(BlockComponent.class).getPosition(new Vector3i());
        Block blockAtLocation = worldProvider.getBlock(blockLocation);
        if (blockAtLocation == signalTransformer) {
            signalTransformerActivated(entity, producerComponent);
        } else if (blockAtLocation == signalSwitch) {
            signalSwitchActivated(entity, producerComponent);
        } else if (blockAtLocation == signalLimitedSwitch) {
            signalLimitedSwitchActivated(entity, producerComponent);
        } else if (blockAtLocation == signalButton) {
            signalButtonActivated(entity, producerComponent);
        }
    }

    /**
     * Flips the limited switch represented by {@code entity}.
     *
     * If the limited switch was previously off, it flips on and starts producing a finite strength
     * signal of strength 5.
     *
     * If the limited switch was previously on, it flips off and signal production is stopped.
     *
     * @param entity The limited switch entity.
     * @param producerComponent The {@link SignalProducerComponent} on the limited switch entity.
     */
    private void signalLimitedSwitchActivated(EntityRef entity, SignalProducerComponent producerComponent) {
        switchFlipped(5, entity, producerComponent);
    }

    /**
     * Flips the switch represented by {@code entity}.
     *
     * If the switch was previously off, it flips on and starts producing a infinite strength
     * signal.
     *
     * If the switch was previously on, it flips off and signal production is stopped.
     *
     * @param entity The switch entity.
     * @param producerComponent The {@link SignalProducerComponent} on the switch entity.
     */
    private void signalSwitchActivated(EntityRef entity, SignalProducerComponent producerComponent) {
        switchFlipped(-1, entity, producerComponent);
    }

    /**
     * Activates the button represented by {@code entity}.
     *
     * When activated, the button starts emitting an infinite signal and is "released" after a delay of
     * {@link #BUTTON_PRESS_TIME} milliseconds.
     *
     * @param entity The button entity.
     * @param producerComponent The {@link SignalProducerComponent} on the button entity.
     */
    private void signalButtonActivated(EntityRef entity, SignalProducerComponent producerComponent) {
        delayManager.addDelayedAction(entity, BUTTON_RELEASE_ID, BUTTON_PRESS_TIME);

        startProducingSignal(entity, -1);
    }

    /**
     * Flips the switch represented by {@code entity}.
     *
     * If the switch was previously off, it flips on and starts producing a signal with strength
     * {@code onSignalStrength}.
     *
     * If the switch was previously on, it flips off and signal production is stopped.
     *
     * @param onSignalStrength The strength of the signal produced when the switch is flipped on.
     * @param entity The switch entity.
     * @param producerComponent The {@link SignalProducerComponent} on the switch entity.
     */
    private void switchFlipped(int onSignalStrength, EntityRef entity, SignalProducerComponent producerComponent) {
        int currentSignalStrength = producerComponent.signalStrength;
        if (currentSignalStrength == 0) {
            startProducingSignal(entity, onSignalStrength);
        } else {
            stopProducingSignal(entity);
        }
    }

    /**
     * Activates the transformer represented by {@code entity}.
     *
     * Upon activation, a transformer increases its signal strength by 1. However, if the signal strength is
     * already 10 or infinite, signal production is stopped.
     *
     * @param entity The transformer entity.
     * @param producerComponent The {@link SignalProducerComponent} on the transformer entity.
     */
    private void signalTransformerActivated(EntityRef entity, SignalProducerComponent producerComponent) {
        int result = producerComponent.signalStrength + 1;
        if (result == 11) {
            result = 0;
        }
        if (result > 0) {
            startProducingSignal(entity, result);
        } else {
            stopProducingSignal(entity);
        }
    }

    /**
     * Updates the consumer of a gate based on the gate's type.
     * @param event The event caused by changing a component
     * @param entity The gate to modify
     * @param signalGate The SignalGate Component, used to determine the action performed.
     * @param block
     */
    @ReceiveEvent(components = {SignalConsumerStatusComponent.class})
    public void gateConsumerModified(OnChangedComponent event, EntityRef entity, SignalGateComponent signalGate, BlockComponent block) {
        String gateType = signalGate.gateType;
        GateSignalChangeHandler gateSignalChangeHandler = signalChangeHandlers.get(gateType);
        if (gateSignalChangeHandler != null) {
            gateSignalChangeHandler.handleGateSignalChange(entity);
        }
    }

    /**
     * Updates a Consumer of a signal based on a change to that signal.
     * Example: Turning on and off a lamp.
     * @param event The event modifying the consumer.
     * @param entity The consumer to be updated.
     */
    @ReceiveEvent(components = {SignalConsumerStatusComponent.class})
    public void consumerModified(OnChangedComponent event, EntityRef entity) {
        if (entity.hasComponent(BlockComponent.class)) {
            SignalConsumerStatusComponent consumerStatusComponent = entity.getComponent(SignalConsumerStatusComponent.class);
            Vector3i blockLocation = entity.getComponent(BlockComponent.class).getPosition(new Vector3i());
            Block block = worldProvider.getBlock(blockLocation);

            SignalConsumerComponent signalConsumerComponent = entity.getComponent(SignalConsumerComponent.class);

            if (block == lampTurnedOff && consumerStatusComponent.hasSignal) {
                lampTurnedOn.setKeepActive(true);
                worldProvider.setBlock(blockLocation, lampTurnedOn);
            } else if (block == lampTurnedOn && !consumerStatusComponent.hasSignal) {
                lampTurnedOff.setKeepActive(true);
                worldProvider.setBlock(blockLocation, lampTurnedOff);
            }
            blockEntityRegistry.getBlockEntityAt(blockLocation).addOrSaveComponent(signalConsumerComponent);
            worldProvider.getBlock(blockLocation).setKeepActive(true);

        }
    }

    private void signalChangedForDelayOffGate(EntityRef entity, SignalConsumerStatusComponent consumerStatusComponent) {
        SignalTimeDelayComponent delay = entity.getComponent(SignalTimeDelayComponent.class);
        if (consumerStatusComponent.hasSignal) {
            // Remove any signal-delayed actions on the entity and turn on signal from it, if it doesn't have any
            if (delayManager.hasDelayedAction(entity, DELAYED_OFF_GATE_ID)) {
                delayManager.cancelDelayedAction(entity, DELAYED_OFF_GATE_ID);
            }
            startProducingSignal(entity, -1);
        } else {
            // Schedule for the gate to be looked at when the time passes
            delayManager.addDelayedAction(entity, DELAYED_OFF_GATE_ID, delay.delaySetting);
        }
    }

    private void signalChangedForDelayOnGate(EntityRef entity, SignalConsumerStatusComponent consumerStatusComponent) {
        SignalTimeDelayComponent delay = entity.getComponent(SignalTimeDelayComponent.class);
        if (consumerStatusComponent.hasSignal) {
            // Schedule for the gate to be looked at when the time passes
            delayManager.addDelayedAction(entity, DELAYED_ON_GATE_ID, delay.delaySetting);
        } else {
            // Remove any signal-delayed actions on the entity and turn off signal from it, if it has any
            if (delayManager.hasDelayedAction(entity, DELAYED_ON_GATE_ID)) {
                delayManager.cancelDelayedAction(entity, DELAYED_ON_GATE_ID);
            }
            stopProducingSignal(entity);
        }
    }

    private void signalChangedForNormalGate(EntityRef entity) {
        delayGateSignalChangeIfNeeded(entity, NORMAL_GATE_ID);
    }

    private void signalChangedForNotGate(EntityRef entity, SignalConsumerStatusComponent consumerStatusComponent) {
        logger.debug("Gate has signal: " + consumerStatusComponent.hasSignal);
        delayGateSignalChangeIfNeeded(entity, REVERTED_GATE_ID);
    }

    private void delayGateSignalChangeIfNeeded(EntityRef entity, String actionId) {
        if (!delayManager.hasDelayedAction(entity, actionId)) {
            // Schedule for the gate to be looked either immediately (during "update" method) or at least
            // GATE_MINIMUM_SIGNAL_CHANGE_INTERVAL from the time it has last changed, whichever is later
            long delay;
            final Vector3i location = entity.getComponent(BlockComponent.class).getPosition(new Vector3i());
            if (gateLastSignalChangeTime.containsKey(location)) {
                delay = gateLastSignalChangeTime.get(location) + GATE_MINIMUM_SIGNAL_CHANGE_INTERVAL - time.getGameTimeInMs();
            } else {
                delay = 0;
            }
            delayManager.addDelayedAction(entity, actionId, delay);
        }
    }

    /**
     * Starts production of a signal of given strength from the given entity.
     *
     * @param entity The signal producer entity.
     * @param signalStrength The strength of the signal produced.
     * @return A boolean stating whether a change to the {@link SignalProducerComponent} was actually made.
     */
    private boolean startProducingSignal(EntityRef entity, int signalStrength) {
        final SignalProducerComponent producer = entity.getComponent(SignalProducerComponent.class);
        if (producer.signalStrength != signalStrength) {
            producer.signalStrength = signalStrength;
            entity.saveComponent(producer);
            entity.addComponent(new SignalProducerModifiedComponent());
            return true;
        }
        return false;
    }

    /**
     * Stops production of a signal from the given entity.
     *
     * @param entity The signal producer entity.
     * @return A boolean stating whether a change to the {@link SignalProducerComponent} was actually made.
     */
    private boolean stopProducingSignal(EntityRef entity) {
        SignalProducerComponent producer = entity.getComponent(SignalProducerComponent.class);
        if (producer.signalStrength != 0) {
            producer.signalStrength = 0;
            entity.saveComponent(producer);
            entity.removeComponent(SignalProducerModifiedComponent.class);
            return true;
        }
        return false;
    }
}
