// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.componentSystem;

import org.joml.Vector3ic;
import org.terasology.blockNetwork.NetworkNode;

/**
 * Note that two nodes will be considered the same if their types are equal.
 */
public class SignalNetworkNode extends NetworkNode {
    public enum Type {
        PRODUCER, CONSUMER, CONDUCTOR
    }

    private Type type;

    public SignalNetworkNode(Vector3ic location, byte inputSides, byte outputSides, Type type) {
        super(location, inputSides, outputSides);
        this.type = type;
    }

    /**
     * @return Return the type of this Node. Can be any of {@link SignalNetworkNode.Type}
     */
    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        SignalNetworkNode that = (SignalNetworkNode) o;

        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
}
