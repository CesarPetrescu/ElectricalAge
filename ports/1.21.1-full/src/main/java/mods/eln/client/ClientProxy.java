package mods.eln.client;

import mods.eln.CommonProxy;
import mods.eln.Eln;
import mods.eln.init.ElnContent;
import mods.eln.init.ModBlock;
import mods.eln.generic.GenericItemUsingDamage;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.generic.GenericItemBlockUsingDamage;
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor;
import mods.eln.node.six.SixNodeEntity;
import mods.eln.node.six.SixNodeRender;
import mods.eln.node.transparent.TransparentNodeEntity;
import mods.eln.node.transparent.TransparentNodeRender;
import mods.eln.sound.SoundClientEventListener;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;

/**
 * Client-side proxy implementation.
 * Registers tile entity special renderers and other client-side content.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Eln.MODID, value = Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public static UuidManager uuidManager;
    public static SoundClientEventListener soundClientEventListener;
    public static FrameTime frameTime;

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        Eln.logger.info("Registering Electrical Age item models...");

        // Register models for multi-damage items (SharedItem, SixNodeItem, TransparentNodeItem)
        registerMultiDamageItem(Eln.sharedItem);
        registerMultiDamageItemBlock(Eln.sixNodeItem);
        registerMultiDamageItemBlock(Eln.transparentNodeItem);

        // Register models for other items in ElnContent.registeredItems
        for (Item item : ElnContent.registeredItems) {
            if (item instanceof GenericItemUsingDamage) {
                registerMultiDamageItem((GenericItemUsingDamage) item);
            } else if (item instanceof GenericItemBlockUsingDamage) {
                registerMultiDamageItemBlock((GenericItemBlockUsingDamage) item);
            } else {
                registerSimpleModel(item);
            }
        }

        // Register ItemBlocks for standard blocks
        for (Block block : ElnContent.registeredBlocks) {
            if (block == ModBlock.sixNodeBlock || block == ModBlock.transparentNodeBlock) continue;
            
            Item item = Item.getItemFromBlock(block);
            if (item != null) {
                registerSimpleModel(item);
            }
        }
    }

    private static void registerMultiDamageItem(GenericItemUsingDamage item) {
        if (item == null) return;
        
        item.subItemList.forEach((damage, desc) -> {
            GenericItemUsingDamageDescriptor d = (GenericItemUsingDamageDescriptor) desc;
            if (d != null && d.IconName != null) {
                String path = d.IconName;
                if (path.startsWith(Eln.MODID + ":")) {
                    path = path.substring(Eln.MODID.length() + 1);
                }
                ModelLoader.setCustomModelResourceLocation(item, (Integer)damage, 
                    new ModelResourceLocation(new ResourceLocation(Eln.MODID, path), "inventory"));
            }
        });
    }

    private static void registerMultiDamageItemBlock(GenericItemBlockUsingDamage item) {
        if (item == null) return;
        
        item.subItemList.forEach((damage, desc) -> {
            GenericItemBlockUsingDamageDescriptor d = (GenericItemBlockUsingDamageDescriptor) desc;
            if (d != null && d.IconName != null) {
                String path = d.IconName;
                if (path.startsWith(Eln.MODID + ":")) {
                    path = path.substring(Eln.MODID.length() + 1);
                }
                ModelLoader.setCustomModelResourceLocation(item, (Integer)damage, 
                    new ModelResourceLocation(new ResourceLocation(Eln.MODID, path), "inventory"));
            } else if (d != null && d.name != null) {
                String iconName = d.name.replaceAll(" ", "").toLowerCase();
                ModelLoader.setCustomModelResourceLocation(item, (Integer)damage, 
                    new ModelResourceLocation(new ResourceLocation(Eln.MODID, iconName), "inventory"));
            } else {
                ModelLoader.setCustomModelResourceLocation(item, (Integer)damage, 
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
            }
        });
    }

    private static void registerSimpleModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0, 
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        uuidManager = new UuidManager();
        soundClientEventListener = new SoundClientEventListener(uuidManager);
        frameTime = new FrameTime();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientRegistry.bindTileEntitySpecialRenderer(SixNodeEntity.class, new SixNodeRender());
        ClientRegistry.bindTileEntitySpecialRenderer(TransparentNodeEntity.class, new TransparentNodeRender());
    }
}
