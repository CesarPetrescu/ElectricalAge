package mods.eln.sim.persistence;
public interface StateSerializable {
 void readState(StateData data,String prefix);
 StateData writeState(StateData data,String prefix);
}
