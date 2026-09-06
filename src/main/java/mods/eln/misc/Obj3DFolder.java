package mods.eln.misc;

import mods.eln.misc.Obj3D.Obj3DPart;

import java.io.File;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.List;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility class used to load all eln models and corresponding obj files.
 */
public class Obj3DFolder {

    private Map<String, Obj3D> nameToObjHash = new HashMap<String, Obj3D>();
    private final Obj3D missingObj = new Obj3D();
    private final Set<String> missingObjNamesWarned = new HashSet<String>();

    /**
     * Load all obj models available in the mod's assets. The mod file is walked through FML's
     * mod-file resource root (a jar, a dev classpath directory or the JUnit union filesystem
     * alike); the 1.7.10 code-source/jar walk only knew the first two.
     */
    public void loadAllElnModels() {
        Path modelRoot = null;
        try {
            net.neoforged.fml.ModList modList = net.neoforged.fml.ModList.get();
            if (modList != null && modList.getModFileById(mods.eln.Eln.MODID) != null) {
                modelRoot = modList.getModFileById(mods.eln.Eln.MODID).getFile().findResource("assets", "eln", "model");
            }
        } catch (Throwable ignored) {
            // outside a mod-loading context (unit tests without FML): fall through to the classpath
        }
        if (modelRoot == null || !Files.isDirectory(modelRoot)) {
            try {
                java.net.URL url = mods.eln.Eln.class.getResource("/assets/eln/model");
                if (url != null) modelRoot = Paths.get(url.toURI());
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        if (modelRoot == null || !Files.isDirectory(modelRoot)) return;
        try (Stream<Path> files = Files.walk(modelRoot)) {
            List<Path> objs = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obj")).sorted().collect(Collectors.toList());
            int modelCount = 0;
            for (Path obj : objs) {
                String filename = modelRoot.relativize(obj).toString().replace('\\', '/');
                Utils.println(String.format("Loading model %03d '%s'", ++modelCount, filename));
                loadObj(filename);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static File codeSourceLocationToFile(String locationString) throws URISyntaxException {
        String uriString = locationString;
        if (uriString.startsWith("jar:")) {
            int bangIndex = uriString.indexOf("!");
            if (bangIndex >= 0) {
                uriString = uriString.substring(0, bangIndex);
            }
            uriString = uriString.substring(4);
        }
        return new File(new URI(uriString));
    }

    private void loadModelsRecursive(File folder, Integer modelCount) {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                loadModelsRecursive(file, modelCount);
            } else if (file.getName().toLowerCase().endsWith(".obj")) {
                String filename = file.getPath().replaceAll("\\\\", "/");
                filename = filename.substring(filename.indexOf("/model/") + 7, filename.length());
                Utils.println(String.format("Loading model %03d '%s'", ++modelCount, filename));
                loadObj(filename);
            }
        }
    }

    /**
     * Load an obj file of a model.
     *
     * @param modelPath path inside model folder (ex. Vumeter/Vumeter.obj)
     */
    private void loadObj(String modelPath) {
        Obj3D obj = new Obj3D();
        if (obj.loadFile(modelPath)) {
            String tag = modelPath.replaceAll(".obj", "").replaceAll(".OBJ", "");
            tag = tag.substring(tag.lastIndexOf('/') + 1, tag.length());
            if (nameToObjHash.containsKey(tag.toLowerCase(Locale.ROOT))) {
                Utils.println("Double load of model " + tag);
            }
            // Keyed lowercase: Minecraft 1.11+ lowercases every ResourceLocation path, so
            // the .obj files on disk are lowercase while getObj() callers still pass the
            // original mixed-case model names (e.g. "BatteryBig").
            nameToObjHash.put(tag.toLowerCase(Locale.ROOT), obj);
            Utils.println(String.format(" - model '%s' loaded", modelPath));
        } else {
            Utils.println(String.format(" - unable to load model '%s'", modelPath));
        }
    }

    public Obj3D getObj(String obj3DName) {
        Obj3D obj = nameToObjHash.get(obj3DName == null ? null : obj3DName.toLowerCase(Locale.ROOT));
        if (obj == null) {
            if (missingObjNamesWarned.add(obj3DName)) {
                Utils.println(String.format("Missing model '%s', using fallback empty model", obj3DName));
            }
            return missingObj;
        }
        return obj;
    }

    public Obj3DPart getPart(String objName, String partName) {
        Obj3D obj = getObj(objName);
        if (obj == null) return null;
        return obj.getPart(partName);
    }

    public void draw(String objName, String partName) {
        Obj3DPart part = getPart(objName, partName);
        if (part != null) part.draw();
    }

    public Set<String> getObjectList() {
        return nameToObjHash.keySet();
    }
}
