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

import org.joml.RoundingMode;
import org.joml.Vector3i;
import org.terasology.blockNetwork.block.family.RotationBlockFamily;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.common.ActivateEvent;
import org.terasology.engine.math.Rotation;
import org.terasology.engine.math.Side;
import org.terasology.engine.registry.In;
import org.terasology.engine.world.BlockEntityRegistry;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.family.BlockFamily;
import org.terasology.engine.world.block.family.SideDefinedBlockFamily;
import org.terasology.gestalt.entitysystem.event.ReceiveEvent;
import org.terasology.signalling.components.RotateableByScrewdriverComponent;
import org.terasology.signalling.components.ScrewdriverComponent;

import java.util.EnumMap;

/**
 * Component system that handles {@link ScrewdriverComponent} actions.
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class ScrewdriverSystem extends BaseComponentSystem {
    @In
    private WorldProvider worldProvider;
    @In
    private BlockEntityRegistry blockEntityRegistry;

    /** This map holds the order in which rotation should happen */
    private EnumMap<Side, Side> sideOrder = new EnumMap<>(Side.class);

    /** Initializes the order of rotation */
    @Override
    public void initialise() {
        sideOrder.put(Side.FRONT, Side.LEFT);
        sideOrder.put(Side.LEFT, Side.BACK);
        sideOrder.put(Side.BACK, Side.RIGHT);
        sideOrder.put(Side.RIGHT, Side.TOP);
        sideOrder.put(Side.TOP, Side.BOTTOM);
        sideOrder.put(Side.BOTTOM, Side.FRONT);
    }

    /**
     * Event fired when a {@link ScrewdriverComponent} item is used on a {@link RotateableByScrewdriverComponent} entity.
     *
     * @param event The activation event holding the rotated target entity
     * @param screwdriver The screwdriver item doing the rotating
     */
    @ReceiveEvent(components = {ScrewdriverComponent.class})
    public void rotateGate(ActivateEvent event, EntityRef screwdriver) {
        final EntityRef target = event.getTarget();
        if (target.hasComponent(RotateableByScrewdriverComponent.class)) {
            final Vector3i targetLocation = new Vector3i(event.getTargetLocation(), RoundingMode.FLOOR);
            final Block block = worldProvider.getBlock(targetLocation);
            final BlockFamily blockFamily = block.getBlockFamily();
            if (blockFamily instanceof SideDefinedBlockFamily) {
                final SideDefinedBlockFamily sideDefinedBlockFamily = (SideDefinedBlockFamily) blockFamily;
                // Figure out the next block and side
                Side newSide = block.getDirection();
                Block blockForSide;
                do {
                    newSide = sideOrder.get(newSide);
                    blockForSide = sideDefinedBlockFamily.getBlockForSide(newSide);
                } while (blockForSide == null);

                worldProvider.setBlock(targetLocation, blockForSide);
            } else if (blockFamily instanceof RotationBlockFamily) {
                RotationBlockFamily rotationBlockFamily = (RotationBlockFamily) blockFamily;
                Side clickedSide = Side.inDirection(event.getHitNormal());
                Block rotatedBlock = getBlockForClockwiseRotation(rotationBlockFamily, block, clickedSide);
                if (rotatedBlock != null) {
                    worldProvider.setBlock(targetLocation, rotatedBlock);
                }
            }
        }
    }

    /**
     * Gets the next rotated version of the given block in its {@link RotationBlockFamily} assuming a clockwise rotation.
     *
     * @param rotationBlockFamily The BlockFamily that contains the various rotations of the target block
     * @param currentBlock The block being rotated
     * @param sideToRotateAround The target side of the block being rotated
     *
     * @return The next rotated block from the current block after a clockwise rotation.
     */
    private Block getBlockForClockwiseRotation(RotationBlockFamily rotationBlockFamily, Block currentBlock, Side sideToRotateAround) {
        // This definitely can be done more efficiently, but I'm too lazy to figure it out and it's going to be
        // invoked once in a blue moon anyway, so we can do it the hard way
        Rotation currentRotation = rotationBlockFamily.getRotation(currentBlock);

        // Pick a side we want to rotate
        SideMapping sideMapping = findSideMappingForSide(sideToRotateAround);

        // Find which side the side we want to keep was originally at
        Side originalSide = findOriginalSide(currentRotation, sideToRotateAround);

        // Find which side we want to rotate was originally at
        Side originalRotatedSide = findOriginalSide(currentRotation, sideMapping.originalSide);

        // This is the side we want the leftRelativeToEndUpAt
        Side resultRotatedSide = sideMapping.resultSide;

        Rotation resultRotation = findDesiredRotation(originalSide, sideToRotateAround, originalRotatedSide, resultRotatedSide);

        return rotationBlockFamily.getBlockForRotation(resultRotation);
    }

    /**
     * Gets the corresponding side that should be rotated given a target side that should be rotated around.
     *
     * @param side the side the rotation is targetted around
     *
     * @return The side that should be rotated given the side the rotation is targeted around
     */
    private SideMapping findSideMappingForSide(Side side) {
        switch (side) {
            case TOP:
                return new SideMapping(Side.RIGHT, Side.FRONT);
            case BOTTOM:
                return new SideMapping(Side.RIGHT, Side.BACK);
            case RIGHT:
                return new SideMapping(Side.FRONT, Side.TOP);
            case LEFT:
                return new SideMapping(Side.FRONT, Side.BOTTOM);
            case FRONT:
                return new SideMapping(Side.TOP, Side.RIGHT);
            default:
                return new SideMapping(Side.TOP, Side.LEFT);
        }
    }

    /**
     * Finds the rotation that would rotate the given original side to the relative result side,
     * and the original left side to its result side.
     *
     * @param originalSide The pre-rotation side
     * @param relativeSide The post-rotation target for the originalSide
     * @param originalLeftSide The pre-rotation left side
     * @param resultSide The post-rotation target for the originalLeftSide
     *
     * @return The rotation that transforms the given two original sides to the their respective resultant sides.
     */
    private Rotation findDesiredRotation(Side originalSide, Side relativeSide, Side originalLeftSide, Side resultSide) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(originalSide) == relativeSide
                    && rotation.rotate(originalLeftSide) == resultSide) {
                return rotation;
            }
        }
        return null;
    }

    /**
     * Finds the original (pre-rotation) side for which the given rotation would have produced the result side.
     *
     * @param rotation The rotation that was applied to get the result side
     * @param resultSide The rotated resultant side
     *
     * @return The original (pre-rotation) side for which the given rotation would have produced the result side.
     */
    private Side findOriginalSide(Rotation rotation, Side resultSide) {
        for (Side side : Side.values()) {
            if (rotation.rotate(side) == resultSide) {
                return side;
            }
        }

        return null;
    }

    /**
     * Represents a mapping from a block's original side to its rotated result block's corresponding side
     */
    private static class SideMapping {
        private final Side originalSide;
        private final Side resultSide;

        private SideMapping(Side resultSide, Side originalSide) {
            this.resultSide = resultSide;
            this.originalSide = originalSide;
        }
    }
}
