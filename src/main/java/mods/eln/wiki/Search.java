package mods.eln.wiki;

import net.minecraft.client.gui.screens.Screen;

/** The same scrollable catalogue, with focus retained while typing. */
public class Search extends Root {
    public Search(String text) { this(text, new Root(null)); }
    public Search(String text, Screen preview) {
        super(preview);
        initialQuery(text);
    }

    @Override
    public void initGui() {
        super.initGui();
        setInitialFocus(searchText);
        searchText.setCursorPosition(searchText.getValue().length());
    }
}
