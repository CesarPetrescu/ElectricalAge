package mods.eln.item;

import mods.eln.misc.McBridge;
import mods.eln.Eln;
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.item.lampitem.BoilerplateLampData;
import mods.eln.item.lampitem.LampDescriptor;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeBlock;
import mods.eln.node.NodeManager;
import mods.eln.sixnode.currentcable.CurrentCableDescriptor;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import mods.eln.sixnode.electricalcable.IUtilityCableInventory;
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor;
import mods.eln.sixnode.electricalcable.UtilityCableItemMovingHelper;
import mods.eln.sixnode.genericcable.GenericCableDescriptor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class ConfigCopyToolDescriptor extends GenericItemUsingDamageDescriptor {
    public ConfigCopyToolDescriptor(String name) { super(name); }

    @Override
    public boolean onItemUse(ItemStack stack, Player player, Level world, int x, int y, int z, int side, float vx, float vy, float vz) {
        if(world.isClientSide) return false;

        Block block = McBridge.getBlock(world, x, y, z);

        if(block instanceof NodeBlock) {
            NodeBase node = NodeManager.instance.getNodeFromCoordonate(new Coordinate(x, y, z, world));
            if(node != null) {
                node.onBlockActivated(player, Direction.fromIntMinecraftSide(side), vx, vy, vz);
            }
            return true;
        }
        return false;
    }

    public static boolean readCableType(CompoundTag compound, Container inv, int slot, Player invoker, boolean acceptSignalCable) {
        String name = "cable";

        if (compound.contains(name + "Type")) {
            int type = compound.getInt(name + "Type");
            GenericItemBlockUsingDamageDescriptor desc = Eln.sixNodeItem.getDescriptor(type);
            boolean readCable = false;

            // ElectricalCableDescriptor here covers utility cables (utility cables are not signal cables)
            if (desc instanceof ElectricalCableDescriptor) {
                readCable = !(((ElectricalCableDescriptor) desc).signalWire && !acceptSignalCable);
            } else if (desc instanceof CurrentCableDescriptor) {
                readCable = true;
            }

            if (readCable) return readCableType(compound, inv, slot, invoker);
        }

        return false;
    }

    public static boolean readCableType(CompoundTag compound, Container inv, int slot, Player invoker) {
        return readCableType(compound, "cable", inv, slot, invoker);
    }

    public static boolean readCableType(CompoundTag compound, String name, Container inv, int slot, Player invoker) {
        if (compound.contains(name + "Type")) {
            int amt = 1;
            if (compound.contains(name + "Amt")) amt = compound.getInt(name + "Amt");
            int type = compound.getInt(name + "Type");
            ItemStack stackInSlot = inv.getItem(slot);

            // MOVE THE OLD ITEM OUT OF THE DESTINATION INVENTORY (INTO THE PLAYER INVENTORY)
            if (!McBridge.isNothing(stackInSlot)) {
                GenericItemBlockUsingDamageDescriptor thisCableDesc = GenericItemBlockUsingDamageDescriptor.getDescriptor(stackInSlot, GenericCableDescriptor.class);
                if (thisCableDesc != null) {
                    if (thisCableDesc instanceof UtilityCableDescriptor) {
                        double cableLength = ((UtilityCableDescriptor) thisCableDesc).getRemainingLengthMeters(stackInSlot);
                        UtilityCableItemMovingHelper itemMover = new UtilityCableItemMovingHelper((UtilityCableDescriptor) thisCableDesc, cableLength);
                        itemMover.move(invoker.getInventory(), inv, slot, 0);
                    } else {
                        (new ItemMovingHelper() {
                            @Override
                            public boolean acceptsStack(ItemStack stack) {
                                return thisCableDesc.checkSameItemStack(stack);
                            }

                            @Override
                            public ItemStack newStackOfSize(int items) {
                                return thisCableDesc.newItemStack(items);
                            }
                        }).move(invoker.getInventory(), inv, slot, 0);
                    }
                }
            }

            // MOVE THE NEW ITEM INTO THE DESTINATION INVENTORY (OUT OF THE PLAYER INVENTORY)
            if (type != -1) {
                GenericItemBlockUsingDamageDescriptor cableDesc = Eln.sixNodeItem.getDescriptor(type);
                if (cableDesc != null) {
                    if (cableDesc instanceof UtilityCableDescriptor) {
                        double cableLength = IUtilityCableInventory.DEFAULT_REQUIRED_LENGTH;
                        if (compound.contains(name + "Length")) cableLength = compound.getDouble(name + "Length");
                        UtilityCableItemMovingHelper itemMover = new UtilityCableItemMovingHelper((UtilityCableDescriptor) cableDesc, cableLength);
                        itemMover.move(invoker.getInventory(), inv, slot, amt);
                    } else {
                        (new ItemMovingHelper() {
                            @Override
                            public boolean acceptsStack(ItemStack stack) {
                                return cableDesc.checkSameItemStack(stack);
                            }

                            @Override
                            public ItemStack newStackOfSize(int items) {
                                return cableDesc.newItemStack(items);
                            }
                        }).move(invoker.getInventory(), inv, slot, amt);
                    }
                }
            }

            return true;
        } else return false;
    }

    public static void writeCableType(CompoundTag compound, ItemStack stack) {
        writeCableType(compound, "cable", stack);
    }

    public static void writeCableType(CompoundTag compound, String name, ItemStack stack) {
        if(!McBridge.isNothing(stack)) {
            Eln.logger.info("CCT Copy: " + name + "Amt: " + stack.getCount());
            compound.putInt(name + "Amt", stack.getCount());
        }
        GenericItemBlockUsingDamageDescriptor desc = GenericItemBlockUsingDamageDescriptor.getDescriptor(stack);
        if(desc != null) {
            Eln.logger.info("CCT Copy: " + name + "Type: " + desc.parentItemDamage);
            compound.putInt(name + "Type", desc.parentItemDamage);
            if (desc instanceof UtilityCableDescriptor) {
                compound.putDouble(name + "Length", ((UtilityCableDescriptor) desc).getRemainingLengthMeters(stack));
            }
        } else {
            Eln.logger.info("CCT Copy: " + name + "Type: -1");
            compound.putInt(name + "Type", -1);
        }
    }

    public static boolean readLampDescriptor(CompoundTag compound, String name, Container inv, int slot, Player invoker, BoilerplateLampData[] acceptedLampTypes) {
        if (compound.contains(name)) {
            String type = compound.getString(name);
            GenericItemUsingDamageDescriptor desc = GenericItemUsingDamageDescriptor.getByName(type);

            if (desc instanceof LampDescriptor) {
                for (BoilerplateLampData acceptedLampType : acceptedLampTypes) {
                    if (((LampDescriptor) desc).getLampData().getTechnology() == acceptedLampType) {
                        return readGenDescriptor(compound, name, inv, slot, invoker);
                    }
                }
            }
        }

        return false;
    }

    public static boolean readGenDescriptor(CompoundTag compound, String name, Container inv, int slot, Player invoker) {
        if (compound.contains(name)) {
            int amt = 1;
            if (compound.contains(name + "Amt")) amt = compound.getInt(name + "Amt");
            String type = compound.getString(name);
            GenericItemUsingDamageDescriptor desc = GenericItemUsingDamageDescriptor.getDescriptor(inv.getItem(slot));

            // MOVE THE OLD ITEM OUT OF THE DESTINATION INVENTORY (INTO THE PLAYER INVENTORY)
            if (desc != null) {
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return desc.checkSameItemStack(stack);
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return desc.newItemStack(items);
                    }
                }).move(invoker.getInventory(), inv, slot, 0);
            }

            // MOVE THE NEW ITEM INTO THE DESTINATION INVENTORY (OUT OF THE PLAYER INVENTORY)
            if (!type.equals(GenericItemUsingDamageDescriptor.INVALID_NAME)) {
                GenericItemUsingDamageDescriptor newDesc = GenericItemUsingDamageDescriptor.getByName(type);
                if (newDesc != null) {
                    (new ItemMovingHelper() {
                        @Override
                        public boolean acceptsStack(ItemStack stack) {
                            return newDesc.checkSameItemStack(stack);
                        }

                        @Override
                        public ItemStack newStackOfSize(int items) {
                            return newDesc.newItemStack(items);
                        }
                    }).move(invoker.getInventory(), inv, slot, amt);
                }
            }

            return true;
        } else return false;
    }

    public static void writeGenDescriptor(CompoundTag compound, String name, ItemStack stack) {
        if(!McBridge.isNothing(stack)) {
            Eln.logger.info("CCT Copy: " + name + "Amt: " + stack.getCount());
            compound.putInt(name + "Amt", stack.getCount());
        }
        GenericItemUsingDamageDescriptor desc = GenericItemUsingDamageDescriptor.getDescriptor(stack);
        if(desc != null) {
            Eln.logger.info("CCT Copy: " + name + " " + desc.name);
            compound.putString(name, desc.name);
        } else {
            Eln.logger.info("CCT Copy: " + name + " Invalid Descriptor");
            compound.putString(name, GenericItemUsingDamageDescriptor.INVALID_NAME);
        }
    }

    public static boolean readVanillaStack(CompoundTag compound, String name, Container inv, int slot, Player invoker) {
        if(compound.contains(name)) {
            int amt = 1;
            if(compound.contains(name + "Amt")) {
                amt = compound.getInt(name + "Amt");
            }
            int itemId = compound.getInt(name);
            ItemStack current = inv.getItem(slot);
            if(current != null) {
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return current.getItem() == stack.getItem();
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return new ItemStack(current.getItem(), items);
                    }
                }).move(invoker.getInventory(), inv, slot, 0);
            }
            if(itemId >= 0) {
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return mods.eln.misc.McBridge.itemId(stack.getItem()) == itemId;
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return new ItemStack(mods.eln.misc.McBridge.itemById(itemId), items);
                    }
                }).move(invoker.getInventory(), inv, slot, amt);
            }
            return true;
        }
        return false;
    }

    public static void writeVanillaStack(CompoundTag compound, String name, ItemStack stack) {
        if(McBridge.isNothing(stack)) {
            Eln.logger.info("CCT Copy: " + name + "Amt: 0");
            compound.putInt(name, -1);
            compound.putInt(name + "Amt", 0);
        } else {
            Eln.logger.info("CCT Copy: " + name + " " + mods.eln.misc.McBridge.itemId(stack.getItem()));
            Eln.logger.info("CCT Copy: " + name + "Amt: " + stack.getCount());
            compound.putInt(name, mods.eln.misc.McBridge.itemId(stack.getItem()));
            compound.putInt(name + "Amt", stack.getCount());
        }
    }
}
