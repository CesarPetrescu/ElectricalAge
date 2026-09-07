package mods.eln.transparentnode.floodlight

import mods.eln.item.lampitem.BoilerplateLampData
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.lightblock.LightBlockEntity
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils.getItemObject
import mods.eln.sim.IProcess
import net.minecraft.world.item.ItemStack
import kotlin.math.*
import mods.eln.misc.isNothing

class FloodlightProcess(val element: FloodlightElement) : IProcess {

    companion object {
        // How often to create a light block within a given beam of light
        const val LIGHT_BLOCK_FREQUENCY = 2
        // Base length of a light beam
        const val BASE_THROW_DISTANCE = 16
    }

    private var processElapsedTime = 0.0

    override fun process(time: Double) {
        if (element.motorized) {
            element.swivelAngle = (element.swivelControl.normalized) * FloodlightGui.MAX_HORIZONTAL_ANGLE
            element.headAngle = (element.headControl.normalized) * FloodlightGui.MAX_VERTICAL_ANGLE
            element.beamWidth = (element.beamControl.normalized) * FloodlightGui.MAX_BEAM_WIDTH
        }

        val lampStacks = mutableListOf<ItemStack?>()
        val lampLightValues = mutableListOf<Int>()
        val lampLightRanges = mutableListOf<Int>()

        lampStacks.add(element.inventory.getItem(FloodlightContainer.LAMP_SLOT_1_ID))
        lampStacks.add(element.inventory.getItem(FloodlightContainer.LAMP_SLOT_2_ID))

        for ((idx, lampStack) in lampStacks.withIndex()) {
            if (!lampStack.isNothing()) {
                val lampDescriptor = getItemObject(lampStack) as LampDescriptor
                val lampData = lampDescriptor.lampData
                val lampVoltage = abs(element.electricalLoad.voltage)

                if (lampVoltage > (lampData.nominalU * lampData.technology.minimalUFactor)) {
                    val num: Double = lampVoltage - (lampData.nominalU * lampData.technology.minimalUFactor)
                    val den: Double = lampData.nominalU - (lampData.nominalU * lampData.technology.minimalUFactor)

                    lampLightValues.add(((num / den) * lampData.nominalLightValue).toInt())

                    if (lampLightValues[idx] < BoilerplateLampData.MIN_LIGHT_VALUE) lampLightValues[idx] = BoilerplateLampData.MIN_LIGHT_VALUE
                    else if (lampLightValues[idx] > BoilerplateLampData.MAX_LIGHT_VALUE) lampLightValues[idx] = BoilerplateLampData.MAX_LIGHT_VALUE

                    lampLightRanges.add(BASE_THROW_DISTANCE)
                } else {
                    lampLightValues.add(BoilerplateLampData.MIN_LIGHT_VALUE)
                    lampLightRanges.add(0)
                }

                /* Only decrease the life of a bulb once a second. This reduces the update rate at which the NBT is changed
                 * to once per second from once per tick, reducing the probability of an NBT mismatch bug occurring when
                 * shift-clicking. When the bug is eventually fixed, the processElapsedTime variable and supporting code can
                 * be deleted. Also update the decreaseLampLife function definition according to the note there.
                */
                if (processElapsedTime in -0.001..0.001) {
                    val lampLife = lampDescriptor.decreaseLampLife(lampStack, lampVoltage)

                    if (lampLife <= 0.0) {
                        lampLightValues[idx] = BoilerplateLampData.MIN_LIGHT_VALUE
                        element.inventory.setItem(idx, ItemStack.EMPTY)
                        element.inventory.setChanged()
                    }
                }
            } else {
                lampLightValues.add(BoilerplateLampData.MIN_LIGHT_VALUE)
                lampLightRanges.add(0)
            }
        }

        val newLightValue = max(lampLightValues[FloodlightContainer.LAMP_SLOT_1_ID], lampLightValues[FloodlightContainer.LAMP_SLOT_2_ID])
        val newLightRange = lampLightRanges[FloodlightContainer.LAMP_SLOT_1_ID] + lampLightRanges[FloodlightContainer.LAMP_SLOT_2_ID]

        // Only run raytracing when the floodlight is actually on.
        if (newLightValue > BoilerplateLampData.MIN_LIGHT_VALUE) placeSpots(newLightValue, newLightRange)

        if (newLightValue != element.node!!.lightValue) {
            element.node!!.lightValue = newLightValue
            element.powered = newLightValue > BoilerplateLampData.MIN_LIGHT_VALUE
            element.needPublish()
        }

        processElapsedTime += time
        if (processElapsedTime >= 1.0) processElapsedTime = 0.0
    }

    private fun placeSpots(lightValue: Int, lightRange: Int) {
        val origin = element.node!!.coordinate.toVec3()
        val lbCoordinate = Coordinate(origin, element.node!!.coordinate.dimension)
        val rays = FloodlightOptics.rays(element.swivelAngle, element.headAngle, element.beamWidth,
            element.rotationAxis, element.blockFacing)
        val position = DoubleArray(3)
        for ((step, lengthFactor) in rays) {
            val throwDistance = (lightRange * lengthFactor).toInt()
            for (distance in 1..throwDistance) {
                // Reuse the coordinate buffer: a wide, two-bulb beam walks thousands of cells per tick.
                position[0] = origin.x + step.x * distance
                position[1] = origin.y + step.y * distance
                position[2] = origin.z + step.z * distance
                lbCoordinate.setPosition(position)
                if (!lbCoordinate.blockExist ||
                    lbCoordinate.blockState.isSolidRender(lbCoordinate.world(), lbCoordinate.pos)) {
                    position[0] -= step.x
                    position[1] -= step.y
                    position[2] -= step.z
                    lbCoordinate.setPosition(position)
                    LightBlockEntity.addLight(lbCoordinate, lightValue, 5)
                    break
                }
                if (distance % LIGHT_BLOCK_FREQUENCY == 0 || distance == throwDistance) {
                    LightBlockEntity.addLight(lbCoordinate, lightValue, 5)
                }
            }
        }
    }
}
