# Porting TODO List (from age-series/ElectricalAge 1.7.10 Fork)

We might want to try merge v1.24.8, and we can pick what we want, and adjust it to our codebase.

This list tracks features, bugfixes, and optimizations from the actively maintained 1.7.10 fork (up to v1.24.8) that need to be ported to this 1.12.2 version.

## 🔴 CRITICAL: Simulation & Logic Fixes
- [ ] **MNA Solver Optimizations:** Significant performance improvements to the Modified Nodal Analysis core.
- [ ] **NaN/Infinite Loop Protection:** Fixes for "exploding" circuits and `NaN` values in shaft/clutch systems.
- [ ] **Steam Generation Logic:** Fixes for steam generation thresholds and power misinterpretation in cables.
- [ ] **Thermal System Fixes:** Negative thermal power now correctly causes negative temperatures.
- [ ] **DC/DC Converter Rewrite:** Logic rewritten in Kotlin (was previously causing world corruption in some cases).
- [ ] **Variable DC/DC Converter:** Port the boost/buck logic and orientation-based control.
- [ ] **Clutch Logic Revamp:** Fixes for clutches not allowing shafts to rotate after being disengaged.

## 🟡 High Priority: Features & Content
- [ ] **Electrical Integration API (v1):** A stable API for other mods to interact with ELN's electrical domain (16-channel signal bus support, etc.).
- [ ] **Rotary Motor:** High-power fuel-based motor (24mB/t) with specific efficiency curves.
- [ ] **Thermal Heat Exchanger:** Allows conversion between IC2 Coolant and ELN Heat, and Water to Steam.
- [ ] **Advanced Shaft Objects:**
    - [ ] Fixed Shaft (for braking).
    - [ ] Functional Clutch (proper engagement/disengagement).
- [ ] **Signal Bus System:** 16-channel signal cables and breakout logic.
- [ ] **Floodlights:** 3D-rotatable directed light projection system (requires advanced beam math).
- [ ] **Config Copy Tool:** Item/Config cloning between machines (Shift+Right Click logic).

## 🟢 Medium Priority: Improvements & Polish
- [ ] **Biome & Y-Height Thermals:** Temperature ramps based on biome data and altitude.
- [ ] **Halogen Bulbs:** Halogen variants.
- [ ] **GUI Modernization:**
    - [ ] Horizontal Trackbars.
    - [ ] Multilanguage Tutorial Signs.
    - [ ] Expanded WAILA/Hwyla tooltips (Matrix debugging info).
- [x] **Autominer Improvements:** Output to any inventory (not just vanilla chests) and "full chest" logic.
- [ ] **Sound System:** Client-side looped sounds with pitch/volume shifting based on machine state.

## 🔵 Technical & Build (Reference Only)
- [ ] **Kotlin Migration:** Upstream has moved heavily towards Kotlin for core logic.
- [ ] **Unit Testing:** Port the extensive Kotlin-based test suite (MNA tests, component tests).
- [ ] **Gradle Kotlin DSL:** Upstream uses `.gradle.kts` (decide if we want to follow).

---
*Note: This list is derived from the `age-series` fork (1.24.8) and compared against our 1.14.2-based port.*
