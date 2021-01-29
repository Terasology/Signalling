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
package org.terasology.signalling.componentSystem;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockNetwork.BlockNetworkUtil;
import org.terasology.blockNetwork.EfficientBlockNetwork;
import org.terasology.blockNetwork.Network2;
import org.terasology.blockNetwork.NetworkChangeReason;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.OnChangedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.config.ModuleConfigManager;
import org.terasology.logic.health.BeforeDestroyEvent;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.registry.In;
import org.terasology.signalling.components.SignalConductorComponent;
import org.terasology.signalling.components.SignalConsumerAdvancedStatusComponent;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalConsumerStatusComponent;
import org.terasology.signalling.components.SignalProducerComponent;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BeforeDeactivateBlocks;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.OnActivatedBlocks;
import org.terasology.world.block.items.OnBlockItemPlaced;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A system that manages networks of signal producers, conductors, and consumers.
 *
 * This system sends signals from producers to consumers via conductors at every update and also handles
 * signalling events for {@link SignalProducerComponent}, {@link SignalConductorComponent}, and
 * {@link SignalConsumerComponent}.
 */
@RegisterSystem(value = RegisterMode.AUTHORITY)
public class SignalSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    private static final Logger logger = LoggerFactory.getLogger(SignalSystem.class);

    @In
    private Time time;
    @In
    private WorldProvider worldProvider;

    @In
    private BlockEntityRegistry blockEntityRegistry;

    @In
    private ModuleConfigManager moduleConfigManager;

    private EfficientBlockNetwork<SignalNetworkNode> signalNetwork;
    private SignalEfficientNetworkState signalNetworkState = new SignalEfficientNetworkState();

    private long processingMinimumInterval;
    private boolean consumerCanPowerItself;

    // Used to detect producer changes
    private Map<SignalNetworkNode, Integer> producerSignalStrengths = Maps.newHashMap();

    private Set<SignalNetworkNode> modifiedProducers = Sets.newHashSet();
    private Set<SignalNetworkNode> modifiedConsumers = Sets.newHashSet();

    // Used to store signal for consumer from networks
    private Map<SignalNetworkNode, Map<Network2<SignalNetworkNode>, NetworkSignals>> consumerSignalInNetworks =
        Maps.newHashMap();

    private long lastUpdate;

    @Override
    public void initialise() {
        signalNetwork = new EfficientBlockNetwork<>();
        signalNetwork.addTopologyListener(signalNetworkState);
    }

    @Override
    public void preBegin() {
        processingMinimumInterval = moduleConfigManager.getIntVariable("Signalling", "processingMinimumInterval", 0);
        consumerCanPowerItself = moduleConfigManager.getBooleanVariable("Signalling", "consumerCanPowerItself", false);
    }

    @Override
    public void shutdown() {
        signalNetwork = null;
    }

    @Override
    public void update(float delta) {
        long worldTime = time.getGameTimeInMs();
        // Ensures that computers cannot be faster than the processing interval
        if (worldTime > lastUpdate + processingMinimumInterval) {
            lastUpdate = worldTime;

            updateSignals();
        }
    }

    /**
     * Updates signals and their states in all signal networks and notifies consumers of any changes.
     */
    private void updateSignals() {
        // Gather all networks that might have their signal state modified
        Set<Network2<SignalNetworkNode>> networksToRecalculate =
            Sets.newHashSet(signalNetworkState.consumeNetworksToRecalculate());

        // This includes networks with modified producers
        appendNetworksContainingModifiedProducer(networksToRecalculate);

        // Gather all consumers that might be affected by the changes
        Set<SignalNetworkNode> consumersToEvaluate = Sets.newHashSet();

        for (Network2<SignalNetworkNode> network : networksToRecalculate) {
            if (signalNetwork.isNetworkActive(network)) {
                Iterable<SignalNetworkNode> consumers = getConsumersInNetwork(network);
                for (SignalNetworkNode consumer : consumers) {
                    // Set the signal for each consumer in the affected network
                    NetworkSignals consumerSignalInNetwork = getConsumerSignalInNetwork(network, consumer);
                    consumerSignalInNetworks.get(consumer).put(network, consumerSignalInNetwork);
                }
                consumersToEvaluate.addAll(Sets.newHashSet(consumers));
            }
        }

        // Update signals of consumers that have been changed in networks that are not going to be recalculated
        for (SignalNetworkNode modifiedConsumer : Iterables.concat(modifiedConsumers,
            signalNetworkState.consumeConsumersToRecalculate())) {
            for (Network2<SignalNetworkNode> network : signalNetwork.getNetworks()) {
                if (!networksToRecalculate.contains(network) && network.hasLeafNode(modifiedConsumer)) {
                    NetworkSignals consumerSignalInNetwork = getConsumerSignalInNetwork(network, modifiedConsumer);
                    consumerSignalInNetworks.get(modifiedConsumer).put(network, consumerSignalInNetwork);
                }
            }

            consumersToEvaluate.add(modifiedConsumer);
        }

        // Clearing the changed states
        modifiedProducers.clear();
        modifiedConsumers.clear();

        // Set consumer status changes
        for (SignalNetworkNode consumerToEvaluate : consumersToEvaluate) {
            if (signalNetwork.containsLeafNode(consumerToEvaluate)) {
                final EntityRef blockEntity =
                    blockEntityRegistry.getBlockEntityAt(consumerToEvaluate.location.toVector3i());
                final SignalConsumerComponent consumerComponent =
                    blockEntity.getComponent(SignalConsumerComponent.class);
                if (consumerComponent != null) {
                    Map<Network2<SignalNetworkNode>, NetworkSignals> consumerSignals =
                        consumerSignalInNetworks.get(consumerToEvaluate);
                    removeStaleSignals(consumerToEvaluate, consumerSignals);

                    processSignalConsumerResult(consumerSignals.values(), consumerComponent, blockEntity);
                }
            }
        }
    }

    /**
     * Finds the consumers in a network
     *
     * @param network The network to query for Consumers
     * @return The consumers on the given network
     */
    private Iterable<SignalNetworkNode> getConsumersInNetwork(Network2<SignalNetworkNode> network) {
        return network.getLeafNodes().stream()
            .filter(input -> input.getType() == SignalNetworkNode.Type.CONSUMER)
            .collect(Collectors.toList());
    }

    /**
     * Finds the producers in a network
     *
     * @param network The network to query for Producers
     * @return The producers on the given network
     */
    private Iterable<SignalNetworkNode> getProducersInNetwork(Network2<SignalNetworkNode> network) {
        return network.getLeafNodes().stream()
            .filter(input -> input.getType() == SignalNetworkNode.Type.PRODUCER)
            .collect(Collectors.toList());
    }

    /**
     * Adds all networks that have a producer that is modified to the list of networks to be recalculated
     *
     * @param networksToRecalculate The list of networks that need to be recalculated
     */
    private void appendNetworksContainingModifiedProducer(Set<Network2<SignalNetworkNode>> networksToRecalculate) {
        for (Network2<SignalNetworkNode> network : signalNetwork.getNetworks()) {
            if (!networksToRecalculate.contains(network)) {
                for (SignalNetworkNode modifiedProducer : modifiedProducers) {
                    if (network.getLeafNodes().contains(modifiedProducer)) {
                        networksToRecalculate.add(network);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Removes the signals for a consumer which have gone stale. A stale network is that which is no longer active or
     * does not contain the consumer.
     *
     * @param consumerToEvaluate The network node of the consumer with respect to which stale signals are
     *     evaluated.
     * @param consumerSignals A mapping of the networks containing the consumer and the signals they contain.
     */
    private void removeStaleSignals(SignalNetworkNode consumerToEvaluate, Map<Network2<SignalNetworkNode>,
        NetworkSignals> consumerSignals) {
        Iterator<Map.Entry<Network2<SignalNetworkNode>, NetworkSignals>> signalInNetworkIterator =
            consumerSignals.entrySet().iterator();
        while (signalInNetworkIterator.hasNext()) {
            Map.Entry<Network2<SignalNetworkNode>, NetworkSignals> signalEntry = signalInNetworkIterator.next();
            // If the network no longer is active or no longer contains the consumer - it is "stale"
            if (!signalNetwork.isNetworkActive(signalEntry.getKey()) || !signalEntry.getKey().hasLeafNode(consumerToEvaluate)) {
                signalInNetworkIterator.remove();
            }
        }
    }

    /**
     * Sends the correct signal to the correct entity based on the received signal
     *
     * @param networkSignals The signals in the network
     * @param signalConsumerComponent The component of the gate receiving the signal
     * @param entity The block to send the signal to
     */
    private void processSignalConsumerResult(Collection<NetworkSignals> networkSignals,
                                             SignalConsumerComponent signalConsumerComponent, EntityRef entity) {
        final SignalConsumerComponent.Mode mode = signalConsumerComponent.mode;
        switch (mode) {
            // OR
            case AT_LEAST_ONE: {
                final boolean signal = hasSignalForOr(networkSignals);
                outputSignalToSimpleConsumer(entity, signal);
                return;
            }
            // AND
            case ALL_CONNECTED: {
                final boolean signal = hasSignalForAnd(networkSignals);
                outputSignalToSimpleConsumer(entity, signal);
                return;
            }
            // XOR
            case EXACTLY_ONE: {
                final boolean signal = hasSignalForXor(networkSignals);
                outputSignalToSimpleConsumer(entity, signal);
                return;
            }
            // Special leaving the calculation to the block's system itself
            case SPECIAL: {
                outputSignalToAdvancedConsumer(entity, networkSignals);
                return;
            }
            default:
                throw new IllegalArgumentException("Unknown mode set for SignalConsumerComponent");
        }
    }

    /**
     * Sends a signal change to an advanced consumer represented by {@code entity}. This is done by updating the {@code
     * signalStrengths} field of the {@link SignalConsumerAdvancedStatusComponent} on the {@code entity}.
     *
     * @param entity The consumer entity.
     * @param networkSignals The signals in the network which are to be sent to the {@code entity}.
     */
    private void outputSignalToAdvancedConsumer(EntityRef entity, Collection<NetworkSignals> networkSignals) {
        final SignalConsumerAdvancedStatusComponent advancedStatusComponent =
            entity.getComponent(SignalConsumerAdvancedStatusComponent.class);
        Map<String, Integer> signalResult = new HashMap<>();
        if (networkSignals != null) {
            for (NetworkSignals networkSignal : networkSignals) {
                for (Map.Entry<Side, Integer> sideSignalEntry : networkSignal.signalStrengths.entrySet()) {
                    signalResult.put(sideSignalEntry.getKey().name(), sideSignalEntry.getValue());
                }
            }
        }
        if (!advancedStatusComponent.signalStrengths.equals(signalResult)) {
            advancedStatusComponent.signalStrengths = signalResult;
            entity.saveComponent(advancedStatusComponent);
        }
    }

    /**
     * Sends a signal change to a simple consumer represented by {@code entity}. This is done by updating the {@link
     * SignalConsumerStatusComponent} on the {@code entity}.
     *
     * @param entity The consumer entity.
     * @param result Whether a signal has been sent to the consumer or not.
     */
    private void outputSignalToSimpleConsumer(EntityRef entity, boolean result) {
        final SignalConsumerStatusComponent consumerStatusComponent =
            entity.getComponent(SignalConsumerStatusComponent.class);
        if (consumerStatusComponent.hasSignal != result) {
            consumerStatusComponent.hasSignal = result;
            entity.saveComponent(consumerStatusComponent);
            if (logger.isDebugEnabled()) {
                logger.debug("Consumer has signal: " + result);
            }
        }
    }

    /**
     * Used for the XOR signal block
     *
     * @param networkSignals The signals in the network
     * @return True if the network has a XOR signal
     */
    private boolean hasSignalForXor(Collection<NetworkSignals> networkSignals) {
        if (networkSignals == null) {
            return false;
        }
        boolean connected = false;
        for (NetworkSignals networkSignal : networkSignals) {
            if (SideBitFlag.getSides(networkSignal.sidesWithSignal).size() > 1) {
                // More than one side connected in network
                return false;
            } else if (networkSignal.sidesWithSignal > 0) {
                if (connected) {
                    // One side connected in network, but already connected in other network
                    return false;
                } else {
                    connected = true;
                }
            }
        }

        return connected;
    }

    /**
     * Used for the AND signal block
     *
     * @param networkSignals The signals in the network
     * @return True if the network has a AND signal
     */
    private boolean hasSignalForAnd(Collection<NetworkSignals> networkSignals) {
        if (networkSignals == null) {
            return false;
        }
        for (NetworkSignals networkSignal : networkSignals) {
            if (networkSignal.sidesWithoutSignal > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Used for the OR signal block
     *
     * @param networkSignals The signals in the network
     * @return True if the network has a OR signal
     */
    private boolean hasSignalForOr(Collection<NetworkSignals> networkSignals) {
        if (networkSignals == null) {
            return false;
        }
        for (NetworkSignals networkSignal : networkSignals) {
            if (networkSignal.sidesWithSignal > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the most powerful signals in the given network. If there is a signal with infinite strength on the
     * network, the whole network is returned.
     *
     * @param network The network to check for signals
     * @param consumerNode The node receiving the signals
     * @return The most powerful of the signals
     */
    private NetworkSignals getConsumerSignalInNetwork(Network2<SignalNetworkNode> network,
                                                      SignalNetworkNode consumerNode) {
        // Check for infinite signal strength (-1), if there - it powers whole network
        Iterable<SignalNetworkNode> producers = getProducersInNetwork(network);
        for (SignalNetworkNode producer : producers) {
            if (consumerCanPowerItself || !producer.location.equals(consumerNode.location)) {
                final int signalStrength = producerSignalStrengths.get(producer);
                if (signalStrength == -1) {
                    NetworkSignals networkSignals = new NetworkSignals();
                    for (Side side : SideBitFlag.getSides(network.getLeafSidesInNetwork(consumerNode))) {
                        networkSignals.addSignal(side, -1);
                    }

                    return networkSignals;
                }
            }
        }

        byte sidesInNetwork = network.getLeafSidesInNetwork(consumerNode);

        NetworkSignals networkSignals = new NetworkSignals();

        for (Side sideInNetwork : SideBitFlag.getSides(sidesInNetwork)) {
            int maxSignal = getMaxSignalInNetworkOnSide(network, consumerNode, producers, sideInNetwork);
            networkSignals.addSignal(sideInNetwork, maxSignal);
        }

        return networkSignals;
    }

    /**
     * Returns the most powerful signal on the network on a specific side
     *
     * @param network The network to check for signals
     * @param consumerNode The node to check from
     * @param producers The producers in the network
     * @param sideInNetwork The side in question
     * @return The most powerful signal on the side
     */
    private int getMaxSignalInNetworkOnSide(Network2<SignalNetworkNode> network, SignalNetworkNode consumerNode,
                                            Iterable<SignalNetworkNode> producers, Side sideInNetwork) {
        int result = 0;
        for (SignalNetworkNode producer : producers) {
            if (consumerCanPowerItself || !producer.location.equals(consumerNode.location)) {
                final int signalStrength = producerSignalStrengths.get(producer);
                int distance = network.getDistanceWithSide(producer, consumerNode, sideInNetwork, signalStrength);
                if (distance != -1) {
                    result = Math.max(signalStrength - distance + 1, result);
                }
            }
        }
        return result;
    }

    /**
     * Makes a node with the given specifications
     *
     * @param location The location of the node
     * @param inputDefinedSides The sides to receive input
     * @param outputDefinedSides The sides to make output
     * @param type The {@link SignalNetworkNode.Type} the node should be
     * @return A node with the given specifications
     */
    private SignalNetworkNode toNode(Vector3ic location, int inputDefinedSides, int outputDefinedSides,
                                     SignalNetworkNode.Type type) {
        return new SignalNetworkNode(location, getConnections(location, (byte) inputDefinedSides),
            getConnections(location, (byte) outputDefinedSides), type);
    }

    /**
     * Gets all the sides of the block that it is possible to connect to, input or output
     *
     * @param location The location of the block to get connections from
     * @param definedSides The sides of the block that are defined as input or output
     * @return The sides which it is possible to connect to
     */
    private byte getConnections(Vector3ic location, byte definedSides) {
        return BlockNetworkUtil.getResultConnections(worldProvider.getBlock(location), definedSides);
    }

    /**
     * Adds the placed block to the correct list
     *
     * @param event The event triggered by block placement
     * @param entityRef The entity information of the placed block
     */
    @ReceiveEvent()
    public void onBlockPlaced(OnBlockItemPlaced event, EntityRef entityRef) {
        EntityRef ref = event.getPlacedBlock();
        final Vector3ic location = event.getPosition();

        if (ref.hasComponent(SignalConductorComponent.class)) {
            logger.debug("SignalConductor placed: " + ref.getParentPrefab());
            for (SignalConductorComponent.ConnectionGroup connectionGroup :
                ref.getComponent(SignalConductorComponent.class).connectionGroups) {
                final SignalNetworkNode conductorNode = toNode(location, connectionGroup.inputSides,
                    connectionGroup.outputSides, SignalNetworkNode.Type.CONDUCTOR);
                signalNetwork.addNetworkingBlock(conductorNode, NetworkChangeReason.WORLD_CHANGE);
            }
        }

        if (ref.hasComponent(SignalConsumerComponent.class)) {
            logger.debug("SignalConsumer placed: " + ref.getParentPrefab());
            byte connectingOnSides = ref.getComponent(SignalConsumerComponent.class).connectionSides;

            SignalNetworkNode consumerNode = toNode(location, connectingOnSides, 0, SignalNetworkNode.Type.CONSUMER);

            consumerSignalInNetworks.put(consumerNode, Maps.newHashMap());
            signalNetwork.addLeafBlock(consumerNode, NetworkChangeReason.WORLD_CHANGE);
        } else if (ref.hasComponent(SignalProducerComponent.class)) {
            logger.debug("SignalProducer placed: " + ref.getParentPrefab());
            final SignalProducerComponent producerComponent = ref.getComponent(SignalProducerComponent.class);
            final int signalStrength = producerComponent.signalStrength;
            byte connectingOnSides = producerComponent.connectionSides;

            final SignalNetworkNode producerNode = toNode(location, 0, connectingOnSides,
                SignalNetworkNode.Type.PRODUCER);

            producerSignalStrengths.put(producerNode, signalStrength);
            signalNetwork.addLeafBlock(producerNode, NetworkChangeReason.WORLD_CHANGE);
        }

    }


    /*
     * ****************************** Conductor events ********************************
     */

    @ReceiveEvent(components = {SignalConductorComponent.class})
    public void prefabConductorLoaded(OnActivatedBlocks event, EntityRef blockType) {
        for (SignalConductorComponent.ConnectionGroup connectionGroup :
            blockType.getComponent(SignalConductorComponent.class).connectionGroups) {
            Set<SignalNetworkNode> conductorNodes = Sets.newHashSet();
            for (Vector3ic location : event) {
                final SignalNetworkNode conductorNode = toNode(location, connectionGroup.inputSides,
                    connectionGroup.outputSides, SignalNetworkNode.Type.CONDUCTOR);
                conductorNodes.add(conductorNode);
            }
            signalNetwork.addNetworkingBlocks(conductorNodes, NetworkChangeReason.CHUNK_EVENT);
        }
    }

    @ReceiveEvent(components = {SignalConductorComponent.class})
    public void prefabConductorUnloaded(BeforeDeactivateBlocks event, EntityRef blockType) {
        for (SignalConductorComponent.ConnectionGroup connectionGroup :
            blockType.getComponent(SignalConductorComponent.class).connectionGroups) {
            Set<SignalNetworkNode> conductorNodes = Sets.newHashSet();
            // Quite messy due to the order of operations, need to check if the order is important
            for (Vector3ic location : event) {
                final SignalNetworkNode conductorNode = toNode(location, connectionGroup.inputSides,
                    connectionGroup.outputSides, SignalNetworkNode.Type.CONDUCTOR);
                conductorNodes.add(conductorNode);
            }
            signalNetwork.removeNetworkingBlocks(conductorNodes, NetworkChangeReason.CHUNK_EVENT);
        }
    }


    @ReceiveEvent(components = {BlockComponent.class, SignalConductorComponent.class})
    public void conductorRemoved(BeforeDestroyEvent event, EntityRef block) {
        final Vector3i location = block.getComponent(BlockComponent.class).getPosition(new Vector3i());
        for (SignalConductorComponent.ConnectionGroup connectionGroup :
            block.getComponent(SignalConductorComponent.class).connectionGroups) {
            final SignalNetworkNode conductorNode = toNode(location, connectionGroup.inputSides,
                connectionGroup.outputSides, SignalNetworkNode.Type.CONDUCTOR);
            signalNetwork.removeNetworkingBlock(conductorNode, NetworkChangeReason.WORLD_CHANGE);
        }
    }

    /*
     * ****************************** Producer events ********************************
     */

    @ReceiveEvent(components = {SignalProducerComponent.class})
    public void prefabProducerLoaded(OnActivatedBlocks event, EntityRef blockType) {
        final SignalProducerComponent producerComponent = blockType.getComponent(SignalProducerComponent.class);
        int signalStrength = producerComponent.signalStrength;
        Set<SignalNetworkNode> producerNodes = Sets.newHashSet();
        for (Vector3ic location : event) {
            final SignalNetworkNode producerNode = toNode(location, 0,
                producerComponent.connectionSides, SignalNetworkNode.Type.PRODUCER);

            producerSignalStrengths.put(producerNode, signalStrength);
            producerNodes.add(producerNode);
        }
        signalNetwork.addLeafBlocks(producerNodes, NetworkChangeReason.CHUNK_EVENT);
    }

    @ReceiveEvent(components = {SignalProducerComponent.class})
    public void prefabProducerUnloaded(BeforeDeactivateBlocks event, EntityRef blockType) {
        byte connectingOnSides = blockType.getComponent(SignalProducerComponent.class).connectionSides;
        // Quite messy due to the order of operations, need to check if the order is important
        Set<SignalNetworkNode> producerNodes = Sets.newHashSet();
        for (Vector3ic location : event) {
            final SignalNetworkNode producerNode = toNode(location, 0, connectingOnSides,
                SignalNetworkNode.Type.PRODUCER);
            producerNodes.add(producerNode);
        }

        signalNetwork.removeLeafBlocks(producerNodes, NetworkChangeReason.CHUNK_EVENT);
        for (SignalNetworkNode producerNode : producerNodes) {
            producerSignalStrengths.remove(producerNode);
        }
    }


    @ReceiveEvent(components = {SignalProducerComponent.class})
    public void producerUpdated(OnChangedComponent event, EntityRef block) {
        logger.debug("Producer updated: " + block.getParentPrefab());
        if (block.hasComponent(BlockComponent.class)) {
            Vector3i location = block.getComponent(BlockComponent.class).getPosition(new Vector3i());
            final SignalProducerComponent producerComponent = block.getComponent(SignalProducerComponent.class);

            Set<SignalNetworkNode> oldLeafNodes = Sets.newHashSet(signalNetwork.getLeafNodesAt(location));
            for (SignalNetworkNode oldLeafNode : oldLeafNodes) {
                if (oldLeafNode.getType() == SignalNetworkNode.Type.PRODUCER) {
                    producerSignalStrengths.remove(oldLeafNode);
                    signalNetwork.removeLeafBlock(oldLeafNode, NetworkChangeReason.WORLD_CHANGE);
                }
            }

            SignalNetworkNode node = toNode(location, 0, producerComponent.connectionSides,
                SignalNetworkNode.Type.PRODUCER);
            producerSignalStrengths.put(node, producerComponent.signalStrength);
            signalNetwork.addLeafBlock(node, NetworkChangeReason.WORLD_CHANGE);

            modifiedProducers.add(node);
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalProducerComponent.class})
    public void producerRemoved(BeforeDestroyEvent event, EntityRef block) {
        Vector3i location = block.getComponent(BlockComponent.class).getPosition(new Vector3i());
        byte connectingOnSides = block.getComponent(SignalProducerComponent.class).connectionSides;

        final SignalNetworkNode producerNode = toNode(location, 0, connectingOnSides, SignalNetworkNode.Type.PRODUCER);
        signalNetwork.removeLeafBlock(producerNode, NetworkChangeReason.WORLD_CHANGE);
        producerSignalStrengths.remove(producerNode);
    }

    /*
     * ****************************** Consumer events ********************************
     */

    @ReceiveEvent(components = {SignalConsumerComponent.class})
    public void prefabConsumerLoaded(OnActivatedBlocks event, EntityRef blockType) {
        byte connectingOnSides = blockType.getComponent(SignalConsumerComponent.class).connectionSides;
        Set<SignalNetworkNode> consumerNodes = Sets.newHashSet();
        for (Vector3ic location : event) {
            SignalNetworkNode consumerNode = toNode(location, connectingOnSides, 0,
                SignalNetworkNode.Type.CONSUMER);

            consumerSignalInNetworks.put(consumerNode, Maps.newHashMap());
            consumerNodes.add(consumerNode);
        }
        signalNetwork.addLeafBlocks(consumerNodes, NetworkChangeReason.CHUNK_EVENT);
    }

    @ReceiveEvent(components = {SignalConsumerComponent.class})
    public void prefabConsumerUnloaded(BeforeDeactivateBlocks event, EntityRef blockType) {
        byte connectingOnSides = blockType.getComponent(SignalConsumerComponent.class).connectionSides;
        Set<SignalNetworkNode> consumerNodes = Sets.newHashSet();

        // Quite messy due to the order of operations, need to check if the order is important
        for (Vector3ic location : event) {
            SignalNetworkNode consumerNode = toNode(location, connectingOnSides, 0,
                SignalNetworkNode.Type.CONSUMER);
            consumerNodes.add(consumerNode);
        }

        signalNetwork.removeLeafBlocks(consumerNodes, NetworkChangeReason.CHUNK_EVENT);
        for (SignalNetworkNode consumerNode : consumerNodes) {
            consumerSignalInNetworks.remove(consumerNode);
        }
    }


    @ReceiveEvent(components = {SignalConsumerComponent.class})
    public void consumerUpdated(OnChangedComponent event, EntityRef block) {

        if (block.hasComponent(BlockComponent.class)) {
            Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition(new Vector3i()));
            final SignalConsumerComponent consumerComponent = block.getComponent(SignalConsumerComponent.class);

            Set<SignalNetworkNode> oldLeafNodes = Sets.newHashSet(signalNetwork.getLeafNodesAt(location));
            for (SignalNetworkNode oldLeafNode : oldLeafNodes) {
                if (oldLeafNode.getType() == SignalNetworkNode.Type.CONSUMER) {
                    consumerSignalInNetworks.remove(oldLeafNode);
                    signalNetwork.removeLeafBlock(oldLeafNode, NetworkChangeReason.WORLD_CHANGE);
                }
            }

            SignalNetworkNode node = toNode(location, consumerComponent.connectionSides, 0,
                SignalNetworkNode.Type.CONSUMER);
            consumerSignalInNetworks.put(node, Maps.newHashMap());
            signalNetwork.addLeafBlock(node, NetworkChangeReason.WORLD_CHANGE);

            // Mode could have changed
            modifiedConsumers.add(node);
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalConsumerComponent.class})
    public void consumerRemoved(BeforeDestroyEvent event, EntityRef block) {
        Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition(new Vector3i()));
        byte connectingOnSides = block.getComponent(SignalConsumerComponent.class).connectionSides;

        final SignalNetworkNode consumerNode = toNode(location, connectingOnSides, 0, SignalNetworkNode.Type.CONSUMER);
        signalNetwork.removeLeafBlock(consumerNode, NetworkChangeReason.WORLD_CHANGE);
        consumerSignalInNetworks.remove(consumerNode);
    }

    /**
     * Represents a set of signals that belong to a network and their strengths.
     */
    private final class NetworkSignals {
        private Map<Side, Integer> signalStrengths = new HashMap<>();
        private byte sidesWithSignal;
        private byte sidesWithoutSignal;

        /**
         * Adds a signal with the given strength which is emitted from the given {@link Side}.
         *
         * @param side The {@link Side} from which the signal is emitted.
         * @param strength The strength of the signal.
         */
        private void addSignal(Side side, int strength) {
            signalStrengths.put(side, strength);
            if (strength != 0) {
                sidesWithSignal += SideBitFlag.getSide(side);
            } else {
                sidesWithoutSignal += SideBitFlag.getSide(side);
            }
        }
    }
}
