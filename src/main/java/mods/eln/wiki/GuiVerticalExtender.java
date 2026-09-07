package mods.eln.wiki;

import mods.eln.gui.Gui;
import mods.eln.gui.GuiHelper;
import mods.eln.gui.IGuiObject;
import java.util.ArrayList;

/** GUI-coordinate viewport. Drawing, hit testing and scrolling share the same absolute bounds. */
public class GuiVerticalExtender extends Gui implements IGuiObject {
    public final GuiHelper helper;
    int posX, posY, w, h;
    final ArrayList<IGuiObject> objectList = new ArrayList<>();
    private float scroll;
    private boolean dragging;

    public GuiVerticalExtender(int x, int y, int w, int h, GuiHelper helper) {
        posX = x; posY = y; this.w = w; this.h = h; this.helper = helper;
    }

    public int contentWidth() { return w - 14; }
    public int maxScroll() {
        int bottom = 0;
        for (IGuiObject o : objectList) bottom = Math.max(bottom, o.getYMax());
        return Math.max(0, bottom + 8 - h);
    }
    public float getSliderPosition() { return -scroll; }
    public void setSliderPosition(float position) {
        scroll = Float.isFinite(position) ? Math.max(0, Math.min(maxScroll(), -position)) : 0;
    }
    public void add(IGuiObject o) { objectList.add(o); }
    public void clear() { objectList.clear(); scroll = 0; }
    void remove(IGuiObject o) { objectList.remove(o); }
    public boolean contains(double x, double y) {
        return x >= posX && x < posX + w && y >= posY && y < posY + h;
    }
    private boolean containsContent(double x, double y) {
        return contains(x, y) && x < posX + contentWidth();
    }
    public boolean scroll(double x, double y, double delta) {
        if (!contains(x, y)) return false;
        setSliderPosition(getSliderPosition() + (float) delta * 24);
        return true;
    }
    public void scrollPage(int direction) { setSliderPosition(getSliderPosition() + direction * h * .85f); }
    private int thumbHeight() { return Math.max(16, h * h / (h + maxScroll())); }
    private int thumbY() { return posY + (maxScroll() == 0 ? 0 : Math.round(scroll / maxScroll() * (h - thumbHeight()))); }

    @Override
    public void idraw(int x, int y, float partialTick) {
        setSliderPosition(getSliderPosition());
        var g = graphics();
        g.flush();
        // posX/posY are ALREADY screen coordinates, including GuiHelper.add's translation.
        g.enableScissor(posX, posY, posX + contentWidth(), posY + h);
        g.pose().pushPose();
        try {
            g.pose().translate(posX, posY - Math.round(scroll), 0);
            for (IGuiObject o : objectList) {
                if (o instanceof GuiItemStack item &&
                    (item.posY + item.h < scroll || item.posY > scroll + h)) continue;
                o.idraw(x - posX, y - posY + Math.round(scroll), partialTick);
            }
            g.flush();
        } finally {
            g.pose().popPose();
            g.disableScissor();
        }
        if (maxScroll() > 0) {
            g.fill(posX + w - 8, posY, posX + w - 2, posY + h, 0xFF30434D);
            g.fill(posX + w - 8, thumbY(), posX + w - 2, thumbY() + thumbHeight(), 0xFF83BFAF);
        }
    }

    @Override
    public void idraw2(int x, int y) {
        if (!containsContent(x, y) || dragging) return;
        int localX = x - posX, localY = y - posY + Math.round(scroll);
        for (IGuiObject o : objectList) {
            if (o instanceof GuiItemStack item && item.contains(localX, localY)) {
                item.renderTooltip(x, y); // Screen coordinates, with no scrolled pose on the tooltip.
                return;
            }
        }
        // Other users (the Modbus editor) still host legacy text fields with hover help.
        var g = graphics();
        g.pose().pushPose();
        try {
            g.pose().translate(posX, posY - Math.round(scroll), 0);
            for (IGuiObject o : objectList) if (!(o instanceof GuiItemStack)) o.idraw2(localX, localY);
        } finally { g.pose().popPose(); }
    }

    @Override
    public boolean ikeyTyped(char key, int code) {
        for (IGuiObject o : new ArrayList<>(objectList)) if (o.ikeyTyped(key, code)) return true;
        return false;
    }
    @Override
    public void imouseClicked(int x, int y, int button) {
        if (!contains(x, y)) return;
        if (button == 0 && x >= posX + contentWidth() && maxScroll() > 0) {
            dragging = true;
            imouseMove(x, y);
        } else if (containsContent(x, y)) {
            for (IGuiObject o : new ArrayList<>(objectList))
                o.imouseClicked(x - posX, y - posY + Math.round(scroll), button);
        }
    }
    @Override
    public void imouseMove(int x, int y) {
        if (dragging) {
            scroll = Math.max(0, Math.min(maxScroll(),
                (float) (y - posY - thumbHeight() / 2) / Math.max(1, h - thumbHeight()) * maxScroll()));
        }
        if (containsContent(x, y))
            for (IGuiObject o : objectList) o.imouseMove(x - posX, y - posY + Math.round(scroll));
    }
    @Override
    public void imouseMovedOrUp(int x, int y, int button) {
        if (button == 0) dragging = false;
        // Release even outside the viewport so child buttons cannot remain pressed.
        for (IGuiObject o : new ArrayList<>(objectList))
            o.imouseMovedOrUp(x - posX, y - posY + Math.round(scroll), button);
    }
    @Override
    public void translate(int x, int y) { posX += x; posY += y; }
    @Override
    public int getYMax() { return posY + h; }
}
