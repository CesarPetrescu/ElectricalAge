package mods.eln.sixnode.lampsocket.objrender

import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.sixnode.lampsocket.LampSocketRender
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType

interface ILampSocketObjRender {

    fun connectionEdges(front: mods.eln.misc.LRDU, offset: Double): FloatArray

    fun draw(descriptor: LampSocketDescriptor, type: ItemRenderType, distanceToPlayer: Double)

    fun draw(render: LampSocketRender, distanceToPlayer: Double)

}
