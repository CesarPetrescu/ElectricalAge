package mods.eln.wiki;

import mods.eln.gui.Gui;
import mods.eln.gui.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static mods.eln.i18n.I18N.tr;

/** Native 1.21 chrome; descriptor plug-ins still contribute to the scrollable document. */
public class Default extends Screen {
    protected final Screen preview;
    protected GuiHelper helper;
    protected GuiVerticalExtender extender;
    protected EditBox searchText;
    protected int left, top;
    private float savedScroll;
    private String query = "";

    public Default(Screen preview) {
        super(Component.literal(tr("Electrical Age Guide")));
        this.preview = preview;
    }

    @Override
    protected void init() {
        initGui();
        extender.setSliderPosition(savedScroll);
    }

    public void initGui() {
        helper = new GuiHelper(this, Math.min(440, width - 16), Math.min(360, height - 16));
        left = (width - helper.xSize) / 2;
        top = (height - helper.ySize) / 2;
        extender = new GuiVerticalExtender(left + 8, top + 56, helper.xSize - 16, helper.ySize - 78, helper);
        addRenderableWidget(Button.builder(Component.literal(tr("Previous")), b -> onClose())
            .bounds(left + 8, top + 28, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal(tr("Contents")), b -> minecraft.setScreen(new Root(null)))
            .bounds(left + 74, top + 28, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("×"), b -> minecraft.setScreen(null))
            .bounds(left + helper.xSize - 28, top + 5, 20, 20).build());
        searchText = new EditBox(font, left + 146, top + 29, helper.xSize - 156, 18, Component.literal(tr("Search items")));
        searchText.setMaxLength(150);
        searchText.setHint(Component.literal(tr("Search items")));
        searchText.setValue(query);
        searchText.setResponder(text -> {
            query = text;
            searchChanged(text);
        });
        addRenderableWidget(searchText);
    }

    protected String query() { return query; }
    protected void initialQuery(String text) { query = text; }

    protected void searchChanged(String text) {
        minecraft.setScreen(new Search(text, this));
    }

    @Override
    public void removed() {
        savedScroll = extender == null ? 0 : extender.getSliderPosition();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        savedScroll = extender == null ? 0 : extender.getSliderPosition();
        super.resize(mc, w, h);
    }

    @Override
    public void onClose() { minecraft.setScreen(preview); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return extender.scroll(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (extender.contains(x, y)) {
            setFocused(null);
            extender.imouseClicked((int) x, (int) y, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        extender.imouseMovedOrUp((int) x, (int) y, button);
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!searchText.isFocused()) {
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                extender.scrollPage(key == GLFW.GLFW_KEY_PAGE_DOWN ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                extender.setSliderPosition(key == GLFW.GLFW_KEY_HOME ? 0 : -Float.MAX_VALUE);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB010161C);
        g.fill(left - 1, top - 1, left + helper.xSize + 1, top + helper.ySize + 1, 0xFF4A6570);
        g.fill(left, top, left + helper.xSize, top + helper.ySize, 0xFF202C34);
        g.fill(left, top, left + helper.xSize, top + 2, 0xFF72CDB3);
        g.drawString(font, title, left + 10, top + 11, 0xFFF1F5F6, false);
        g.fill(left + 7, top + 55, left + helper.xSize - 7, top + helper.ySize - 21, 0xFF141E25);
        g.drawString(font, tr("Scroll to browse • Click an item for recipes"), left + 10, top + helper.ySize - 14, 0xFFB8C8CF, false);
        g.flush();
        Gui.begin(g);
        try {
            extender.imouseMove(mouseX, mouseY);
            extender.idraw(mouseX, mouseY, partialTick);
        } finally {
            Gui.end();
        }
        super.render(g, mouseX, mouseY, partialTick);
        Gui.begin(g);
        try { extender.idraw2(mouseX, mouseY); }
        finally { Gui.end(); }
    }
}
