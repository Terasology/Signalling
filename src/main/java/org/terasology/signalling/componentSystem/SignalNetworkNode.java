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

import org.terasology.blockNetwork.NetworkNode;
import org.terasology.math.geom.Vector3i;

/**
 * @author Marcin Sciesinski <marcins78@gmail.com>
 */
public class SignalNetworkNode extends NetworkNode {
    public enum Type {
        PRODUCER, CONSUMER, CONDUCTOR
    }

    private Type type;

    public SignalNetworkNode(Vector3i location, byte connectionSides, Type type) {
        super(location, (type == Type.PRODUCER) ? 0 : connectionSides, (type == Type.CONSUMER) ? 0 : connectionSides);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SignalNetworkNode that = (SignalNetworkNode) o;

        if (type != that.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
}
