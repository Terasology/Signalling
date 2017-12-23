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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import gnu.trove.iterator.TByteObjectIterator;
import gnu.trove.map.TByteObjectMap;
import gnu.trove.map.hash.TByteObjectHashMap;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.Rotation;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.math.geom.Vector3i;
import org.terasology.naming.Name;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockBuilderHelper;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockUri;
import org.terasology.world.block.family.BlockFamily;
import org.terasology.world.block.family.BlockFamilyFactory;
import org.terasology.world.block.family.RegisterBlockFamilyFactory;
import org.terasology.world.block.family.UpdatesWithNeighboursFamily;
import org.terasology.world.block.family.UpdatesWithNeighboursFamilyFactory;
import org.terasology.world.block.items.BlockItemComponent;
import org.terasology.world.block.items.OnBlockItemPlaced;
import org.terasology.world.block.loader.BlockFamilyDefinition;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * A factory that produces {@link BlockFamily BlockFamilies}.
 */
@RegisterBlockFamilyFactory("cable")
@RegisterSystem(RegisterMode.AUTHORITY)
public class SignalCableBlockFamilyFactory extends BaseComponentSystem implements BlockFamilyFactory  {
    
    
    @In
    private WorldProvider worldProvider;
    
    @In
    private BlockEntityRegistry blockEntityRegistry;
    
    private static final ImmutableSet<String> BLOCK_NAMES = ImmutableSet.of(
            UpdatesWithNeighboursFamilyFactory.NO_CONNECTIONS,
            UpdatesWithNeighboursFamilyFactory.ONE_CONNECTION,
            UpdatesWithNeighboursFamilyFactory.TWO_CONNECTIONS_LINE,
            UpdatesWithNeighboursFamilyFactory.TWO_CONNECTIONS_CORNER,
            UpdatesWithNeighboursFamilyFactory.THREE_CONNECTIONS_CORNER,
            UpdatesWithNeighboursFamilyFactory.THREE_CONNECTIONS_T,
            UpdatesWithNeighboursFamilyFactory.FOUR_CONNECTIONS_CROSS,
            UpdatesWithNeighboursFamilyFactory.FOUR_CONNECTIONS_SIDE,
            UpdatesWithNeighboursFamilyFactory.FIVE_CONNECTIONS,
            UpdatesWithNeighboursFamilyFactory.SIX_CONNECTIONS);
    
    private static final Map<String, Byte> DEFAULT_SHAPE_MAPPING = ImmutableMap.<String, Byte>builder()
            .put(UpdatesWithNeighboursFamilyFactory.NO_CONNECTIONS, (byte) 0)
            .put(UpdatesWithNeighboursFamilyFactory.ONE_CONNECTION, SideBitFlag.getSides(Side.BACK))
            
            .put(UpdatesWithNeighboursFamilyFactory.TWO_CONNECTIONS_LINE, SideBitFlag.getSides(Side.BACK, Side.FRONT))
            .put(UpdatesWithNeighboursFamilyFactory.TWO_CONNECTIONS_CORNER, SideBitFlag.getSides(Side.LEFT, Side.BACK))
            
            .put(UpdatesWithNeighboursFamilyFactory.THREE_CONNECTIONS_CORNER, SideBitFlag.getSides(Side.LEFT, Side.BACK, Side.TOP))
            .put(UpdatesWithNeighboursFamilyFactory.THREE_CONNECTIONS_T, SideBitFlag.getSides(Side.LEFT, Side.BACK, Side.FRONT))
            
            .put(UpdatesWithNeighboursFamilyFactory.FOUR_CONNECTIONS_CROSS, SideBitFlag.getSides(Side.RIGHT, Side.LEFT, Side.BACK, Side.FRONT))
            .put(UpdatesWithNeighboursFamilyFactory.FOUR_CONNECTIONS_SIDE, SideBitFlag.getSides(Side.LEFT, Side.BACK, Side.FRONT, Side.TOP))
            
            .put(UpdatesWithNeighboursFamilyFactory.FIVE_CONNECTIONS, SideBitFlag.getSides(Side.LEFT, Side.BACK, Side.FRONT, Side.TOP, Side.BOTTOM))
            .put(UpdatesWithNeighboursFamilyFactory.SIX_CONNECTIONS, (byte) 63)
            .build();
    
    
    
    public SignalCableBlockFamilyFactory(){
    }

    /**
     * Creates a new {@link BlockFamily} with a given set of definitions.
     *
     * @param definition A definition to create blocks with
     * @param blockBuilder A BlockBuilderHelper to create connections
     * @return A new {@link SignalUpdateFamily} with the given definitions, always non-null.
     */
    @Override
    public BlockFamily createBlockFamily(BlockFamilyDefinition definition, BlockBuilderHelper blockBuilder) {
        TByteObjectMap<String>[] basicBlocks = new TByteObjectMap[7];
        TByteObjectMap<Block> blocksForConnections = new TByteObjectHashMap<>();
        
        addConnections(basicBlocks, 0,  UpdatesWithNeighboursFamilyFactory.NO_CONNECTIONS);
        addConnections(basicBlocks, 1,  UpdatesWithNeighboursFamilyFactory.ONE_CONNECTION);
        addConnections(basicBlocks, 2,  UpdatesWithNeighboursFamilyFactory.TWO_CONNECTIONS_LINE);
        addConnections(basicBlocks, 2,  UpdatesWithNeighboursFamilyFactory.TWO_CONNECTIONS_CORNER);
        addConnections(basicBlocks, 3,  UpdatesWithNeighboursFamilyFactory.THREE_CONNECTIONS_CORNER);
        addConnections(basicBlocks, 3,  UpdatesWithNeighboursFamilyFactory.THREE_CONNECTIONS_T);
        addConnections(basicBlocks, 4,  UpdatesWithNeighboursFamilyFactory.FOUR_CONNECTIONS_CROSS);
        addConnections(basicBlocks, 4,  UpdatesWithNeighboursFamilyFactory.FOUR_CONNECTIONS_SIDE);
        addConnections(basicBlocks, 5,  UpdatesWithNeighboursFamilyFactory.FIVE_CONNECTIONS);
        addConnections(basicBlocks, 6,  UpdatesWithNeighboursFamilyFactory.SIX_CONNECTIONS);
        
        BlockUri blockUri = new BlockUri(definition.getUrn());
        
        // Now make sure we have all combinations based on the basic set (above) and rotations
        for (byte connections = 0; connections < 64; connections++) {
            // Only the allowed connections should be created
            if ((connections & (byte)63) == connections) {
                Block block = constructBlockForConnections(connections, blockBuilder, definition, basicBlocks);
                if (block == null) {
                    throw new IllegalStateException("Unable to find correct block definition for connections: " + connections);
                }
                block.setUri(new BlockUri(blockUri, new Name(String.valueOf(connections))));
                blocksForConnections.put(connections, block);
            }
        }
        
        final Block archetypeBlock = blocksForConnections.get(SideBitFlag.getSides(Side.RIGHT, Side.LEFT));
//        return new SignalUpdateFamily(blockUri, definition.getCategories(), archetypeBlock, blocksForConnections, (byte)63);
        return new SignalUpdateFamily(blockUri, definition.getCategories(),
                archetypeBlock, blocksForConnections, (byte)63);
    }

    /**
     * Links the given blocks together.
     * <p>
     * @param basicBlocks An array of blocks to link
     * @param index The index of the block to put a shape mapping
     * @param connections The connection type being added
     */
    private void addConnections(TByteObjectMap<String>[] basicBlocks, int index, String connections) {
        if (basicBlocks[index] == null) {
            basicBlocks[index] = new TByteObjectHashMap<>();
        }
        Byte val = DEFAULT_SHAPE_MAPPING.get(connections);
        if (val != null) {
            basicBlocks[index].put(DEFAULT_SHAPE_MAPPING.get(connections), connections);
        }
    }
    
    private Block constructBlockForConnections(final byte connections, final BlockBuilderHelper blockBuilder,
                                               BlockFamilyDefinition definition, TByteObjectMap<String>[] basicBlocks) {
        int connectionCount = SideBitFlag.getSides(connections).size();
        TByteObjectMap<String> possibleBlockDefinitions = basicBlocks[connectionCount];
        final TByteObjectIterator<String> blockDefinitionIterator = possibleBlockDefinitions.iterator();
        while (blockDefinitionIterator.hasNext()) {
            blockDefinitionIterator.advance();
            final byte originalConnections = blockDefinitionIterator.key();
            final String section = blockDefinitionIterator.value();
            Rotation rot = getRotationToAchieve(originalConnections, connections);
            if (rot != null) {
                return blockBuilder.constructTransformedBlock(definition, section, rot);
            }
        }
        return null;
    }
    
    private Rotation getRotationToAchieve(byte source, byte target) {
        Collection<Side> originalSides = SideBitFlag.getSides(source);
        
        Iterable<Rotation> rotations = /*horizontalOnly ? Rotation.horizontalRotations() :*/ Rotation.values();
        for (Rotation rot : rotations) {
            Set<Side> transformedSides = Sets.newHashSet();
            transformedSides.addAll(originalSides.stream().map(rot::rotate).collect(Collectors.toList()));
            
            byte transformedSide = SideBitFlag.getSides(transformedSides);
            if (transformedSide == target) {
                return rot;
            }
        }
        return null;
    }

    /**
     * Processes updates for a given {@link BlockComponent}'s neighbours when the block is placed.
     *
     * @param event A {@link OnBlockItemPlacedEvent} to fetch the {@link BlockComponent}
     * @param entity An ignored entity
     */
    @ReceiveEvent(components = {BlockItemComponent.class})
    public void onPlaceBlock(OnBlockItemPlaced event, EntityRef entity) {
        BlockComponent blockComponent = event.getPlacedBlock().getComponent(BlockComponent.class);
        if (blockComponent == null) {
            return;
        }
        
        Vector3i targetBlock = blockComponent.getPosition();
        processUpdateForBlockLocation(targetBlock);
    }
    
    private void processUpdateForBlockLocation(Vector3i blockLocation) {
        for (Side side : Side.values()) {
            Vector3i neighborLocation = new Vector3i(blockLocation);
            neighborLocation.add(side.getVector3i());
            if (worldProvider.isBlockRelevant(neighborLocation)) {
                Block neighborBlock = worldProvider.getBlock(neighborLocation);
                final BlockFamily blockFamily = neighborBlock.getBlockFamily();
                if (blockFamily instanceof SignalUpdateFamily) {
                    UpdatesWithNeighboursFamily neighboursFamily = (UpdatesWithNeighboursFamily) blockFamily;
                    Block neighborBlockAfterUpdate = neighboursFamily.getBlockForNeighborUpdate(worldProvider,blockEntityRegistry,neighborLocation, neighborBlock);
                    if (neighborBlock != neighborBlockAfterUpdate) {
                        worldProvider.setBlock(neighborLocation, neighborBlockAfterUpdate);
                    }
                }
            }
        }
    }
    

    @Override
    public Set<String> getSectionNames() {
        return BLOCK_NAMES;
    }
    
    
    
}
