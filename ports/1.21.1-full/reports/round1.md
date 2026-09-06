# First real full-source compile

Source: e06b71e3d0448bacac191d2bff09f4be65870e56.
GitHub Actions run: 34025599543.
Target: NeoForge 21.1.249 / Minecraft 1.21.1 / Java 21 / Kotlin 2.2.21.

The real Kotlin compiler emitted **2,816 diagnostic entries across 71 Kotlin files**. The dependent Java compilation did not run after Kotlin failed. Counts include cascades and are NOT independent bugs or a port-completion percentage.

The leading blockers are removed/moved Minecraft types (ItemStack, NBTTagCompound, EntityPlayer, ItemBlock), removed Forge lifecycle/network/config APIs, GUI types, and optional-integration types. Round 2 applies reviewed type mappings in the ORIGINAL source files, preserving gameplay and original resources.

The source archive upload in round 1 was empty due to git archive being invoked from a subdirectory. This is corrected in round 2; do not use round 1's project artifact.
