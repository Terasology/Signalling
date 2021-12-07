// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.signalling.blockFamily;

import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.terasology.blockNetwork.BlockNetworkUtil;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.math.Rotation;
import org.terasology.engine.math.Side;
import org.terasology.engine.math.SideBitFlag;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockBuilderHelper;
import org.terasology.engine.world.block.BlockComponent;
import org.terasology.engine.world.block.BlockUri;
import org.terasology.engine.world.block.family.BlockSections;
import org.terasology.engine.world.block.family.MultiConnectFamily;
import org.terasology.engine.world.block.family.RegisterBlockFamily;
import org.terasology.engine.world.block.loader.BlockFamilyDefinition;
import org.terasology.engine.world.block.shapes.BlockShape;
import org.terasology.signalling.components.SignalConductorComponent;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalProducerComponent;

@RegisterBlockFamily("cable")
@BlockSections({"no_connections", "one_connection", "line_connection", "2d_corner", "3d_corner", "2d_t", "cross", "3d_side",
        "five_connections", "all"})
public class SignalCableBlockFamily extends MultiConnectFamily {
    public static final String NO_CONNECTIONS = "no_connections";
    public static final String ONE_CONNECTION = "one_connection";
    public static final String TWO_CONNECTIONS_LINE = "line_connection";
    public static final String TWO_CONNECTIONS_CORNER = "2d_corner";
    public static final String THREE_CONNECTIONS_CORNER = "3d_corner";
    public static final String THREE_CONNECTIONS_T = "2d_t";
    public static final String FOUR_CONNECTIONS_CROSS = "cross";
    public static final String FOUR_CONNECTIONS_SIDE = "3d_side";
    public static final String FIVE_CONNECTIONS = "five_connections";
    public static final String SIX_CONNECTIONS = "all";

    public SignalCableBlockFamily(BlockFamilyDefinition definition, BlockShape shape, BlockBuilderHelper blockBuilder) {
        super(definition, shape, blockBuilder);
    }

    public SignalCableBlockFamily(BlockFamilyDefinition definition, BlockBuilderHelper blockBuilder) {
        super(definition, blockBuilder);

        BlockUri blockUri = new BlockUri(definition.getUrn());

        this.registerBlock(blockUri, definition, blockBuilder, NO_CONNECTIONS, (byte) 0, Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, ONE_CONNECTION, SideBitFlag.getSides(Side.BACK), Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, TWO_CONNECTIONS_LINE, SideBitFlag.getSides(Side.BACK, Side.FRONT),
                Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, TWO_CONNECTIONS_CORNER, SideBitFlag.getSides(Side.LEFT, Side.BACK),
                Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, THREE_CONNECTIONS_CORNER, SideBitFlag.getSides(Side.LEFT, Side.BACK,
                Side.TOP), Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, THREE_CONNECTIONS_T, SideBitFlag.getSides(Side.LEFT, Side.BACK,
                Side.FRONT), Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, FOUR_CONNECTIONS_CROSS, SideBitFlag.getSides(Side.RIGHT, Side.LEFT,
                Side.BACK, Side.FRONT), Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, FOUR_CONNECTIONS_SIDE, SideBitFlag.getSides(Side.LEFT, Side.BACK,
                Side.FRONT, Side.TOP), Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, FIVE_CONNECTIONS, SideBitFlag.getSides(Side.LEFT, Side.BACK, Side.FRONT,
                Side.TOP, Side.BOTTOM), Rotation.allValues());
        this.registerBlock(blockUri, definition, blockBuilder, SIX_CONNECTIONS, SideBitFlag.getSides(Side.LEFT, Side.BACK, Side.FRONT,
                Side.TOP, Side.BOTTOM, Side.RIGHT), Rotation.allValues());
    }


    @Override
    public byte getConnectionSides() {
        return SideBitFlag.getSides(Side.LEFT, Side.BACK, Side.FRONT, Side.TOP, Side.BOTTOM, Side.RIGHT);
    }

    @Override
    public Block getArchetypeBlock() {
        return blocks.get(SideBitFlag.getSides(Side.RIGHT, Side.LEFT));
    }

    @Override
    protected boolean connectionCondition(Vector3ic blockLocation, Side connectSide) {
        Vector3i neighborLocation = new Vector3i(blockLocation);
        neighborLocation.add(connectSide.direction());

        byte sourceConnection = BlockNetworkUtil.getSourceConnections(worldProvider.getBlock(blockLocation),
                SideBitFlag.getSide(connectSide));

        boolean input = false;
        boolean output = false;

        Prefab prefab = this.getArchetypeBlock().getPrefab().get();
        for (SignalConductorComponent.ConnectionGroup connectionGroup
                : prefab.getComponent(SignalConductorComponent.class).connectionGroups) {
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
            if (neighborConsumerComponent != null && SideBitFlag.hasSide(BlockNetworkUtil.getResultConnections(block,
                    neighborConsumerComponent.connectionSides), oppositeDirection)) {
                return true;
            }
        }
        if (input) {
            final SignalProducerComponent neighborProducerComponent = neighborEntity.getComponent(SignalProducerComponent.class);
            return neighborProducerComponent != null && SideBitFlag.hasSide(BlockNetworkUtil.getResultConnections(block,
                    neighborProducerComponent.connectionSides), oppositeDirection);
        }

        return false;
    }

}
