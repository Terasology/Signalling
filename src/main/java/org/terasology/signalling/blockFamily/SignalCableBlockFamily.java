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
package org.terasology.signalling.blockFamily;

import org.terasology.blockNetwork.BlockNetworkUtil;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.math.geom.Vector3i;
import org.terasology.signalling.components.SignalConductorComponent;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalProducerComponent;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockBuilderHelper;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.family.MultiConnectBlockFamily;
import org.terasology.world.block.family.RegisterBlockFamily;
import org.terasology.world.block.loader.BlockFamilyDefinition;
import org.terasology.world.block.shapes.BlockShape;

/**
 * @author Marcin Sciesinski <marcins78@gmail.com>
 */
@RegisterBlockFamily("cable")
public class SignalCableBlockFamily extends MultiConnectBlockFamily {
    public SignalCableBlockFamily(BlockFamilyDefinition definition, BlockShape shape, BlockBuilderHelper blockBuilder) {
        super(definition, shape, blockBuilder);
    }

    public SignalCableBlockFamily(BlockFamilyDefinition definition, BlockBuilderHelper blockBuilder) {
        super(definition, blockBuilder);
    }

    @Override
    public byte getConnectionSides() {
        return 63;
    }

    @Override
    public Block getBlockForNeighborUpdate(Vector3i location, Block oldBlock) {
        return super.getBlockForNeighborUpdate(location, oldBlock);
    }

    @Override
    public boolean connectionCondition(Vector3i blockLocation, Side connectSide) {
        Vector3i neighborLocation = new Vector3i(blockLocation);
        neighborLocation.add(connectSide.getVector3i());

        byte sourceConnection = BlockNetworkUtil.getSourceConnections(worldProvider.getBlock(blockLocation), SideBitFlag.getSide(connectSide));

        boolean input = false;
        boolean output = false;

        Prefab prefab = this.getArchetypeBlock().getPrefab().get();
        for (SignalConductorComponent.ConnectionGroup connectionGroup : prefab.getComponent(SignalConductorComponent.class).connectionGroups) {
            input |= (connectionGroup.inputSides & sourceConnection) > 0;
            output |= (connectionGroup.outputSides & sourceConnection) > 0;
        }


        if (!input && !output) {
            return false;
        }
        EntityRef neighborEntity = blockEntityRegistry.getBlockEntityAt(neighborLocation);
        return neighborEntity != null && connectsToNeighbor(connectSide, input, output, neighborEntity);
    }


    private boolean connectsToNeighbor(Side connectSide, boolean input, boolean output, EntityRef neighborEntity) {
        final Side oppositeDirection = connectSide.reverse();

        Block block = neighborEntity.getComponent(BlockComponent.class).getBlock();

        final SignalConductorComponent neighborConductorComponent = neighborEntity.getComponent(SignalConductorComponent.class);
        if (neighborConductorComponent != null) {
            if (output) {
                for (SignalConductorComponent.ConnectionGroup connectionGroup : neighborConductorComponent.connectionGroups) {
                    if (SideBitFlag.hasSide(BlockNetworkUtil.getResultConnections(block, connectionGroup.inputSides), oppositeDirection)) {
                        return true;
                    }
                }
            }
            if (input) {
                for (SignalConductorComponent.ConnectionGroup connectionGroup : neighborConductorComponent.connectionGroups) {
                    if (SideBitFlag.hasSide(BlockNetworkUtil.getResultConnections(block, connectionGroup.inputSides), oppositeDirection)) {
                        return true;
                    }
                }
            }
        }

        if (output) {
            final SignalConsumerComponent neighborConsumerComponent = neighborEntity.getComponent(SignalConsumerComponent.class);
            if (neighborConsumerComponent != null && SideBitFlag.hasSide(BlockNetworkUtil.getResultConnections(block, neighborConsumerComponent.connectionSides), oppositeDirection)) {
                return true;
            }
        }
        if (input) {
            final SignalProducerComponent neighborProducerComponent = neighborEntity.getComponent(SignalProducerComponent.class);
            if (neighborProducerComponent != null && SideBitFlag.hasSide(BlockNetworkUtil.getResultConnections(block, neighborProducerComponent.connectionSides), oppositeDirection)) {
                return true;
            }
        }

        return false;
    }

}
