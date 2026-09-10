package mods.eln.devtest

import mods.eln.Eln
import mods.eln.mechanical.*
import mods.eln.misc.Direction as Side
import mods.eln.node.transparent.TransparentNode
import net.minecraft.core.BlockPos
import kotlin.math.abs

/** Rigid replacement and clutch engagement have different, intentionally tested contracts. */
internal class NativeMechanicalCampaign(private val f: NativeCampaignFixtures) {
    fun prepare() {
        rigidShafts()
        clutches()
    }

    private fun state(position: BlockPos): Map<String, Any> {
        val part = (f.node(position) as? TransparentNode)?.element as? SimpleShaftElement
            ?: return mapOf("position" to listOf(position.x, position.y, position.z), "present" to false)
        return mapOf("position" to listOf(position.x, position.y, position.z), "present" to true,
            "id" to f.id(part), "instance" to System.identityHashCode(part), "front" to part.front.name,
            "ports" to part.shaftConnectivity.map { it.name }, "network" to System.identityHashCode(part.shaft),
            "speedRadS" to part.shaft.rads, "rpm" to part.shaft.rads * 60.0 / (2.0 * Math.PI),
            "energyJ" to part.shaft.energy, "networkParts" to part.shaft.parts.size)
    }

    private fun rigidShafts() = with(f) {
        val p = BlockPos(512, 65, 526)
        val wheelPos = p.east(3)
        fun motor() = machine(p) as MotorElement
        fun generator() = machine(p.east(4)) as GeneratorElement
        val originalFront = machine(wheelPos).front
        val originalPorts = (machine(wheelPos) as FlyWheelElement).shaftConnectivity.toList()
        val components = (0..4).map { retained.add(p.east(it)); id(machine(p.east(it))) }
        fun measurements(): Map<String, Any> {
            val m = motor(); val g = generator()
            check(m.shaft.rads.isFinite() && g.shaft.rads.isFinite())
            check(m.shaft.energy.isFinite() && g.shaft.energy.isFinite())
            return mapOf("motorRadS" to m.shaft.rads, "generatorRadS" to g.shaft.rads,
                "motorShaftJ" to m.shaft.energy, "generatorShaftJ" to g.shaft.energy,
                "generatorW" to g.electricalPowerSource.power, "sharedNetwork" to (m.shaft === g.shaft),
                "parts" to (0..4).map { state(p.east(it)) })
        }
        steps += NativeCampaignFixtures.Step("shaft-loaded", "Loaded rigid motor/joint/tachometer/flywheel/generator", p.east(2), components, 80,
            sample = ::measurements, verify = {
                check(motor().shaft === generator().shaft)
                check(motor().shaft.rads > motor().desc.nominalRads * .7)
                check(generator().electricalPowerSource.power > 1.0)
                measurements()
            })
        var coastingEnergy = 0.0
        steps += NativeCampaignFixtures.Step("shaft-coasting", "Remove supply: remaining shaft energy pays for generation", p.east(2), components, 30,
            begin = { coastingEnergy = motor().shaft.energy; world.removeBlock(p.north().west(), false) },
            sample = ::measurements, verify = {
                check(motor().shaft.energy in 0.0..<coastingEnergy)
                measurements() + mapOf("beforeShaftJ" to coastingEnergy)
            })
        steps += NativeCampaignFixtures.Step("shaft-split", "Remove the spinning flywheel: two independent networks", p.east(2), components, 20,
            begin = { world.destroyBlock(wheelPos, false); retained.remove(wheelPos) }, sample = ::measurements, verify = {
                check(node(wheelPos) == null && motor().shaft !== generator().shaft)
                measurements()
            })
        var rejected: FlyWheelElement? = null
        var mismatch = emptyMap<String, Any>()
        steps += NativeCampaignFixtures.Step("shaft-unsafe-reinsert", "Unsafe rigid insertion destroys the fresh stationary flywheel", p.east(2), components, 10,
            begin = {
                val m = motor().shaft.rads; val g = generator().shaft.rads
                check(NativeCampaignOracles.unsafeRigidMerge(m, 0.0) || NativeCampaignOracles.unsafeRigidMerge(g, 0.0)) {
                    "Hazard precondition missing: motor=$m generator=$g rad/s"
                }
                mismatch = mapOf("motorBeforeRadS" to m, "generatorBeforeRadS" to g, "freshPartRadS" to 0.0,
                    "motorThresholdRadS" to (50.0 - .1 * m), "generatorThresholdRadS" to (50.0 - .1 * g),
                    "explosionsEnabled" to Eln.config.getBooleanOrElse("gameplay.hazards.explosionsEnabled", false))
                rejected = placeMachine(requiredItem("Flywheel"), wheelPos) as FlyWheelElement
                check(rejected!!.front == originalFront && rejected!!.shaftConnectivity.toList() == originalPorts)
                check(machine(wheelPos) === rejected) { "Placement did not create the flywheel" }
                // The inserted frame can last less than one render frame. Record the server event;
                // do not pause physics to manufacture a screenshot of a pending destruction.
                mismatch = mismatch + mapOf("placementObserved" to true, "replacementInstance" to System.identityHashCode(rejected))
            }, sample = { measurements() + mismatch }, verify = {
                check(node(wheelPos) == null && world.getBlockState(wheelPos).isAir)
                check(motor().shaft !== generator().shaft)
                check(motor().shaft.parts.none { it.element === rejected } && generator().shaft.parts.none { it.element === rejected })
                retained.remove(wheelPos)
                measurements() + mismatch + mapOf("replacementDestroyed" to true, "ghostConnection" to false)
            })
        steps += NativeCampaignFixtures.Step("shaft-brake-to-safe-speed", "Electrical regeneration and a resistor slow both surviving shafts", p.east(2), components, 20,
            begin = {
                // Declared physical boundaries: the 0 V supply absorbs regeneration; the generator
                // dissipates into a 10-ohm resistor. No assignment to shaft.rads or shaft.energy.
                source(p.north().west(), 0.0)
                load(p.east(5).north(), 10.0)
            }, sample = ::measurements, ready = { motor().shaft.rads < 20.0 && generator().shaft.rads < 20.0 }, verify = {
                check(!NativeCampaignOracles.unsafeRigidMerge(motor().shaft.rads, 0.0))
                check(!NativeCampaignOracles.unsafeRigidMerge(generator().shaft.rads, 0.0))
                measurements() + mapOf("freshPartSafeOnBothSides" to true)
            })
        var safeBefore = emptyMap<String, Any>()
        steps += NativeCampaignFixtures.Step("shaft-safe-reinsert", "Low-speed rigid replacement remains present and joins all three parts", p.east(2), components, 20,
            begin = {
                check(motor().shaft.rads < 20.0 && generator().shaft.rads < 20.0)
                safeBefore = mapOf("motorBeforeRadS" to motor().shaft.rads, "generatorBeforeRadS" to generator().shaft.rads)
                val part = placeMachine(requiredItem("Flywheel"), wheelPos) as FlyWheelElement
                check(part.front == originalFront && part.shaftConnectivity.toList() == originalPorts)
            }, sample = ::measurements, verify = {
                val part = machine(wheelPos) as FlyWheelElement
                check(motor().shaft === part.shaft && part.shaft === generator().shaft)
                measurements() + safeBefore + mapOf("survivedSlowTicks" to true)
            })
        steps += NativeCampaignFixtures.Step("shaft-reconnect", "Restore motor power after safe coupling; generation and animation recover", p.east(2), components, 80,
            begin = { load(p.east(5).north(), 100.0); voltage(p.north().west(), 480.0) }, sample = ::measurements,
            ready = { motor().shaft.rads > motor().desc.nominalRads * .7 }, verify = {
                check(motor().shaft === generator().shaft && node(wheelPos) != null)
                check(generator().electricalPowerSource.power > 1.0)
                measurements()
            })
        val idle = cell()
        val idleParts = listOf("Joint", "Flywheel", "Joint").mapIndexed { i, name ->
            placeMachine(requiredItem(name), idle.east(i)) as SimpleShaftElement
        }
        steps += NativeCampaignFixtures.Step("shaft-stationary-join", "All-stopped rigid insertion joins without destruction or injected energy", idle.east(), idleParts.map(::id), 20,
            verify = {
                check(idleParts.withIndex().all { (i, part) -> machine(idle.east(i)) === part })
                check(idleParts.all { it.shaft === idleParts.first().shaft && it.shaft.rads == 0.0 && it.shaft.energy == 0.0 })
                mapOf("parts" to (0..2).map { state(idle.east(it)) }, "allStopped" to true)
            })
        val large = BlockPos(512, 65, 533)
        steps += NativeCampaignFixtures.Step("large-shaft-loaded", "Large motor/generator use their elevated shaft connection", large.east(2),
            listOf(id(machine(large)), id(machine(large.east(4)))), 40, verify = {
                val m = machine(large) as MotorElement; val g = machine(large.east(4)) as GeneratorElement
                check(m.shaft === g.shaft && g.electricalPowerSource.power > 1.0)
                mapOf("speedRadS" to m.shaft.rads, "outputW" to g.electricalPowerSource.power)
            })
        steps += NativeCampaignFixtures.Step("large-shaft-remove", "Removing the multiblock also removes its ghost blocks", large.east(2), listOf("eln:large_shaft_motor"), 15,
            begin = { world.destroyBlock(large, false); retained.remove(large) }, verify = {
                check(node(large) == null)
                check(BlockPos.betweenClosed(large.offset(-1, 0, -1), large.offset(1, 2, 1))
                    .none { world.getBlockState(it).block == Eln.ghostBlock })
                mapOf("ghostsRemaining" to 0)
            })
    }

    private fun clutches() = with(f) {
        fun rig(plate: String): BlockPos {
            val p = cell()
            placeMachine(requiredItem("Shaft Motor"), p.west(2))
            placeMachine(requiredItem("Flywheel"), p.west())
            val clutch = placeMachine(requiredItem("Clutch"), p) as ClutchElement
            clutch.inventory.setItem(0, requiredItem(plate))
            clutch.inventory.setChanged()
            placeMachine(requiredItem("Flywheel"), p.east())
            placeMachine(requiredItem("Generator"), p.east(2))
            wire(p.west(2).north()); source(p.west(2).north(2), 0.0)
            wire(p.east(2).north()); load(p.east(2).north(2), 2304.0, Side.ZP); ground(p.east(2).north(3))
            wire(p.south(), true); source(p.south(2), 0.0, true)
            return p
        }
        fun measure(p: BlockPos): Map<String, Any> {
            val m = machine(p.west(2)) as MotorElement; val g = machine(p.east(2)) as GeneratorElement
            val c = (node(p) as? TransparentNode)?.element as? ClutchElement
            return mapOf("motorRadS" to m.shaft.rads, "generatorRadS" to g.shaft.rads,
                "deltaRadS" to abs(m.shaft.rads - g.shaft.rads), "generatorW" to g.electricalPowerSource.power,
                "distinctNetworks" to (m.shaft !== g.shaft), "clutchPresent" to (c != null),
                "slipping" to (c?.slipping ?: false), "controlV" to (c?.inputGate?.voltage ?: 0.0),
                "plateWear" to (c?.clutchPlateDescriptor?.getWear(c.clutchPlateStack!!) ?: 0.0),
                "ports" to (c?.shaftConnectivity?.map { it.name } ?: emptyList<String>()))
        }
        val normal = rig("Iron Clutch Plate")
        val normalIds = listOf("eln:clutch", "eln:shaft_motor", "eln:generator")
        fun clutch() = machine(normal) as ClutchElement
        var initialDelta = 0.0
        var initialWear = 0.0
        steps += NativeCampaignFixtures.Step("clutch-mismatch", "Preinstalled disengaged iron clutch separates a running motor from a stationary load", normal, normalIds, 100,
            begin = { voltage(normal.west(2).north(2), 480.0) }, sample = { measure(normal) },
            ready = { (machine(normal.west(2)) as MotorElement).shaft.rads > 120.0 }, verify = {
                val values = measure(normal)
                check(values["distinctNetworks"] == true && values["deltaRadS"] as Double > 100.0)
                NativeCampaignOracles.near(clutch().inputGate.voltage, 0.0, .1, "Disengaged clutch input")
                initialDelta = values["deltaRadS"] as Double
                initialWear = values["plateWear"] as Double
                values
            })
        steps += NativeCampaignFixtures.Step("clutch-slipping", "Apply a 1 V clutch command; observe slip, torque transfer and plate wear", normal, normalIds, 8,
            begin = { voltage(normal.south(2), Eln.SVU * .2) }, sample = { measure(normal) }, verify = {
                val values = measure(normal)
                NativeCampaignOracles.near(clutch().inputGate.voltage, Eln.SVU * .2, .1, "Clutch command")
                check(clutch().slipping && values["distinctNetworks"] == true)
                check((values["deltaRadS"] as Double) < initialDelta && values["generatorRadS"] as Double > 0.0)
                check(values["plateWear"] as Double > initialWear)
                values + mapOf("initialDeltaRadS" to initialDelta, "initialWear" to initialWear)
            })
        steps += NativeCampaignFixtures.Step("clutch-synchronised", "Iron clutch synchronises speed while keeping separate shaft networks", normal, normalIds, 20,
            sample = { measure(normal) }, ready = { !clutch().slipping }, verify = {
                val values = measure(normal)
                check(values["distinctNetworks"] == true)
                NativeCampaignOracles.near(values["deltaRadS"] as Double, 0.0, .1, "Locked clutch speed difference")
                check(values["generatorW"] as Double > 1.0 && node(normal) != null)
                values
            })
        val explosive = rig("Coal Clutch Plate")
        steps += NativeCampaignFixtures.Step("clutch-coal-mismatch", "Preinstalled coal clutch reaches its unsafe engagement precondition", explosive, normalIds, 100,
            begin = { voltage(explosive.west(2).north(2), 480.0) }, sample = { measure(explosive) },
            ready = { (machine(explosive.west(2)) as MotorElement).shaft.rads > 100.0 }, verify = {
                val c = machine(explosive) as ClutchElement
                check(c.clutchPlateDescriptor!!.explodes && abs(c.leftShaft.rads - c.rightShaft.rads) > 5.0)
                check(c.inputGate.voltage < .1)
                measure(explosive)
            })
        var coalDelta = 0.0
        steps += NativeCampaignFixtures.Step("clutch-coal-destroyed", "Coal plate engagement above 5 rad/s mismatch destroys the clutch", explosive, normalIds, 12,
            begin = {
                val c = machine(explosive) as ClutchElement
                coalDelta = abs(c.leftShaft.rads - c.rightShaft.rads)
                check(coalDelta > 5.0)
                voltage(explosive.south(2), Eln.SVU)
            }, sample = { measure(explosive) }, verify = {
                check(node(explosive) == null && world.getBlockState(explosive).isAir)
                retained.remove(explosive)
                check((machine(explosive.west(2)) as MotorElement).shaft !== (machine(explosive.east(2)) as GeneratorElement).shaft)
                measure(explosive) + mapOf("engagementDeltaRadS" to coalDelta, "expectedDestruction" to true)
            })
    }
}
