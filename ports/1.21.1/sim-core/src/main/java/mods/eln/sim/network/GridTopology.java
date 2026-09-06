package mods.eln.sim.network;

import java.util.*;

/** Explicit two-terminal prototype devices and six-way resistive wire. Face contacts are ideal;
 * each wire center-to-face arm is 0.05 ohm. A source includes 1 ohm internal resistance.
 * Adjacent matching faces connect; no implicit return conductor or cross-island ground exists.
 */
public final class GridTopology {
    public static final int MAX_DEVICES = 64;
    public static final double WIRE_ARM_OHMS=.05, SOURCE_OHMS=1, SOURCE_VOLTS=10, LOAD_OHMS=10, CAPACITANCE=1, DT=.05;
    public enum Kind { WIRE, SOURCE, LOAD, CAPACITOR }
    public enum Side {
        DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
        final int x,y,z;
        Side(int x,int y,int z) { this.x=x;this.y=y;this.z=z; }
        public Side opposite() { return values()[ordinal() ^ 1]; }
    }
    public record Cell(int x,int y,int z) {
        Cell neighbor(Side side) { return new Cell(Math.addExact(x,side.x),Math.addExact(y,side.y),Math.addExact(z,side.z)); }
        public String key() { return x+","+y+","+z; }
    }
    public record Device(Cell cell,Kind kind,Side facing,boolean powered,double capacitorVoltage) {
        public Device {
            Objects.requireNonNull(cell);Objects.requireNonNull(kind);Objects.requireNonNull(facing);
            if (!Double.isFinite(capacitorVoltage) || Math.abs(capacitorVoltage)>1000) throw new IllegalArgumentException("Invalid capacitor snapshot");
        }
        public boolean hasPort(Side side) { return kind==Kind.WIRE || facing==side || facing.opposite()==side; }
        public String bodyId() { return cell.key()+"/body"; }
        String port(Side side) { return cell.key()+"/"+side.name(); }
    }
    private GridTopology() { }
    public static CircuitNetwork compile(Collection<Device> devices) {
        return new CircuitNetwork(branches(devices),DT);
    }
    public static List<CircuitNetwork.Branch> branches(Collection<Device> devices) {
        if (devices.size()>MAX_DEVICES) throw new IllegalArgumentException("Prototype limit: 64 active devices per level");
        Map<Cell,Device> grid=new HashMap<>();
        for(Device device:devices) if(grid.put(device.cell(),device)!=null)throw new IllegalArgumentException("Duplicate grid cell");
        CircuitNetwork.Union contacts=new CircuitNetwork.Union();
        for(Device device:devices)for(Side side:Side.values())if(device.hasPort(side)){
            Device neighbor=grid.get(device.cell().neighbor(side));
            if(neighbor!=null && neighbor.hasPort(side.opposite()))contacts.join(device.port(side),neighbor.port(side.opposite()));
        }
        List<CircuitNetwork.Branch> branches=new ArrayList<>();
        for(Device device:devices){
            if(device.kind()==Kind.WIRE){
                for(Side side:Side.values())branches.add(new CircuitNetwork.Branch(device.cell().key()+"/wire/"+side,
                    CircuitNetwork.Kind.RESISTOR,device.cell().key()+"/center",contacts.find(device.port(side)),WIRE_ARM_OHMS,0));
                continue;
            }
            String a=contacts.find(device.port(device.facing())),b=contacts.find(device.port(device.facing().opposite()));
            switch(device.kind()){
                case SOURCE -> {
                    String internal=device.cell().key()+"/internal";
                    branches.add(new CircuitNetwork.Branch(device.bodyId(),CircuitNetwork.Kind.VOLTAGE_SOURCE,internal,b,device.powered()?SOURCE_VOLTS:0,0));
                    branches.add(new CircuitNetwork.Branch(device.cell().key()+"/resistance",CircuitNetwork.Kind.RESISTOR,internal,a,SOURCE_OHMS,0));
                }
                case LOAD -> branches.add(new CircuitNetwork.Branch(device.bodyId(),CircuitNetwork.Kind.RESISTOR,a,b,LOAD_OHMS,0));
                case CAPACITOR -> branches.add(new CircuitNetwork.Branch(device.bodyId(),CircuitNetwork.Kind.CAPACITOR,a,b,CAPACITANCE,device.capacitorVoltage()));
                default -> throw new IllegalStateException("Unexpected device");
            }
        }
        return List.copyOf(branches);
    }
}
