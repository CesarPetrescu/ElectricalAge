package mods.eln.integration.waila;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import mods.eln.Eln;
import mods.eln.misc.Coordinate;
import mods.eln.packets.GhostNodeWailaRequestPacket;
import mods.eln.packets.GhostNodeWailaResponsePacket;
import mods.eln.packets.SixNodeWailaRequestPacket;
import mods.eln.packets.TransparentNodeRequestPacket;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by Gregory Maddra on 2016-06-29.
 */
public class WailaCache {

    public static LoadingCache<Coordinate, TransparentNodeWailaData> nodes = CacheBuilder.newBuilder()
        .maximumSize(20)
        .refreshAfterWrite(2, TimeUnit.SECONDS)
        .build(
            new CacheLoader<Coordinate, TransparentNodeWailaData>() {
                public TransparentNodeWailaData load(Coordinate key) throws Exception {
                    Eln.elnNetwork.sendToServer(new TransparentNodeRequestPacket(key));
                    return new TransparentNodeWailaData(ItemStack.EMPTY, new HashMap<String, String>());
                }

                @Override
                public ListenableFuture<TransparentNodeWailaData> reload(Coordinate key,
                                                                    TransparentNodeWailaData oldValue) throws Exception {
                    Eln.elnNetwork.sendToServer(new TransparentNodeRequestPacket(key));
                    return Futures.immediateFuture(oldValue);
                }
            }
        );

    public static LoadingCache<SixNodeCoordinate, SixNodeWailaData> sixNodes = CacheBuilder.newBuilder()
        .maximumSize(20)
        .refreshAfterWrite(2, TimeUnit.SECONDS)
        .build(
            new CacheLoader<SixNodeCoordinate, SixNodeWailaData>() {
                public SixNodeWailaData load(SixNodeCoordinate key) throws Exception {
                    Eln.elnNetwork.sendToServer(new SixNodeWailaRequestPacket(key.getCoord(), key.getSide()));
                    return new SixNodeWailaData(ItemStack.EMPTY, new HashMap<String, String>());
                }

                @Override
                public ListenableFuture<SixNodeWailaData> reload(SixNodeCoordinate key,
                                                                 SixNodeWailaData oldValue) throws Exception {
                    Eln.elnNetwork.sendToServer(new SixNodeWailaRequestPacket(key.getCoord(), key.getSide()));
                    return Futures.immediateFuture(oldValue);
                }
            }
        );

    public static LoadingCache<Coordinate, GhostNodeWailaData> ghostNodes = CacheBuilder.newBuilder()
        .maximumSize(20)
        .refreshAfterWrite(10, TimeUnit.SECONDS)
        .build(
            new CacheLoader<Coordinate, GhostNodeWailaData>() {
                public GhostNodeWailaData load(Coordinate key) throws Exception {
                    Eln.elnNetwork.sendToServer(new GhostNodeWailaRequestPacket(key));
                    return new GhostNodeWailaData(key, ItemStack.EMPTY, GhostNodeWailaResponsePacket.UNKNOWN_TYPE, mods.eln.misc.Direction.XN);
                }

                @Override
                public ListenableFuture<GhostNodeWailaData> reload(Coordinate key,
                                                                   GhostNodeWailaData oldValue) throws Exception {
                    Eln.elnNetwork.sendToServer(new GhostNodeWailaRequestPacket(key));
                    return Futures.immediateFuture(oldValue);
                }
            }
        );

}
