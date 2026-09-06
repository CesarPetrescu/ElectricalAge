package mods.eln.generic;

/**
 * Implemented by the per-descriptor items ({@link DescriptorItem}, {@link DescriptorBlockItem}) so
 * code that used to switch on item metadata can still reach the family and the legacy sub-id.
 */
public interface IDescriptorItem {
    /** The family (what used to be the single metadata item) this item was registered through. */
    Object descriptorFamily();

    /** The 1.7.10 sub-id (`subId + (group << 6)`), kept for creative-tab grouping and ordering. */
    int legacyId();
}
