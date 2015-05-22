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
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.terasology.blockNetwork.Network2;
import org.terasology.blockNetwork.NetworkChangeReason;
import org.terasology.blockNetwork.EfficientNetworkTopologyListener;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class SignalEfficientNetworkState implements EfficientNetworkTopologyListener<SignalNetworkNode> {
    private Multimap<SignalNetworkNode, Network2<SignalNetworkNode>> producerNetworks = HashMultimap.create();
    private Multimap<Network2<SignalNetworkNode>, SignalNetworkNode> producersInNetwork = HashMultimap.create();

    private Multimap<SignalNetworkNode, Network2<SignalNetworkNode>> consumerNetworks = HashMultimap.create();
    private Multimap<Network2<SignalNetworkNode>, SignalNetworkNode> consumersInNetwork = HashMultimap.create();

    private Set<Network2<SignalNetworkNode>> networksToRecalculate = Sets.newHashSet();
    private Set<SignalNetworkNode> consumersToRecalculate = Sets.newHashSet();

    public Collection<SignalNetworkNode> getProducersInNetwork(Network2<SignalNetworkNode> network) {
        return Collections.unmodifiableCollection(producersInNetwork.get(network));
    }

    public Collection<SignalNetworkNode> getConsumersInNetwork(Network2<SignalNetworkNode> network) {
        return Collections.unmodifiableCollection(consumersInNetwork.get(network));
    }

    public Collection<Network2<SignalNetworkNode>> getNetworksWithProducer(SignalNetworkNode node) {
        return Collections.unmodifiableCollection(producerNetworks.get(node));
    }

    public Collection<Network2<SignalNetworkNode>> getNetworksWithConsumer(SignalNetworkNode node) {
        return Collections.unmodifiableCollection(consumerNetworks.get(node));
    }

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

    @Override
    public void networkAdded(Network2<SignalNetworkNode> network, NetworkChangeReason reason) {
        networksToRecalculate.add(network);
        for (SignalNetworkNode signalNetworkNode : network.getLeafNodes()) {
            if (signalNetworkNode.getType() == SignalNetworkNode.Type.CONSUMER) {
                consumerNetworks.put(signalNetworkNode, network);
                consumersInNetwork.put(network, signalNetworkNode);
            } else {
                producerNetworks.put(signalNetworkNode, network);
                producersInNetwork.put(network, signalNetworkNode);
            }
        }
    }

    @Override
    public void networkRemoved(Network2<SignalNetworkNode> network, NetworkChangeReason reason) {
        for (SignalNetworkNode signalNetworkNode : producersInNetwork.removeAll(network)) {
            producerNetworks.remove(signalNetworkNode, network);
        }
        for (SignalNetworkNode signalNetworkNode : consumersInNetwork.removeAll(network)) {
            consumerNetworks.remove(signalNetworkNode, network);
            consumersToRecalculate.add(signalNetworkNode);
        }
    }

    @Override
    public void networkingNodesAdded(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> networkingNodes, NetworkChangeReason reason) {
        networksToRecalculate.add(network);
    }

    @Override
    public void networkingNodesRemoved(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> networkingNodes, NetworkChangeReason reason) {
        networksToRecalculate.add(network);
    }

    @Override
    public void leafNodesAdded(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> leafNodes, NetworkChangeReason reason) {
        for (SignalNetworkNode modifiedLeafNode : leafNodes) {
            if (modifiedLeafNode.getType() == SignalNetworkNode.Type.PRODUCER) {
                networksToRecalculate.add(network);
                producerNetworks.put(modifiedLeafNode, network);
                producersInNetwork.put(network, modifiedLeafNode);
            } else {
                consumersToRecalculate.add(modifiedLeafNode);
                consumerNetworks.put(modifiedLeafNode, network);
                consumersInNetwork.put(network, modifiedLeafNode);
            }
        }
    }

    @Override
    public void leafNodesRemoved(Network2<SignalNetworkNode> network, Set<SignalNetworkNode> leafNodes, NetworkChangeReason reason) {
        for (SignalNetworkNode modifiedLeafNode : leafNodes) {
            if (modifiedLeafNode.getType() == SignalNetworkNode.Type.PRODUCER) {
                networksToRecalculate.add(network);
                producerNetworks.remove(modifiedLeafNode, network);
                producersInNetwork.remove(network, modifiedLeafNode);
            } else {
                consumersToRecalculate.add(modifiedLeafNode);
                consumerNetworks.remove(modifiedLeafNode, network);
                consumersInNetwork.remove(network, modifiedLeafNode);
            }
        }
    }
}
