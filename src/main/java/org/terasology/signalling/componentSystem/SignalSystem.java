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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockNetwork.EfficientBlockNetwork;
import org.terasology.blockNetwork.ImmutableBlockLocation;
import org.terasology.blockNetwork.Network2;
import org.terasology.blockNetwork.NetworkChangeReason;
import org.terasology.blockNetwork.NetworkNode;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.BeforeDeactivateComponent;
import org.terasology.entitySystem.entity.lifecycleEvents.OnActivatedComponent;
import org.terasology.entitySystem.entity.lifecycleEvents.OnChangedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.config.ModuleConfigManager;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.signalling.SignallingUtil;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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

    // we assume there can be only one consumer, one producer, and/or one conductor per block
    private Map<ImmutableBlockLocation, SignalNetworkNode> signalProducers;
    private Map<ImmutableBlockLocation, SignalNetworkNode> signalConsumers;
    private Multimap<ImmutableBlockLocation, SignalNetworkNode> signalConductors;

    // Used to detect producer changes
    private Map<SignalNetworkNode, Integer> producerSignalStrengths = Maps.newHashMap();

    private Set<SignalNetworkNode> modifiedProducers = Sets.newHashSet();
    private Set<SignalNetworkNode> modifiedConsumers = Sets.newHashSet();

    // Used to store signal for consumer from networks
    private Map<SignalNetworkNode, Map<Network2<SignalNetworkNode>, NetworkSignals>> consumerSignalInNetworks = Maps.newHashMap();

    private long lastUpdate;

    @Override
    public void initialise() {
        signalNetwork = new EfficientBlockNetwork<>();
        signalNetwork.addTopologyListener(signalNetworkState);
        signalProducers = Maps.newHashMap();
        signalConsumers = Maps.newHashMap();
        signalConductors = HashMultimap.create();
    }

    @Override
    public void preBegin() {
        processingMinimumInterval = moduleConfigManager.getIntVariable("Signalling", "processingMinimumInterval", 0);
        consumerCanPowerItself = moduleConfigManager.getBooleanVariable("Signalling", "consumerCanPowerItself", false);
    }

    @Override
    public void shutdown() {
        signalNetwork = null;
        signalProducers = null;
        signalConsumers = null;
        signalConductors = null;
    }

    @Override
    public void update(float delta) {
        long worldTime = time.getGameTimeInMs();
        if (worldTime > lastUpdate + processingMinimumInterval) {
            lastUpdate = worldTime;

            updateSignals();
        }
    }

    private void updateSignals() {
        // Gather all networks that might have their signal state modified
        Set<Network2<SignalNetworkNode>> networksToRecalculate = Sets.newHashSet(signalNetworkState.consumeNetworksToRecalculate());
        for (SignalNetworkNode modifiedProducer : modifiedProducers) {
            networksToRecalculate.addAll(signalNetworkState.getNetworksWithProducer(modifiedProducer));
        }

        // Gather all consumers that might be affected by the changes
        Set<SignalNetworkNode> consumersToEvaluate = Sets.newHashSet(signalNetworkState.consumeConsumersToRecalculate());

        for (Network2<SignalNetworkNode> network : networksToRecalculate) {
            if (signalNetwork.isNetworkActive(network)) {
                Collection<SignalNetworkNode> consumers = signalNetworkState.getConsumersInNetwork(network);
                for (SignalNetworkNode consumer : consumers) {
                    // Set the signal for each consumer in the affected network
                    NetworkSignals consumerSignalInNetwork = getConsumerSignalInNetwork(network, consumer);
                    consumerSignalInNetworks.get(consumer).put(network, consumerSignalInNetwork);
                }
                consumersToEvaluate.addAll(consumers);
            }
        }

        for (SignalNetworkNode modifiedConsumer : modifiedConsumers) {
            for (Network2<SignalNetworkNode> network : signalNetworkState.getNetworksWithConsumer(modifiedConsumer)) {
                NetworkSignals consumerSignalInNetwork = getConsumerSignalInNetwork(network, modifiedConsumer);
                consumerSignalInNetworks.get(modifiedConsumer).put(network, consumerSignalInNetwork);
            }
            consumersToEvaluate.add(modifiedConsumer);
        }

        // Clearing the changed states
        modifiedProducers.clear();
        modifiedConsumers.clear();

        // Set consumer status changes
        for (SignalNetworkNode consumerToEvaluate : consumersToEvaluate) {
            if (signalConsumers.containsValue(consumerToEvaluate)) {
                final EntityRef blockEntity = blockEntityRegistry.getBlockEntityAt(consumerToEvaluate.location.toVector3i());
                final SignalConsumerComponent consumerComponent = blockEntity.getComponent(SignalConsumerComponent.class);
                if (consumerComponent != null) {
                    Map<Network2<SignalNetworkNode>, NetworkSignals> consumerSignals = consumerSignalInNetworks.get(consumerToEvaluate);
                    removeSignalsOfNetworksNoLongerContainingConsumer(consumerToEvaluate, consumerSignals);

                    processSignalConsumerResult(consumerSignals.values(), consumerComponent, blockEntity);
                }
            }
        }
    }

    private void removeSignalsOfNetworksNoLongerContainingConsumer(SignalNetworkNode consumerToEvaluate, Map<Network2<SignalNetworkNode>, NetworkSignals> consumerSignals) {
        Collection<Network2<SignalNetworkNode>> networksWithConsumer = signalNetworkState.getNetworksWithConsumer(consumerToEvaluate);
        Iterator<Map.Entry<Network2<SignalNetworkNode>, NetworkSignals>> signalInNetworkIterator = consumerSignals.entrySet().iterator();
        while (signalInNetworkIterator.hasNext()) {
            Map.Entry<Network2<SignalNetworkNode>, NetworkSignals> signalEntry = signalInNetworkIterator.next();
            if (!networksWithConsumer.contains(signalEntry.getKey())) {
                signalInNetworkIterator.remove();
            }
        }
    }

    private void processSignalConsumerResult(Collection<NetworkSignals> networkSignals, SignalConsumerComponent signalConsumerComponent, EntityRef entity) {
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

    private void outputSignalToAdvancedConsumer(EntityRef entity, Collection<NetworkSignals> networkSignals) {
        final SignalConsumerAdvancedStatusComponent advancedStatusComponent = entity.getComponent(SignalConsumerAdvancedStatusComponent.class);
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

    private void outputSignalToSimpleConsumer(EntityRef entity, boolean result) {
        final SignalConsumerStatusComponent consumerStatusComponent = entity.getComponent(SignalConsumerStatusComponent.class);
        if (consumerStatusComponent.hasSignal != result) {
            consumerStatusComponent.hasSignal = result;
            entity.saveComponent(consumerStatusComponent);
            if (logger.isDebugEnabled()) {
                logger.debug("Consumer has signal: " + result);
            }
        }
    }

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

    //
    private NetworkSignals getConsumerSignalInNetwork(Network2<SignalNetworkNode> network, SignalNetworkNode consumerNode) {
        // Check for infinite signal strength (-1), if there - it powers whole network
        Collection<SignalNetworkNode> producers = signalNetworkState.getProducersInNetwork(network);
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

    private int getMaxSignalInNetworkOnSide(Network2<SignalNetworkNode> network, SignalNetworkNode consumerNode, Collection<SignalNetworkNode> producers, Side sideInNetwork) {
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


    private SignalNetworkNode toNode(Vector3i location, byte definedSides, SignalNetworkNode.Type type) {
        return new SignalNetworkNode(location, getConnections(location, definedSides), type);
    }

    private byte getConnections(Vector3i location, byte definedSides) {
        return SignallingUtil.getResultConnections(worldProvider.getBlock(location), definedSides);
    }

    /*
     * ****************************** Conductor events ********************************
     */

    @ReceiveEvent(components = {SignalConductorComponent.class})
    public void prefabConductorLoaded(OnActivatedBlocks event, EntityRef blockType) {
        for (Byte connectionGroup : blockType.getComponent(SignalConductorComponent.class).connectionGroups) {
            Set<SignalNetworkNode> conductorNodes = Sets.newHashSet();
            for (Vector3i location : event.getBlockPositions()) {
                final SignalNetworkNode conductorNode = toNode(location, connectionGroup, SignalNetworkNode.Type.CONDUCTOR);
                conductorNodes.add(conductorNode);

                signalConductors.put(conductorNode.location, conductorNode);
            }
            signalNetwork.addNetworkingBlocks(conductorNodes, NetworkChangeReason.CHUNK_EVENT);
        }
    }

    @ReceiveEvent(components = {SignalConductorComponent.class})
    public void prefabConductorUnloaded(BeforeDeactivateBlocks event, EntityRef blockType) {
        for (Byte connectionGroup : blockType.getComponent(SignalConductorComponent.class).connectionGroups) {
            Set<SignalNetworkNode> conductorNodes = Sets.newHashSet();
            // Quite messy due to the order of operations, need to check if the order is important
            for (Vector3i location : event.getBlockPositions()) {
                final SignalNetworkNode conductorNode = toNode(location, connectionGroup, SignalNetworkNode.Type.CONDUCTOR);
                conductorNodes.add(conductorNode);
            }
            signalNetwork.removeNetworkingBlocks(conductorNodes, NetworkChangeReason.CHUNK_EVENT);
            for (NetworkNode conductorNode : conductorNodes) {
                signalConductors.removeAll(conductorNode.location);
            }
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalConductorComponent.class})
    public void conductorAdded(OnActivatedComponent event, EntityRef block) {
        final Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
        for (Byte connectionGroup : block.getComponent(SignalConductorComponent.class).connectionGroups) {
            final SignalNetworkNode conductorNode = toNode(location, connectionGroup, SignalNetworkNode.Type.CONDUCTOR);

            signalConductors.put(conductorNode.location, conductorNode);
            signalNetwork.addNetworkingBlock(conductorNode, NetworkChangeReason.WORLD_CHANGE);
        }
    }

    @ReceiveEvent(components = {SignalConductorComponent.class, BlockComponent.class})
    public void conductorUpdated(OnChangedComponent event, EntityRef block) {
        final Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
        final ImmutableBlockLocation blockLocation = new ImmutableBlockLocation(location);
        Collection<SignalNetworkNode> oldSignalNodes = signalConductors.get(blockLocation);
        if (oldSignalNodes.size() > 0) {
            signalNetwork.removeNetworkingBlocks(oldSignalNodes, NetworkChangeReason.WORLD_CHANGE);
        }

        for (Byte connectionGroup : block.getComponent(SignalConductorComponent.class).connectionGroups) {
            final SignalNetworkNode newConductorNode = toNode(new Vector3i(location), connectionGroup, SignalNetworkNode.Type.CONDUCTOR);
            signalConductors.put(newConductorNode.location, newConductorNode);
            signalNetwork.addNetworkingBlock(newConductorNode, NetworkChangeReason.WORLD_CHANGE);
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalConductorComponent.class})
    public void conductorRemoved(BeforeDeactivateComponent event, EntityRef block) {
        final Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
        for (Byte connectionGroup : block.getComponent(SignalConductorComponent.class).connectionGroups) {
            final SignalNetworkNode conductorNode = toNode(location, connectionGroup, SignalNetworkNode.Type.CONDUCTOR);
            signalNetwork.removeNetworkingBlock(conductorNode, NetworkChangeReason.WORLD_CHANGE);
        }
        signalConductors.removeAll(new ImmutableBlockLocation(location));
    }

    /*
     * ****************************** Producer events ********************************
     */

    @ReceiveEvent(components = {SignalProducerComponent.class})
    public void prefabProducerLoaded(OnActivatedBlocks event, EntityRef blockType) {
        final SignalProducerComponent producerComponent = blockType.getComponent(SignalProducerComponent.class);
        int signalStrength = producerComponent.signalStrength;
        Set<SignalNetworkNode> producerNodes = Sets.newHashSet();
        for (Vector3i location : event.getBlockPositions()) {
            final SignalNetworkNode producerNode = toNode(location, producerComponent.connectionSides, SignalNetworkNode.Type.PRODUCER);

            signalProducers.put(producerNode.location, producerNode);
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
        for (Vector3i location : event.getBlockPositions()) {
            final SignalNetworkNode producerNode = toNode(location, connectingOnSides, SignalNetworkNode.Type.PRODUCER);
            producerNodes.add(producerNode);
        }

        signalNetwork.removeLeafBlocks(producerNodes, NetworkChangeReason.CHUNK_EVENT);
        for (SignalNetworkNode producerNode : producerNodes) {
            signalProducers.remove(producerNode.location);
            producerSignalStrengths.remove(producerNode);
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalProducerComponent.class})
    public void producerAdded(OnActivatedComponent event, EntityRef block) {
        Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
        final SignalProducerComponent producerComponent = block.getComponent(SignalProducerComponent.class);
        final int signalStrength = producerComponent.signalStrength;
        byte connectingOnSides = producerComponent.connectionSides;

        final SignalNetworkNode producerNode = toNode(location, connectingOnSides, SignalNetworkNode.Type.PRODUCER);

        signalProducers.put(producerNode.location, producerNode);
        producerSignalStrengths.put(producerNode, signalStrength);
        signalNetwork.addLeafBlock(producerNode, NetworkChangeReason.WORLD_CHANGE);
    }

    @ReceiveEvent(components = {SignalProducerComponent.class})
    public void producerUpdated(OnChangedComponent event, EntityRef block) {
        if (logger.isDebugEnabled()) {
            logger.debug("Producer updated: " + block.getParentPrefab());
        }
        if (block.hasComponent(BlockComponent.class)) {
            Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
            ImmutableBlockLocation blockLocation = new ImmutableBlockLocation(location);
            final SignalProducerComponent producerComponent = block.getComponent(SignalProducerComponent.class);

            // We need to figure out, what exactly was changed
            final byte oldConnectionSides = signalProducers.get(blockLocation).connectionSides;
            byte newConnectionSides = getConnections(blockLocation.toVector3i(), producerComponent.connectionSides);

            SignalNetworkNode node = toNode(location, newConnectionSides, SignalNetworkNode.Type.PRODUCER);
            SignalNetworkNode oldNode = toNode(location, oldConnectionSides, SignalNetworkNode.Type.PRODUCER);
            if (oldConnectionSides != newConnectionSides) {
                producerSignalStrengths.put(node, producerComponent.signalStrength);
                signalProducers.put(node.location, node);
                signalNetwork.updateLeafBlock(oldNode, node, NetworkChangeReason.WORLD_CHANGE);
                producerSignalStrengths.remove(oldNode);
            } else {
                int oldSignalStrength = producerSignalStrengths.get(oldNode);
                int newSignalStrength = producerComponent.signalStrength;
                if (oldSignalStrength != newSignalStrength) {
                    producerSignalStrengths.put(node, newSignalStrength);
                    modifiedProducers.add(node);
                }
            }
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalProducerComponent.class})
    public void producerRemoved(BeforeDeactivateComponent event, EntityRef block) {
        Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
        byte connectingOnSides = block.getComponent(SignalProducerComponent.class).connectionSides;

        final SignalNetworkNode producerNode = toNode(location, connectingOnSides, SignalNetworkNode.Type.PRODUCER);
        signalNetwork.removeLeafBlock(producerNode, NetworkChangeReason.WORLD_CHANGE);
        signalProducers.remove(producerNode.location);
        producerSignalStrengths.remove(producerNode);
    }

    /*
     * ****************************** Consumer events ********************************
     */

    @ReceiveEvent(components = {SignalConsumerComponent.class})
    public void prefabConsumerLoaded(OnActivatedBlocks event, EntityRef blockType) {
        byte connectingOnSides = blockType.getComponent(SignalConsumerComponent.class).connectionSides;
        Set<SignalNetworkNode> consumerNodes = Sets.newHashSet();
        for (Vector3i location : event.getBlockPositions()) {
            SignalNetworkNode consumerNode = toNode(location, connectingOnSides, SignalNetworkNode.Type.CONSUMER);

            signalConsumers.put(consumerNode.location, consumerNode);
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
        for (Vector3i location : event.getBlockPositions()) {
            SignalNetworkNode consumerNode = toNode(location, connectingOnSides, SignalNetworkNode.Type.CONSUMER);
            consumerNodes.add(consumerNode);
        }

        signalNetwork.removeLeafBlocks(consumerNodes, NetworkChangeReason.CHUNK_EVENT);
        for (SignalNetworkNode consumerNode : consumerNodes) {
            signalConsumers.remove(consumerNode.location);
            consumerSignalInNetworks.remove(consumerNode);
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalConsumerComponent.class})
    public void consumerAdded(OnActivatedComponent event, EntityRef block) {
        Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
        byte connectingOnSides = block.getComponent(SignalConsumerComponent.class).connectionSides;

        SignalNetworkNode consumerNode = toNode(location, connectingOnSides, SignalNetworkNode.Type.CONSUMER);

        signalConsumers.put(consumerNode.location, consumerNode);
        consumerSignalInNetworks.put(consumerNode, Maps.newHashMap());
        signalNetwork.addLeafBlock(consumerNode, NetworkChangeReason.WORLD_CHANGE);
    }

    @ReceiveEvent(components = {SignalConsumerComponent.class})
    public void consumerUpdated(OnChangedComponent event, EntityRef block) {
        if (block.hasComponent(BlockComponent.class)) {
            Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
            ImmutableBlockLocation blockLocation = new ImmutableBlockLocation(location);
            final SignalConsumerComponent consumerComponent = block.getComponent(SignalConsumerComponent.class);

            // We need to figure out, what exactly was changed
            final byte oldConnectionSides = signalConsumers.get(blockLocation).connectionSides;
            byte newConnectionSides = getConnections(blockLocation.toVector3i(), consumerComponent.connectionSides);

            SignalNetworkNode node = toNode(location, newConnectionSides, SignalNetworkNode.Type.CONSUMER);
            if (oldConnectionSides != newConnectionSides) {
                signalConsumers.put(node.location, node);
                SignalNetworkNode oldNode = toNode(location, oldConnectionSides, SignalNetworkNode.Type.CONSUMER);
                consumerSignalInNetworks.put(node, Maps.newHashMap());

                signalNetwork.updateLeafBlock(oldNode, node, NetworkChangeReason.WORLD_CHANGE);

                consumerSignalInNetworks.remove(oldNode);
            }
            // Mode could have changed
            modifiedConsumers.add(node);
        }
    }

    @ReceiveEvent(components = {BlockComponent.class, SignalConsumerComponent.class})
    public void consumerRemoved(BeforeDeactivateComponent event, EntityRef block) {
        Vector3i location = new Vector3i(block.getComponent(BlockComponent.class).getPosition());
        byte connectingOnSides = block.getComponent(SignalConsumerComponent.class).connectionSides;

        final SignalNetworkNode consumerNode = toNode(location, connectingOnSides, SignalNetworkNode.Type.CONSUMER);
        signalNetwork.removeLeafBlock(consumerNode, NetworkChangeReason.WORLD_CHANGE);
        signalConsumers.remove(consumerNode.location);
        consumerSignalInNetworks.remove(consumerNode);
    }

    private final class NetworkSignals {
        private Map<Side, Integer> signalStrengths = new HashMap<>();
        private byte sidesWithSignal;
        private byte sidesWithoutSignal;

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
