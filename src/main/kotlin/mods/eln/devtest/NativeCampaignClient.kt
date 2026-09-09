package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as Side
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.mechanical.ShaftRender
import mods.eln.sixnode.logicgate.LogicGateElement
import mods.eln.sixnode.logicgate.LogicGateDescriptor
import mods.eln.sixnode.logicgate.Oscillator
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.event.tick.ServerTickEvent
import mods.eln.transparentnode.OneWayDcDcElement
import mods.eln.transparentnode.VariableDcDcElement
import mods.eln.transparentnode.battery.BatteryElement
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture

/** Opt-in packaged-client acceptance. Authoritative observations execute on the server thread. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.CLIENT])
object NativeCampaignClient {
    private val mc get()=Minecraft.getInstance()
    private val suite get()=System.getProperty("eln.nativeCampaign","")
    private val restart get()=System.getProperty("eln.nativeCampaignRestart","false")=="true"
    private val runId get()=System.getProperty("eln.nativeCampaignRunId","")
    private val dir get()=Path.of(System.getProperty("eln.nativeCampaignDirectory")).toAbsolutePath()
    private val gson=GsonBuilder().setPrettyPrinting().create()
    private var stage=0
    private var tick=0
    @Volatile private var finished=false
    private var started=System.nanoTime()
    private var phaseStarted=0L
    private var index=0
    private var stepStartedTick=0L
    private var work: CompletableFuture<*>?=null
    private lateinit var fixtures: NativeCampaignFixtures
    private var steps=listOf<NativeCampaignFixtures.Step>()
    private var gallery=listOf<BlockContracts.Entry>()
    private var galleryIndex=0
    private val results=mutableListOf<Map<String,Any>>()
    private val traces=mutableListOf<Map<String,Any>>()
    private val frameTimes=mutableListOf<Double>()
    private var lastFrame=0L
    private var observation=emptyMap<String,Any>()
    private var runtime=emptyMap<String,Any>()
    private var sourceSha=""
    private var currentId="boot"
    private var uiDispatched=false
    private var lastWorldTick=-1L
    private var angleBefore:Double?=null
    private var settledFrames=0
    private val serverTimes=java.util.Collections.synchronizedList(mutableListOf<Double>())
    private var serverTickStart=0L

    // KFF registers this object instance: event handlers must not be static.
    @SubscribeEvent fun serverPre(event:ServerTickEvent.Pre) {
        if(suite.isNotEmpty() && !finished)serverTickStart=System.nanoTime()
    }
    @SubscribeEvent fun serverPost(event:ServerTickEvent.Post) {
        if(suite.isNotEmpty() && !finished && serverTickStart!=0L && serverTimes.size<100000)
            serverTimes+=(System.nanoTime()-serverTickStart)/1e6
    }
    @SubscribeEvent fun frame(event:RenderFrameEvent.Post) {
        if(suite.isEmpty() || finished)return
        val now=System.nanoTime()
        if(lastFrame!=0L && mc.level!=null && mc.screen==null && frameTimes.size<100000)frameTimes+=(now-lastFrame)/1e6
        lastFrame=now
    }
    private fun write(name:String,data:Any) {
        Files.createDirectories(dir)
        val tmp=dir.resolve("$name.tmp")
        Files.writeString(tmp,gson.toJson(data))
        Files.move(tmp,dir.resolve(name),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    private fun report(complete:Boolean) {
        write("report.json",mapOf("schema" to 1,"suite" to suite,"restart" to restart,"runId" to runId,"jarSha256" to sourceSha,"complete" to complete,"results" to results,"expected" to steps.map { it.id },"galleryExpected" to gallery.map(::galleryId),"runtime" to runtime))
    }
    private fun next(value:Int) {stage=value;tick=0;phaseStarted=System.nanoTime();work=null;uiDispatched=false;settledFrames=0}
    private fun node(p:BlockPos)=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x,p.y,p.z,mc.singleplayerServer!!.overworld()))
    private fun identity(p:BlockPos):String = when(val n=node(p)) {
        is TransparentNode -> BuiltInRegistries.ITEM.getKey(n.element!!.descriptor!!.parentItem).toString()
        is SixNode -> BuiltInRegistries.ITEM.getKey(n.getElement(Side.YN)!!.sixNodeElementDescriptor.parentItem).toString()
        else -> BuiltInRegistries.BLOCK.getKey(mc.singleplayerServer!!.overworld().getBlockState(p).block).toString()
    }
    private fun camera(p:BlockPos,wide:Boolean=true) {
        val player=mc.singleplayerServer!!.playerList.players.first();val world=player.serverLevel()
        player.setGameMode(GameType.CREATIVE);player.abilities.flying=true;player.onUpdateAbilities();player.setNoGravity(true)
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        val target=Vec3(p.x+.5,p.y+.5,p.z+.5)
        val eye=target.add(if(wide)6.5 else 1.7,if(wide)5.5 else 1.8,if(wide)6.5 else 1.7)
        player.teleportTo(world,eye.x,eye.y-player.eyeHeight,eye.z,0f,0f);player.lookAt(EntityAnchorArgument.Anchor.EYES,target)
    }
    private fun galleryCamera(e:BlockContracts.Entry) {
        val player=mc.singleplayerServer!!.playerList.players.first();val world=player.serverLevel()
        player.setGameMode(GameType.CREATIVE);player.abilities.flying=true;player.onUpdateAbilities();player.setNoGravity(true)
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        var target=Vec3(e.x+.5,e.y+.5,e.z+.5)
        val eye=if(e.kind=="six") {
            val face=Side.fromInt(e.side)!!.toFacing()
            val outward=Vec3.atLowerCornerOf(face.normal).scale(-1.0)
            target=target.add(outward.scale(-.4))
            val offset=if(face.axis==net.minecraft.core.Direction.Axis.Y)Vec3(.65,0.0,.5)
                else if(face.axis==net.minecraft.core.Direction.Axis.X)Vec3(0.0,.4,.65) else Vec3(.65,.4,0.0)
            target.add(outward.scale(2.5)).add(offset)
        } else target.add(3.5,2.5,3.5)
        player.teleportTo(world,eye.x,eye.y-player.eyeHeight,eye.z,0f,0f);player.lookAt(EntityAnchorArgument.Anchor.EYES,target)
    }
    private fun capture(id:String):String {
        val relative="screenshots/$id.png";val dest=dir.resolve(relative);Files.createDirectories(dest.parent)
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { image -> check(image.width>=640 && image.height>=400);image.writeToFile(dest) }
        check(Files.size(dest)>1024)
        return relative
    }
    private fun record(id:String,title:String,components:List<String>,observed:Map<String,Any>,kind:String="functional") {
        currentId=id
        results+=mapOf("id" to id,"title" to title,"components" to components,"kind" to kind,"status" to "passed","screenshot" to capture(id),"observation" to observed,"clientGameTime" to mc.level!!.gameTime,"camera" to listOf(mc.player!!.x,mc.player!!.y,mc.player!!.z))
        report(false)
    }
    private fun field(o:Any,name:String):Any {val f=o.javaClass.getDeclaredField(name);f.isAccessible=true;return f.get(o)}
    private fun typeNative(value:String,converter:Boolean) {
        val screen=mc.screen ?: error("Native screen missing")
        val widget=(if(converter)field(field(screen,"controls"),"value") else field(screen,"voltage")) as GuiTextFieldEln
        check(widget.visible && widget.active)
        screen.mouseClicked(widget.x+widget.width/2.0,widget.y+widget.height/2.0,0)
        screen.mouseReleased(widget.x+widget.width/2.0,widget.y+widget.height/2.0,0)
        check(widget.isFocused)
        screen.keyPressed(GLFW.GLFW_KEY_END,0,0)
        repeat(150){screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE,0,0)}
        value.forEach { screen.charTyped(it,0) };check(widget.text==value)
        screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0)
    }
    private fun galleryId(e:BlockContracts.Entry)="gallery-${e.id.replace(':','-')}-${e.descriptor}-${e.side}"
    private fun prepareGallery() {
        val path=mc.singleplayerServer!!.getWorldPath(LevelResource.ROOT).resolve("eln-contracts.json")
        val all=JsonParser.parseString(Files.readString(path)).asJsonArray.map { gson.fromJson(it,BlockContracts.Entry::class.java) }
        check(all.isNotEmpty() && all.map(::galleryId).distinct().size==all.size)
        val shard=listOf("power","logic","mechanical","storage-thermal").indexOf(suite);check(shard>=0)
        gallery=all.sortedBy(::galleryId).filterIndexed { i,_ -> i%4==shard }
        val descriptors=(Eln.sixNodeItem.subItemList.values+Eln.transparentNodeItem.subItemList.values).filterNotNull()
        write("coverage.json",mapOf("runId" to runId,"jarSha256" to sourceSha,"registry" to descriptors.map {
            mapOf("id" to BuiltInRegistries.ITEM.getKey(it.parentItem).toString(),"descriptor" to it.parentItemDamage,"name" to it.name,"implementation" to it.javaClass.name)
        },"galleryAll" to all,"assignedGallery" to gallery.map(::galleryId),"functionalCases" to steps.map { mapOf("id" to it.id,"components" to it.components) },"scope" to "Gallery is placement/client-presence/render capture, not proof of every component's functional behavior."))
    }
    private fun logicState(e:LogicGateElement):Map<String,String> {
        val tag=CompoundTag();e.writeToNBT(tag)
        return tag.allKeys.filter{it.startsWith("function")}.sorted().associateWith{tag.get(it).toString()}
    }
    private fun persist() {
        val s=mc.singleplayerServer!!;val w=s.overworld()
        val data=fixtures.retained.map { p ->
            check(node(p)!=null) { "Retained fixture disappeared before save: $p" }
            val e=(node(p) as? TransparentNode)?.element
            val settings=when(e){is OneWayDcDcElement->e.settings;is VariableDcDcElement->e.settings;else->null}
            mutableMapOf<String,Any>("x" to p.x,"y" to p.y,"z" to p.z,"id" to identity(p)).apply {
                settings?.let { put("mode",it.mode);put("value",it.value);put("version",it.version);put("enabled",it.enabled) }
                val logic=(node(p) as? SixNode)?.getElement(Side.YN) as? LogicGateElement
                if(logic!=null && (logic.sixNodeElementDescriptor as LogicGateDescriptor).function !is Oscillator)put("logicState",logicState(logic))
                if(e is BatteryElement) {
                    put("batteryJ",e.batteryProcess.energy);put("savedTick",w.gameTime)
                    put("passiveResistance",e.dischargeResistor.resistance);put("maximumBatteryV",e.descriptor.electricalU*3)
                }
            }
        }
        Files.writeString(s.getWorldPath(LevelResource.ROOT).resolve("native-campaign-state.json"),gson.toJson(data))
        s.saveEverything(false,true,true)
    }
    private fun restartPlan():List<NativeCampaignFixtures.Step> {
        val s=mc.singleplayerServer!!;val p=s.playerList.players.first();fixtures=NativeCampaignFixtures(s.overworld(),p)
        val data=JsonParser.parseString(Files.readString(s.getWorldPath(LevelResource.ROOT).resolve("native-campaign-state.json"))).asJsonArray
        check(data.size()>0) { "No retained state for $suite restart" }
        return data.mapIndexed { i,value -> val row=value.asJsonObject;val pos=BlockPos(row["x"].asInt,row["y"].asInt,row["z"].asInt)
            NativeCampaignFixtures.Step("restart-retained-$i","Same saved fixture after separate-JVM restart",pos,listOf(row["id"].asString),8,verify={
                check(identity(pos)==row["id"].asString);val e=(node(pos) as? TransparentNode)?.element
                val settings=when(e){is OneWayDcDcElement->e.settings;is VariableDcDcElement->e.settings;else->null}
                if(row.has("mode")){check(settings!=null);check(settings.mode==row["mode"].asString && settings.value==row["value"].asDouble && settings.enabled==row["enabled"].asBoolean && settings.version==row["version"].asInt)}
                if(row.has("logicState")) {
                    val l=(node(pos) as SixNode).getElement(Side.YN) as LogicGateElement
                    check(logicState(l)==row["logicState"].asJsonObject.entrySet().associate{it.key to it.value.asString})
                }
                if(e is BatteryElement) {
                    val elapsed=(s.overworld().gameTime-row["savedTick"].asLong).coerceAtLeast(0)*.05+2
                    val maxV=row["maximumBatteryV"].asDouble
                    val passiveJ=maxV*maxV/row["passiveResistance"].asDouble*elapsed
                    val before=row["batteryJ"].asDouble;val now=e.batteryProcess.energy
                    check(now.isFinite() && now<=before+.1 && now>=before-passiveJ-1.0) { "Battery state not conserved across restart: $before -> $now" }
                }
                mapOf("savedId" to row["id"].asString,"loadedId" to identity(pos),"settingsRestored" to row.has("mode"),"logicStateRestored" to row.has("logicState"),"batteryJ" to if(e is BatteryElement)e.batteryProcess.energy else "not a battery")
            })
        }
    }
    private fun finish() {
        finished=true
        val ordered=frameTimes.sorted()
        fun percentile(p:Double)=if(ordered.isEmpty())0.0 else ordered[((ordered.size-1)*p).toInt()]
        val server=synchronized(serverTimes){serverTimes.sorted()}
        fun serverPercentile(p:Double)=if(server.isEmpty())0.0 else server[((server.size-1)*p).toInt()]
        write("performance.json",mapOf("serverTickSamples" to server.size,"serverTickP50Ms" to serverPercentile(.5),"serverTickP95Ms" to serverPercentile(.95),"serverTickP99Ms" to serverPercentile(.99),"usedJvmHeapBytes" to java.lang.management.ManagementFactory.getMemoryMXBean().heapMemoryUsage.used,"runId" to runId,"totalSeconds" to (System.nanoTime()-started)/1e9,"worldFrameSamples" to ordered.size,"frameP50Ms" to percentile(.5),"frameP95Ms" to percentile(.95),"frameP99Ms" to percentile(.99),"note" to "Client frame intervals at a 60 FPS cap and separate integrated-server Pre/Post intervals, including test instrumentation. Used JVM heap is not per-network allocation."))
        write("trace.json",traces);report(true);mc.stop()
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(suite.isEmpty() || finished)return
        try {
            check((System.nanoTime()-started)/1e9<1500) { "Native suite exceeded 25 minute watchdog" }
            if(phaseStarted!=0L)check((System.nanoTime()-phaseStarted)/1e9<180) { "Stage $stage / $currentId did not advance" }
            tick++
            when(stage) {
                0 -> {
                    if(mc.screen !is TitleScreen || mc.overlay!=null || tick<20)return
                    check(FMLEnvironment.production)
                    val jar=ModList.get().getModFileById("eln").file.filePath;check(Files.isRegularFile(jar))
                    sourceSha=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)).joinToString(""){"%02x".format(it)}
                    check(sourceSha==System.getProperty("eln.nativeCampaignJarSha256"))
                    runtime=mapOf("os" to System.getProperty("os.name"),"arch" to System.getProperty("os.arch"),"java" to System.getProperty("java.version"),"glVendor" to (GL11.glGetString(GL11.GL_VENDOR)?:"unknown"),"glRenderer" to (GL11.glGetString(GL11.GL_RENDERER)?:"unknown"),"glVersion" to (GL11.glGetString(GL11.GL_VERSION)?:"unknown"),"pid" to ProcessHandle.current().pid(),"jarSha256" to sourceSha,"integratedServer" to true,"runId" to runId,"graphicsBackend" to System.getProperty("eln.nativeGraphicsBackend","system-opengl"))
                    write("runtime.json",runtime);capture("runtime-title");report(false)
                    mc.createWorldOpenFlows().openWorld("campaign"){error("Cannot open exact archived fixture world")};next(1)
                }
                1 -> {
                    if(mc.player==null || mc.level==null || mc.screen!=null || tick<40)return
                    mc.tutorial.setStep(net.minecraft.client.tutorial.TutorialSteps.NONE)
                    if(work==null) {work=mc.singleplayerServer!!.submit(Runnable {
                        val s=mc.singleplayerServer!!;val w=s.overworld();val p=s.playerList.players.first()
                        w.setDayTime(6000);w.setWeatherParameters(100000,0,false,false);w.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,s)
                        p.setGameMode(GameType.CREATIVE)
                        fixtures=NativeCampaignFixtures(w,p);steps=if(restart)restartPlan()else fixtures.prepare(suite)
                        if(!restart)prepareGallery()
                    });return}
                    if(!work!!.isDone)return;work!!.join();report(false);next(2)
                }
                2 -> {
                    if(index>=steps.size){next(6);return}
                    val step=steps[index];currentId=step.id
                    if(work==null){mc.player!!.closeContainer();mc.setScreen(null);mc.options.hideGui=false
                        work=mc.singleplayerServer!!.submit(Runnable {camera(step.target,step.view=="world")});return}
                    if(!work!!.isDone || tick<20)return;work!!.join()
                    if(!mc.level!!.getChunkSource().hasChunk(step.target.x shr 4,step.target.z shr 4))return
                    next(3)
                }
                3 -> {
                    val step=steps[index]
                    if(step.view!="world" && !uiDispatched) {
                        if(tick==1) {
                            mc.player!!.inventory.selected=(0..8).firstOrNull {mc.player!!.inventory.getItem(it).isEmpty}?:0
                            mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(step.target),net.minecraft.core.Direction.UP,step.target,false))
                            return
                        }
                        if(mc.screen==null || tick<15)return
                        typeNative(step.view.substringAfter(':'),step.view.startsWith("converter:"));uiDispatched=true
                    }
                    if(work==null){work=mc.singleplayerServer!!.submit(Runnable {step.begin();stepStartedTick=mc.singleplayerServer!!.overworld().gameTime;lastWorldTick=-1});angleBefore=((mc.level!!.getBlockEntity(step.target) as? TransparentNodeEntity)?.elementRender as? ShaftRender)?.angle;return}
                    if(!work!!.isDone)return;work!!.join();next(4)
                }
                4 -> {
                    val step=steps[index]
                    if(work==null){work=mc.singleplayerServer!!.submit<Boolean> {
                        val time=mc.singleplayerServer!!.overworld().gameTime
                        if(time!=lastWorldTick) {lastWorldTick=time;val values=step.sample();if(values.isNotEmpty())traces+=mapOf("case" to step.id,"serverTick" to time,"values" to values)}
                        if(time-stepStartedTick<step.waitTicks)false else {observation=step.verify()+mapOf("serverTick" to time,"elapsedServerTicks" to (time-stepStartedTick));true}
                    };return}
                    if(!work!!.isDone)return
                    val ready=work!!.join() as Boolean;work=null
                    if(ready)next(5)
                }
                5 -> {
                    val step=steps[index]
                    if(tick<5)return
                    val render=(mc.level!!.getBlockEntity(step.target) as? TransparentNodeEntity)?.elementRender
                    if(step.id in setOf("shaft-loaded","shaft-reconnect") && render is ShaftRender) {
                        check(angleBefore!=null && kotlin.math.abs(render.angle-angleBefore!!)>1e-6) { "Server shaft runs but client animation did not advance" }
                        observation=observation+mapOf("clientShaftAngleBefore" to angleBefore!!,"clientShaftAngleAfter" to render.angle)
                    }
                    observation=observation+mapOf("clientScreen" to (mc.screen?.javaClass?.name?:"world"))
                    record(step.id,step.title,step.components,observation)
                    next(7);work=mc.singleplayerServer!!.submit(Runnable {step.end()})
                }
                7 -> {if(work?.isDone!=true)return;work!!.join();index++;next(2)}
                6 -> {
                    if(restart){finish();return}
                    if(galleryIndex>=gallery.size) {
                        if(work==null){work=mc.singleplayerServer!!.submit(Runnable{persist()});return}
                        if(!work!!.isDone)return;work!!.join();finish();return
                    }
                    val entry=gallery[galleryIndex];currentId=galleryId(entry);val pos=BlockPos(entry.x,entry.y,entry.z)
                    if(work==null){mc.player!!.closeContainer();mc.setScreen(null);mc.options.hideGui=true
                        work=mc.singleplayerServer!!.submit(Runnable {
                            val n=node(pos)
                            when(entry.kind){"six"->check((n as? SixNode)?.sideElementIdList?.get(entry.side)==entry.descriptor);"transparent"->check((n as? TransparentNode)?.elementId==entry.descriptor);else->check(identity(pos)==entry.id)}
                            galleryCamera(entry)
                        });return}
                    if(!work!!.isDone || tick<20)return;work!!.join()
                    if(!mc.level!!.getChunkSource().hasChunk(pos.x shr 4,pos.z shr 4))return
                    val synchronizedRenderer=when(entry.kind) {
                        "six"->(mc.level!!.getBlockEntity(pos) as? SixNodeEntity)?.elementRenderList?.get(entry.side)!=null
                        "transparent"->(mc.level!!.getBlockEntity(pos) as? TransparentNodeEntity)?.elementRender!=null
                        else->!mc.level!!.getBlockState(pos).isAir
                    }
                    if(!synchronizedRenderer && tick<200){settledFrames=0;return}
                    check(synchronizedRenderer) { "Server component never reached native client: ${entry.id}" }
                    if(settledFrames++<5)return
                    record(galleryId(entry),"Registered component: ${entry.id}",listOf(entry.id),mapOf("descriptor" to entry.descriptor,"side" to entry.side,"position" to listOf(entry.x,entry.y,entry.z)),"gallery")
                    galleryIndex++;next(6)
                }
            }
        }catch(t:Throwable){
            finished=true
            runCatching{results+=mapOf("id" to "failure-$currentId","title" to "Native runtime failure","status" to "failed","kind" to "runtime","components" to emptyList<String>(),"detail" to t.stackTraceToString(),"screenshot" to capture("failure-$currentId"));write("trace.json",traces);report(true)}
            Eln.logger.error("Native campaign failed: {} / {}",suite,currentId,t);mc.stop()
        }
    }
}
