package mods.eln.devtest;

import mods.eln.gui.GuiContainerEln;
import mods.eln.gui.GuiHelper;
import mods.eln.gui.GuiScreenEln;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** QA-only observation: helper controls are not part of vanilla Screen.children(). */
public final class MenuGeometryChecks {
    private static final Map<Screen,Integer> BEFORE_RESIZE = new WeakHashMap<>();
    private MenuGeometryChecks() {}

    public static void verify(Screen screen, boolean initialSize) {
        if (screen == null) throw new AssertionError("No screen for geometry check");
        try {
            GuiHelper helper = null;
            if (screen instanceof GuiContainerEln container) helper = container.helper;
            else if (screen instanceof GuiScreenEln) {
                var field = GuiScreenEln.class.getDeclaredField("helper");
                field.setAccessible(true);
                helper = (GuiHelper)field.get(screen);
            }
            for (var child : screen.children()) if (child instanceof AbstractWidget widget) visibleBounds(screen,widget);
            if (helper == null) return; // Non-legacy screens keep their own widget implementation.
            var field = GuiHelper.class.getDeclaredField("objectList");
            field.setAccessible(true);
            List<?> controls = (List<?>)field.get(helper);
            if (initialSize) BEFORE_RESIZE.put(screen,controls.size());
            else if (BEFORE_RESIZE.getOrDefault(screen,0)>0 && controls.isEmpty())
                throw new AssertionError("Resize lost all legacy controls: before="+BEFORE_RESIZE.get(screen)+" after=0");
            int left=(screen.width-helper.xSize)/2, top=(screen.height-helper.ySize)/2;
            System.out.println("NATIVE_MENU_GEOMETRY "+screen.getClass().getName()+" viewport="+screen.width+"x"+screen.height
                +" panel="+helper.xSize+"x"+helper.ySize+" origin="+left+","+top+" legacyControls="+controls.size());
            if (left<0 || top<0 || left+helper.xSize>screen.width || top+helper.ySize>screen.height)
                throw new AssertionError("Panel outside viewport: viewport="+screen.width+"x"+screen.height+" panel="+helper.xSize+"x"+helper.ySize+" origin="+left+","+top);
            for (Object object : controls) if (object instanceof AbstractWidget widget) visibleBounds(screen,widget);
            if (screen instanceof GuiContainerEln container) {
                for (var slot : container.getMenu().slots) if (slot.isActive()) {
                    int x=container.getGuiLeft()+slot.x, y=container.getGuiTop()+slot.y;
                    if(x<0 || y<0 || x+16>screen.width || y+16>screen.height)
                        throw new AssertionError("Inventory slot outside viewport: "+slot.index+" at "+x+","+y);
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not inspect actual legacy GUI structures",error);
        }
    }
    private static void visibleBounds(Screen screen, AbstractWidget widget) {
        if(!widget.visible)return;
        if(widget.getX()<0 || widget.getY()<0 || widget.getX()+widget.getWidth()>screen.width || widget.getY()+widget.getHeight()>screen.height)
            throw new AssertionError("Visible control outside "+screen.width+"x"+screen.height+": "+widget.getMessage().getString()
                +" x="+widget.getX()+" y="+widget.getY()+" width="+widget.getWidth()+" height="+widget.getHeight());
    }
}
