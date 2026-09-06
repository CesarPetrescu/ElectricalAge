package mods.eln.node;

import mods.eln.misc.Coordinate;
import mods.eln.misc.Utils;
import mods.eln.node.transparent.TransparentNode;
import mods.eln.node.transparent.TransparentNodeElement;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NodeManager extends WorldSavedData {
    public static NodeManager instance = null;

    private HashMap<Coordinate, NodeBase> nodesMap;
    private ArrayList<NodeBase> nodes;
    private Set<NodeBase> nodesToPublish = Collections.newSetFromMap(new ConcurrentHashMap<NodeBase, Boolean>());

    public Set<NodeBase> getNodesToPublish() {
        return nodesToPublish;
    }

    public void addNodeToPublish(NodeBase node) {
        nodesToPublish.add(node);
    }

    public void clearNodesToPublish() {
        nodesToPublish.clear();
    }

    public HashMap<Coordinate, NodeBase> getNodeArray() {
        return nodesMap;
    }

    public ArrayList<NodeBase> getNodes() {
        return nodes;
    }

    public static final HashMap<String, Class> UUIDToClass = new HashMap<String, Class>();

    public static void registerUuid(String uuid, Class classType) {
        UUIDToClass.put(uuid, classType);
    }

    Collection<NodeBase> getNodeList() {
        return nodesMap.values();
    }

    // private ArrayList<Node> nodeArray = new ArrayList<Node>();

    public NodeManager(String name) {
        super(name);
        nodesMap = new HashMap<Coordinate, NodeBase>();
        nodes = new ArrayList<NodeBase>();
        instance = this;

    }

    public void addNode(NodeBase node) {
        if (node.coordinate == null) {
            throw new RuntimeException("Null coordinate addnode");
        }
        NodeBase old = nodesMap.put(node.coordinate, node);
        if (old != null) {
            nodes.remove(old);
        }

        nodes.add(node);
        setDirty(true); // Mark for saving
    }

    public void removeNode(NodeBase node) {
        if (node == null) return;
        nodesMap.remove(node.coordinate);
        nodes.remove(node);
        setDirty(true); // Mark for saving
    }

    public void removeCoordinate(Coordinate c) {
        // nodeArray.remove(node);
        NodeBase n = nodesMap.remove(c);
        if (n != null) nodes.remove(n);
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        int i = 0;
        i++;
        for(Object o : Utils.getTags(nbt)) {
            NBTTagCompound tag = (NBTTagCompound) o;
            Class nodeClass = UUIDToClass.get(tag.getString("tag"));
            try {
                NodeBase node = (NodeBase) nodeClass.getConstructor().newInstance();
                node.readFromNBT(tag);
                addNode(node);
                node.initializeFromNBT();

            } catch (Exception e) { e.printStackTrace(); }

        }

        for(NodeBase node : nodes){ node.globalBoot(); }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        return compound;
    }

    /*
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        int nodeCounter = 0;
        for(NodeBase node : nodesMap.values()) {
            try {
                if(!node.mustBeSaved()) {
                    continue;
                }
                NBTTagCompound nbtNode = new NBTTagCompound();
                nbtNode.setString("tag", node.getNodeUuid());
                node.writeToNBT(nbtNode);
                nbt.setTag("n" + nodeCounter++, nbtNode);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return nbt;
     }
     */


    public NodeBase getNodeFromCoordinate(Coordinate nodeCoordinate) {
        return nodesMap.get(nodeCoordinate);
    }

    public TransparentNodeElement getTransparentNodeFromCoordinate(Coordinate coord) {
        NodeBase base = getNodeFromCoordinate(coord);
        if (base instanceof TransparentNode) {
            TransparentNode n = (TransparentNode) base;
            return n.element;
        }
        return null;
    }

    Random rand = new Random();

    public NodeBase getRandomNode() {
        if (nodes.isEmpty()) return null;
        return nodes.get(rand.nextInt(nodes.size()));
    }


    public void loadFromNbt(NBTTagCompound nbt) {
        if (nbt == null) return;
        
        List<NodeBase> addedNode = new ArrayList<NodeBase>();
        for (Object o : Utils.getTags(nbt)) {
            NBTTagCompound tag = (NBTTagCompound) o;
            String uuid = tag.getString("tag");
            Class nodeClass = UUIDToClass.get(uuid);
            if (nodeClass == null) {
                Utils.println("Warning: Unknown node UUID: " + uuid + ", skipping");
                continue;
            }
            try {
                NodeBase node = (NodeBase) nodeClass.getConstructor().newInstance();
                node.readFromNBT(tag);
                addNode(node);
                addedNode.add(node);
                node.initializeFromNBT();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (NodeBase n : addedNode) {
            n.globalBoot();
        }
    }

    public void saveToNbt(NBTTagCompound nbt, int dim) {
        int nodeCounter = 0;
        List<NodeBase> nodesCopy = new ArrayList<NodeBase>();
        nodesCopy.addAll(nodes);
        for (NodeBase node : nodesCopy) {
            try {
                if (node.mustBeSaved() == false) continue;
                if (dim != Integer.MIN_VALUE && node.coordinate.getDimension() != dim) continue;
                NBTTagCompound nbtNode = new NBTTagCompound();
                nbtNode.setString("tag", node.getNodeUuid());
                node.writeToNBT(nbtNode);
                nbt.setTag("n" + nodeCounter++, nbtNode);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public void clear() {
        nodes.clear();
        nodesMap.clear();
    }

    public void unload(int dimensionId) {

        Iterator<NodeBase> i = nodes.iterator();
        while (i.hasNext()) {
            NodeBase n = i.next();
            if (n.coordinate.getDimension() == dimensionId) {
                n.unload();
                i.remove();
                nodesMap.remove(n.coordinate);
            }
        }
    }
}
