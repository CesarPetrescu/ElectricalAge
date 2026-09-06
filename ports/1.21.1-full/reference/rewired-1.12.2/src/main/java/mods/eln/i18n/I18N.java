package mods.eln.i18n;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Internationalization and localization helper class.
 */
public class I18N {
    public static String getCurrentLanguage() {
        return FMLCommonHandler.instance().getCurrentLanguage();
    }

    static String encodeLangKey(final String key) {
        return encodeLangKey(key, true);
    }

    static String encodeLangKey(String key, boolean replaceWhitspaces) {
        if (key != null) {
            if (replaceWhitspaces) {
                key = key.replace(' ', '_');
            }
            return key.replace("/", "_");
        } else {
            return null;
        }
    }

    /**
     * Translates the given string. You can pass arguments to the method and reference them in the string using
     * the placeholders %N$ whereas N is the index of the actual parameter <b>starting at 1</b>.
     * <p>
     * Example: tr("You have %s lives left", 4);
     * <p>
     * IT IS IMPORTANT THAT YOU PASS THE <b>STRING LITERALS</b> AT LEAST ONCE AS THE FIRST PARAMETER TO THIS METHOD or
     * you call the method TR() with the actual string literal in order to register the translation text automatically!
     * Otherwise the translation will not be added to the language files. There is no problem to use the tr() method
     * afterwards using an already registered string in the code using a string variable as the first parameter.
     * <p/>
     *
     * @param text    Text to translate
     * @param objects Arguments to integrate into the text.
     * @return Translated text or original text (Argument placeholders are replaced by the actual arguments
     * anyway) if no translation is present.
     */
    public static String tr(final String text, Object... objects) {
        // Only client side has translations - server returns formatted original text
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            return trClient(text, objects);
        } else {
            // Server side: just format the original text
            return String.format(text, objects);
        }
    }

    @SideOnly(Side.CLIENT)
    private static String trClient(final String text, Object... objects) {
        String key = "eln." + encodeLangKey(text).toLowerCase();

        String translation;
        if (I18n.hasKey(key)) {
            translation = I18n.format(key, objects);
        } else {
            // No translation found, use original text with formatting
            translation = String.format(text, objects);
        }

        // Replace escaped characters
        translation = translation.replace("\\n", "\n").replace("\\:", ":");

        return translation;
    }

    /**
     * This method can be used to mark an unlocalized text in order to add it to the generated language files.
     * The method does not actually translate the text - it marks the text literal only to be translated afterwards.
     * A common use case is to add text to the language file which is translated using a text variable with the
     * method tr().
     *
     * @param text String LITERAL to add to the language files.
     * @return Exactly the same text as given to the method.
     */
    public static String TR(final String text) {
        return encodeLangKey(text);
    }

    /**
     * Defines the different translatable types.
     */
    public enum Type {
        /**
         * The text to translate is not related to a particular translatable type, so basically only the ".name" suffix
         * is added to the translation key.
         */
        NONE("", false, true),

        /**
         * The text to translate is related to an item. The "item." runtimePrefix will be added to the translation key.
         */
        ITEM("item.", false, false),

        /**
         * The text to translate is related to a tile. The "tile." runtimePrefix will be added to the translation key.
         */
        TILE("tile.", false, false),

        /**
         * The text to translate is related to an achievement. The "achievement." runtimePrefix will be added to the
         * translation key.
         */
        ACHIEVEMENT("achievement.", true, true),

        /**
         * The text to translate is related to an entity. The "entity." runtimePrefix will be added to the translation key.
         */
        ENTITY("entity.", false, false),

        /**
         * The text to translate is related to a death attack. The "death.attack" runtimePrefix will be added to the
         * translation key.
         */
        DEATH_ATTACK("death.attack.", false, false),

        /**
         * The text to translate is related to an item group. The "itemGroup." runtimePrefix will be added to the translation
         * key.
         */
        ITEM_GROUP("itemGroup.", false, false),

        /**
         * The text to translate is related to a container. The "container." runtimePrefix will be added to the translation
         * key.
         */
        CONTAINER("container.", false, false),

        /**
         * The text to translate is related to an block. The "block." runtimePrefix will be added to the translation key.
         */
        BLOCK("block.", false, false),

        SIX_NODE("eln.sixnode.", false, true),

        NODE("eln.node.", false, true);

        private final String prefix;
        private final boolean encodeAtRuntime;
        private final boolean replaceWhitespacesInFile;

        Type(final String prefix, boolean encodeAtRuntime, boolean replaceWhitespacesInFile) {
            this.prefix = prefix;
            this.encodeAtRuntime = encodeAtRuntime;
            this.replaceWhitespacesInFile = replaceWhitespacesInFile;
        }

        /**
         * Returns the prefix.
         *
         * @return Prefix for the type of translatable text.
         */
        public String getPrefix() {
            return prefix;
        }

        public boolean isEncodedAtRuntime() {
            return encodeAtRuntime;
        }

        public boolean isWhitespacesInFileReplaced() {
            return replaceWhitespacesInFile;
        }
    }

    /**
     * Used to register a name to translate. The forge mechanisms are used in order to translate the name.
     *
     * @param type Type the translatable name is related to.
     * @param text String LITERAL to register for translation.
     * @return Returns the same text literal, forge will translate the name magically.
     */
    public static String TR_NAME(final Type type, final String text) {
        if (type.isEncodedAtRuntime()) {
            return (new StringBuilder(type.getPrefix())).append(encodeLangKey(text)).append(".name").toString();
        } else {
            return text;
        }
    }

    /**
     * Used to register a description to translate. The forge mechanisms are used in order to translate the description.
     *
     * @param type Type the translatable description is related to.
     * @param text String LITERAL to register for translation.
     * @return Returns the same text literal, forge will translate the description magically.
     */
    public static String TR_DESC(final Type type, final String text) {
        if (type.isEncodedAtRuntime()) {
            return (new StringBuilder(type.getPrefix())).append(encodeLangKey(text)).append(".desc").toString();
        } else {
            return text;
        }
    }
}
