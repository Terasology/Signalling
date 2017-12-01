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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.terasology.blockNetwork.EfficientNetworkTopologyListener;
import org.terasology.blockNetwork.Network2;
import org.terasology.blockNetwork.NetworkChangeReason;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A class that stores the states of {@link SignalNetworkNode}s.
 */
public class SignalEfficientNetworkState implements EfficientNetworkTopologyListener<SignalNetworkNode> {
    private Set<Network2<SignalNetworkNode>> networksToRecalculate = Sets.newHashSet();
    private Set<SignalNetworkNode> consumersToRecalculate = Sets.newHashSet();

    private Iterable<SignalNetworkNode> getConsumersInNetwork(Network2<SignalNetworkNode> network) {
        return Iterables.filter(network.getLeafNodes(),
                new Predicate<SignalNetworkNode>() {
                    @Override
                    public boolean apply(@Nullable SignalNetworkNode input) {
                        return input.getType() == SignalNetworkNode.Type.CONSUMER;
                    }
                });
    }

    /**
     * Forces a recalculation for this SignalEfficientNetworkState's networks.
     *
     * @return A collection of {@link SignalNetworkNode nodes}.
     */
    public Collection<Network2<SignalNetworkNode>> consumeNetworksToRecalculate() {
        Set<Network2<SignalNetworkNode>> result = networksToRecalculate;
        networksToRecalculate = Sets.newHashSet();
        return Collections.unmodifiableCollection(result);
    }

    public Collection<SignalNetworkNode> consumeConsumersToRecalculate() {
        Set<SignalNetworkNode> result = consumersToRecalculate;
        consumersToRecalculate = Sets.newHashSet();
        return Collections.unmodifiableCollection(result);
    }

    /**
     * Adds the given network to a set of networks to recalculate.
     *
     * @param network A network to eventually recalculate, must be non-null
     * @param reason An ignored reason
     */
    @Override
    public void networkAdded(Network2<SignalNetworkNode> network, NetworkChangeReason reason) {
        networksToRecalculate.add(network);
    }

    /**
     * Adds the nodes in the given network to a set to recalculate.
     *
     * @param network A network to eventually recalculate, must be non-null
     * @param reason An ignored reason
     */
    @Override
    public void networkRemoved(Network2<SignalNetworkNode> network, NetworkChangeReason reason) {
        for (SignalNetworkNode signalNetworkNode : getConsumersInNetwork(network)) {
            consumersToRecalculate.add(signalNetworkNode);
        }
    }

    /**
     * Adds the given network to a set of networks to recalculate.
     *
     * @param network A network to eventually recalculate, must be non-null
     * @param reason An ignored reason
     */
    @Override
    public void networkingNodesAdded(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> networkingNodes, NetworkChangeReason reason) {
        networksToRecalculate.add(network);
    }

    /**
     * Adds the given network node to a set of networks to recalculate.
     *
     * @param network A network to eventually recalculate, must be non-null
     * @param networkingNodes A set of ignored nodes
     * @param reason An ignored reason
     */
    @Override
    public void networkingNodesRemoved(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> networkingNodes, NetworkChangeReason reason) {
        networksToRecalculate.add(network);
    }

    /**
     * Adds the given network node to a set of networks to recalculate.
     *
     * @param network A network to eventually recalculate, must be non-null
     * @param reason An ignored reason
     * @param leafNodes
     */
    @Override
    public void leafNodesAdded(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> leafNodes, NetworkChangeReason reason) {
        for (SignalNetworkNode modifiedLeafNode : leafNodes) {
            if (modifiedLeafNode.getType() == SignalNetworkNode.Type.PRODUCER) {
                networksToRecalculate.add(network);
            } else {
                consumersToRecalculate.add(modifiedLeafNode);
            }
        }
    }

    /**
     * Adds all the {@link SignalNetworkNode}s into a set of consumers to recalculate.
     *
     * @param network A network to add to a set of networks to recalculate if its type is a producer.
     * @param leafNodes A set of nodes to extract types out of
     * @param reason An ignored reason
     */
    @Override
    public void leafNodesRemoved(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> leafNodes, NetworkChangeReason reason) {
        for (SignalNetworkNode modifiedLeafNode : leafNodes) {
            if (modifiedLeafNode.getType() == SignalNetworkNode.Type.PRODUCER) {
                networksToRecalculate.add(network);
            } else {
                consumersToRecalculate.add(modifiedLeafNode);
            }
        }
    }
}
