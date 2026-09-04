package mods.eln.transparentnode.floodlight

import mods.eln.item.lampitem.BoilerplateLampData
import mods.eln.item.lampitem.LampDescriptor
import mods.eln.lightblock.LightBlockEntity
import mods.eln.misc.Coordinate
import mods.eln.misc.HybridNodeDirection
import mods.eln.misc.HybridNodeDirection.*
import mods.eln.misc.Utils.getItemObject
import mods.eln.sim.IProcess
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d
import kotlin.math.*
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord

class FloodlightProcess(val element: FloodlightElement) : IProcess {

    companion object {
        // Number of light rays to be produced in each direction. Must be a mathematical integer!
        const val MAX_LIGHT_BEAM_COUNT = 4.0
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

        lampStacks.add(element.inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_1_ID))
        lampStacks.add(element.inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_2_ID))

        for ((idx, lampStack) in lampStacks.withIndex()) {
            if (lampStack != null) {
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
                        element.inventory.setInventorySlotContents(idx, ItemStack.EMPTY)
                        element.inventory.markDirty()
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

    /**
     * WARNING! BE VERY CAREFUL WHEN EDITING THIS FUNCTION!
     * The logic and math are very complex, and it is easy to break everything if you don't know what you are doing!
     */
    private fun placeSpots(lightValue: Int, lightRange: Int) {
        val rotationVectors = mutableListOf<Pair<Vec3d, Double>>()
        val fractionTable = mutableListOf<Double>()

        val rotationAxis = element.rotationAxis
        val facingDirection = element.blockFacing

        val horzAngle = element.swivelAngle
        val vertAngle = element.headAngle
        val offsetAngle = element.beamWidth / 2.0

        // Number of light rays to be produced in each cardinal direction, also extrapolated into the empty spaces between them.
        val beamCount = ceil((offsetAngle * 2.0) * (MAX_LIGHT_BEAM_COUNT / FloodlightGui.MAX_BEAM_WIDTH)).toInt()

        if (beamCount != 0) {
            for (idx in beamCount downTo -beamCount) {
                fractionTable.add(idx.toDouble() / beamCount.toDouble())
            }
        } else fractionTable.add(0.0)

        for (idx in fractionTable.indices) {
            val offsetAngleFraction = offsetAngle * fractionTable[idx]

            // Unit vectors for the central and vertical spots
            rotationVectors.add(Pair(createRotationVector(horzAngle, vertAngle + offsetAngleFraction, rotationAxis, facingDirection), offsetAngleFraction))

            if (fractionTable[idx] > 0.0) {
                for (jdx in fractionTable.indices) {
                    if (abs(fractionTable[jdx]) != 1.0) {
                        val diagonalAngle = 90.0 * fractionTable[jdx]

                        val (hAdj, kAdj) = calculateAngleAdjustments(vertAngle, offsetAngleFraction, diagonalAngle)

                        // Unit vectors for the horizontal and diagonal spots (mirrored across vertical axis)
                        rotationVectors.add(Pair(createRotationVector(horzAngle + hAdj, kAdj, rotationAxis, facingDirection), offsetAngleFraction))
                        rotationVectors.add(Pair(createRotationVector(horzAngle - hAdj, kAdj, rotationAxis, facingDirection), offsetAngleFraction))
                    }
                }
            }
        }

        for (idx in rotationVectors.indices) {
            val origin = element.node!!.coordinate.toVec3()
            val lbCoordinate = Coordinate(origin, element.node!!.coordinate.dimension)

            // Vec3d is immutable on 1.12, and this walks a ray one step per block, so the
            // position is carried in three doubles rather than reallocating a vector per step.
            var lightX = origin.x
            var lightY = origin.y
            var lightZ = origin.z
            val step = rotationVectors[idx].first
            val lightPosition = DoubleArray(3)

            fun placeAt(x: Double, y: Double, z: Double) {
                lightPosition[0] = x
                lightPosition[1] = y
                lightPosition[2] = z
                lbCoordinate.setPosition(lightPosition)
            }

            // This forces the light cone to be "flat" on the end, instead of curved.
            val throwDistance = lightRange / cos(toRadians(rotationVectors[idx].second))

            for (jdx in 0 until throwDistance.toInt()) {
                lightX += step.x
                lightY += step.y
                lightZ += step.z
                placeAt(lightX, lightY, lightZ)

                if (!lbCoordinate.blockExist || lbCoordinate.block.defaultState.isOpaqueCube) {
                    // Back off one step so the light lands in the last open block, not inside
                    // the wall the beam hit.
                    placeAt(lightX - step.x, lightY - step.y, lightZ - step.z)

                    LightBlockEntity.addLight(lbCoordinate, lightValue, 5)
                    break
                }

                // Place light blocks every few blocks along the path of a beam, as well as always at the beam's endpoint.
                if (jdx % LIGHT_BLOCK_FREQUENCY == (LIGHT_BLOCK_FREQUENCY - 1) || (jdx == throwDistance.toInt() - 1)) {
                    LightBlockEntity.addLight(lbCoordinate, lightValue, 5)
                }
            }
        }
    }

    private fun toRadians(angle: Double): Double {
        return angle * (Math.PI / 180.0)
    }

    private fun toDegrees(angle: Double): Double {
        return angle * (180.0 / Math.PI)
    }

    /**
     * WARNING! DO NOT EDIT THIS FUNCTION!
     * The math is very complex, and it is easy to break everything if you don't know what you are doing!
     * See https://www.desmos.com/3d/xqi6ov3fpn for a visualization of the equations and the raytracing results.
     * Trust me, the math is right! Any bugs that may arise result from improper usage of the parent function.
    */
    private fun calculateAngleAdjustments(vertAngle: Double, offsetAngle: Double, diagonalAngle: Double): Pair<Double, Double> {
        val k0 = toRadians(vertAngle)
        val o0 = toRadians(offsetAngle)
        val d0 = toRadians(diagonalAngle)

        val d = tan(d0)
        val o = acos(sqrt((cos(o0).pow(2) + d.pow(2)) / (1.0 + d.pow(2))))
        val b = atan(sqrt((cos(o).pow(2) / (cos(o).pow(2) - (d.pow(2) * sin(o).pow(2)))) - 1.0)) * sign(d0)
        val a = sqrt(cos(o).pow(2) / (1.0 + tan(k0 + b).pow(2)))

        val hAdj = toRadians(90.0) - atan(sign(cos(k0 + b)) * (a / sin(o)))
        val kAdj = atan(sign(sin(k0 + b)) * sqrt((cos(o).pow(2) - a.pow(2)) / (sin(o).pow(2) + a.pow(2))))

        return Pair(toDegrees(hAdj), toDegrees(kAdj))
    }

    private fun getRawRotationVector(horzAngle: Double, vertAngle: Double): Vec3d {
        val horzSin = sin(toRadians(horzAngle))
        val horzCos = cos(toRadians(horzAngle))

        val vertSin = sin(toRadians(vertAngle))
        val vertCos = cos(toRadians(vertAngle))

        return Vec3d(vertCos * horzSin, vertSin, vertCos * horzCos)
    }

    private fun createRotationVector(horzAngle: Double, vertAngle: Double, axis: HybridNodeDirection, facing: HybridNodeDirection): Vec3d {
        val oldV = getRawRotationVector(horzAngle, vertAngle)
        // Vec3d is immutable on 1.12: the rotated components are accumulated and the vector is
        // built once, at the end.
        var nx = 0.0
        var ny = 0.0
        var nz = 0.0

        when (axis) {
            XN -> {
                nx = -oldV.y

                when (facing) {
                    XN -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    XP -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    YN -> {
                        ny = -oldV.z
                        nz = oldV.x
                    }
                    YP -> {
                        ny = oldV.z
                        nz = -oldV.x
                    }
                    ZN -> {
                        ny = -oldV.x
                        nz = -oldV.z
                    }
                    ZP -> {
                        ny = oldV.x
                        nz = oldV.z
                    }
                }
            }
            XP -> {
                nx = oldV.y

                when (facing) {
                    XN -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    XP -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    YN -> {
                        ny = -oldV.z
                        nz = -oldV.x
                    }
                    YP -> {
                        ny = oldV.z
                        nz = oldV.x
                    }
                    ZN -> {
                        ny = oldV.x
                        nz = -oldV.z
                    }
                    ZP -> {
                        ny = -oldV.x
                        nz = oldV.z
                    }
                }
            }
            YN -> {
                ny = -oldV.y

                when (facing) {
                    XN -> {
                        nx = -oldV.z
                        nz = -oldV.x
                    }
                    XP -> {
                        nx = oldV.z
                        nz = oldV.x
                    }
                    YN -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    YP -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    ZN -> {
                        nx = oldV.x
                        nz = -oldV.z
                    }
                    ZP -> {
                        nx = -oldV.x
                        nz = oldV.z
                    }
                }
            }
            YP -> {
                ny = oldV.y

                when (facing) {
                    XN -> {
                        nx = -oldV.z
                        nz = oldV.x
                    }
                    XP -> {
                        nx = oldV.z
                        nz = -oldV.x
                    }
                    YN -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    YP -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    ZN -> {
                        nx = -oldV.x
                        nz = -oldV.z
                    }
                    ZP -> {
                        nx = oldV.x
                        nz = oldV.z
                    }
                }
            }
            ZN -> {
                nz = -oldV.y

                when (facing) {
                    XN -> {
                        nx = -oldV.z
                        ny = oldV.x
                    }
                    XP -> {
                        nx = oldV.z
                        ny = -oldV.x
                    }
                    YN -> {
                        nx = -oldV.x
                        ny = -oldV.z
                    }
                    YP -> {
                        nx = oldV.x
                        ny = oldV.z
                    }
                    ZN -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    ZP -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                }
            }
            ZP -> {
                nz = oldV.y

                when (facing) {
                    XN -> {
                        nx = -oldV.z
                        ny = -oldV.x
                    }
                    XP -> {
                        nx = oldV.z
                        ny = oldV.x
                    }
                    YN -> {
                        nx = oldV.x
                        ny = -oldV.z
                    }
                    YP -> {
                        nx = -oldV.x
                        ny = oldV.z
                    }
                    ZN -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                    ZP -> TODO("Unused - impossible facing direction. If you get this message there's a bug in the code.")
                }
            }
        }

        return Vec3d(nx, ny, nz)
    }

}