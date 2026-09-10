package mods.eln.mechanical

import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.node.transparent.TransparentNodeElementRender
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real shaft traversal and node lookup with inert port owners; no server or energy injection. */
class ShaftTopologyTest {
    private var previousManager: NodeManager? = null
    private lateinit var manager: NodeManager
    private val descriptor = TransparentNodeDescriptor("topology-test", Part::class.java, TransparentNodeElementRender::class.java)

    private class Part(node: TransparentNode, descriptor: TransparentNodeDescriptor, val coupled: Boolean) :
        TransparentNodeElement(node, descriptor), ShaftElement {
        private val networks = mutableMapOf<Direction, ShaftNetwork>()
        var removing = false
        override val shaftMass = 1.0
        override val shaftConnectivity = arrayOf(Direction.XN, Direction.XP)
        override fun coordonate() = node!!.coordinate
        override fun getShaft(dir: Direction) = networks[dir]
        override fun setShaft(dir: Direction, net: ShaftNetwork?) { if (net != null) networks[dir] = net }
        override fun isInternallyConnected(a: Direction, b: Direction) = coupled
        override fun isShaftElementDestructing() = removing
        override fun initialize() {}
    }

    @Before fun setup() { previousManager = NodeManager.instance; manager = NodeManager("shaft-topology-test") }
    @After fun restore() { manager.clear(); NodeManager.instance = previousManager }
    private fun part(x: Int, coupled: Boolean = true): Part {
        val node = TransparentNode().apply { coordinate = Coordinate(x, 0, 0, Int.MIN_VALUE) }
        val part = Part(node, descriptor, coupled)
        node.element = part
        manager.addNode(node)
        return part
    }
    private fun separateRig(): List<Part> {
        val left = part(-1); val middle = part(0, false); val right = part(1)
        val leftNetwork = ShaftNetwork(left, left.shaftConnectivity.iterator())
        leftNetwork.takeAll(ShaftNetwork(middle, Direction.XN))
        val rightNetwork = ShaftNetwork(right, right.shaftConnectivity.iterator())
        rightNetwork.takeAll(ShaftNetwork(middle, Direction.XP))
        assertNotSame(left.getShaft(Direction.XP), right.getShaft(Direction.XN))
        return listOf(left, middle, right)
    }
    @Test fun rebuildMustNotJumpAcrossAnOpenTwoPortElement() {
        val (left, middle, right) = separateRig()
        left.getShaft(Direction.XP)!!.rebuildNetwork()
        assertSame(left.getShaft(Direction.XP), middle.getShaft(Direction.XN))
        assertSame(right.getShaft(Direction.XN), middle.getShaft(Direction.XP))
        assertNotSame(left.getShaft(Direction.XP), right.getShaft(Direction.XN))
    }
    @Test fun disconnectExcludesTheOwnerBeforeWorldRemovalCompletes() {
        val (left, middle, right) = separateRig()
        // This mirrors ClutchElement.onBreakElement: both disconnect calls run
        // while the old node is still in NodeManager and before its base teardown.
        middle.getShaft(Direction.XN)!!.disconnectShaft(middle)
        middle.getShaft(Direction.XP)!!.disconnectShaft(middle)
        assertSame(middle, (manager.getNodeFromCoordonate(middle.coordonate()) as TransparentNode).element)
        assertNotSame(left.getShaft(Direction.XP), right.getShaft(Direction.XN))
        assertTrue(left.getShaft(Direction.XP)!!.parts.none { it.element === middle })
        assertTrue(right.getShaft(Direction.XN)!!.parts.none { it.element === middle })
    }
    @Test fun rigidConnectedPortsStillRebuildTogether() {
        val left = part(-1); val middle = part(0); val right = part(1)
        val network = ShaftNetwork(left, left.shaftConnectivity.iterator())
        network.takeAll(ShaftNetwork(middle, middle.shaftConnectivity.iterator()))
        network.takeAll(ShaftNetwork(right, right.shaftConnectivity.iterator()))
        network.rebuildNetwork()
        assertSame(left.getShaft(Direction.XP), middle.getShaft(Direction.XN))
        assertSame(middle.getShaft(Direction.XP), right.getShaft(Direction.XN))
        assertEquals(6, left.getShaft(Direction.XP)!!.parts.size)
    }
    @Test fun rigidRemovalCannotRetainAResolvableGhostBridge() {
        val left = part(-1); val middle = part(0); val right = part(1)
        val network = ShaftNetwork(left, left.shaftConnectivity.iterator())
        network.takeAll(ShaftNetwork(middle, middle.shaftConnectivity.iterator()))
        network.takeAll(ShaftNetwork(right, right.shaftConnectivity.iterator()))
        network.disconnectShaft(middle)
        assertNotSame(left.getShaft(Direction.XP), right.getShaft(Direction.XN))
        assertTrue(left.getShaft(Direction.XP)!!.parts.none { it.element === middle })
        assertTrue(right.getShaft(Direction.XN)!!.parts.none { it.element === middle })
    }
}
