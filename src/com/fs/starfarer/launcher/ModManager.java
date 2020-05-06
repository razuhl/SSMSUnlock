/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fs.starfarer.launcher;

import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModPlugin;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.DroneLauncherShipSystemAPI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.campaign.fleet.FleetMember;
import com.fs.starfarer.combat.ai.BasicShipAI;
import com.fs.starfarer.combat.ai.FighterAI;
import com.fs.starfarer.combat.ai.L;
import com.fs.starfarer.combat.ai.P;
import com.fs.starfarer.combat.ai.system.drones.DroneAI;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.combat.systems.N;
import com.fs.starfarer.combat.systems.R;
import com.fs.starfarer.loading.LoadingUtils;
import com.fs.starfarer.loading.scripts.ScriptStore;
import com.fs.starfarer.settings.StarfarerSettings;
import com.fs.util.DoNotObfuscate;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.Sys;

/**
 *
 * @author Malte Schulze
 */
public class ModManager implements ModManagerAPI {

    private static final Logger \u00D400000 = Logger.getLogger(ModManager.class);

    private static volatile ModManager o00000;

    private List<ModSpec> Object = new ArrayList<>();

    private List<ModSpec> \u00D500000 = new ArrayList<>();

    public static String MODS_KEY = "enabledMods";

    private List<ModPlugin> modPlugins = null;

    public static synchronized ModManager getInstance() {
        if (o00000 == null) {
            o00000 = new ModManager();
        }
        return o00000;
    }

    protected ModManager() {
        this.Object.clear();
        updateList();
    }

    public synchronized void updateList() {
        String str = StarfarerSettings.\u00D3\u00D80000();
        File file = new File(str);
        if (!file.exists()) {
            file.mkdirs();
        }
        if (!file.isDirectory()) {
            throw new RuntimeException(String.format("Mod location [%s] is not a directory", new java.lang.Object[]{file.getAbsolutePath()}));
        }
        file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File param1File) {
                if (!param1File.isDirectory()) {
                    return false;
                }
                File file = new File(param1File, "mod_info.json");
                if (!file.exists()) {
                    return false;
                }
                try {
                    ModManager.ModSpec modSpec;
                    try {
                        modSpec = ModManager.this.o00000(param1File, file);
                    } catch (Exception exception) {
                        ModManager.\u00D400000.info(String.format("Error loading mod from [%s]", new java.lang.Object[]{param1File}));
                        ModManager.\u00D400000.info(exception.getMessage(), exception);
                        return false;
                    }
                    if (modSpec.getGameVersion() == null) {
                        return false;
                    }
                    boolean bool = false;
                    for (ModManager.ModSpec modSpec1 : ModManager.this.Object) {
                        if (modSpec1.getId() != null && modSpec1.getId().equals(modSpec.getId())) {
                            bool = true;
                        }
                    }
                    if (!bool) {
                        ModManager.this.Object.add(modSpec);
                        ModManager.\u00D400000.info(String.format("Found mod: %s [%s]", new java.lang.Object[]{modSpec.getId(), param1File.getAbsolutePath()}));
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(String.format("Error loading mod descriptor [%s]", new java.lang.Object[]{file.getAbsolutePath()}), exception);
                }
                return true;
            }
        });
        Collections.sort(this.Object, new Comparator<ModSpec>() {
            public int compare(ModManager.ModSpec param1ModSpec1, ModManager.ModSpec param1ModSpec2) {
                return param1ModSpec1.getName().compareTo(param1ModSpec2.getName());
            }
        });
        
        loadEnabledModList();
        
        List<String> messages = new ArrayList<>();
        //ensure that dependencies and conflicts are valid in the enabled mod list.
        checkForConflicts(this.\u00D500000, messages);
        //order enabled mods based on loadBefore and loadAfter
        sortMods(this.\u00D500000, messages);
        displayMessages(messages);
        if ( !messages.isEmpty() ) {
            saveEnabledModList();
        }
    }
    
    protected void displayMessages(List<String> messages) {
        if ( !messages.isEmpty() ) {
            StringBuilder sb = new StringBuilder();
            for ( String msg  : messages ) {
                sb.append(msg).append("\n");
            }
            Sys.alert("Mod List Problem", sb.toString());
        }
    }
    
    protected void sortMods(List<ModSpec> modSpecs, List<String> messages) {
        Map<String,HashSet<String>> loadAfter = new HashMap<>();
        for ( ModSpec spec : modSpecs ) {
            HashSet<String> ids = loadAfter.get(spec.getId());
            if ( ids == null ) {
                ids = new HashSet<>();
                loadAfter.put(spec.getId(), ids);
            }
            ids.addAll(spec.getLoadAfter());
            for ( String otherModId : spec.getLoadBefore() ) {
                ids = loadAfter.get(otherModId);
                if ( ids == null ) {
                    ids = new HashSet<>();
                    loadAfter.put(otherModId, ids);
                }
                ids.add(spec.getId());
            }
        }
        
        //add them one by one testing from the front to the back if an already added mod has the new mods id in loadafter. If so we insert before that mod.
        //We continue checking for conflicting order instructions. Like C after A and A after C. C after B, B after A and A after C.
        List<ModSpec> newOrder = new ArrayList<>(modSpecs.size());
        outer : for ( ModSpec spec : modSpecs ) {
            int insertIndex = -1;
            for ( int i = 0; i < newOrder.size(); i++ ) {
                if ( insertIndex == -1 ) {
                    if ( loadAfter.get(newOrder.get(i).getId()).contains(spec.getId()) ) {
                        if ( loadAfter.get(spec.getId()).contains(newOrder.get(i).getId()) ) {
                            String msg = "Unable to insert enabled mod "+spec.getName()+" into load order. Mods "+newOrder.get(i).getName()+" and "+spec.getName()+" both want to be loaded after each other.";
                            messages.add(msg);
                            \u00D400000.error(msg);
                            insertIndex = -2;
                            break;
                        } else insertIndex = i;
                    }
                } else {
                    if ( loadAfter.get(newOrder.get(i).getId()).contains(spec.getId()) ) {
                        String msg = "Unable to insert enabled mod "+spec.getName()+" into load order without creating an invalid ordering with mods "+newOrder.get(insertIndex).getName()+" and "+newOrder.get(i).getName();
                        messages.add(msg);
                        \u00D400000.error(msg);
                        insertIndex = -2;
                        break;
                    }
                }
            }
            if ( insertIndex == -1 ) {
                newOrder.add(spec);
            } else if ( insertIndex >= 0 ) {
                newOrder.add(insertIndex, spec);
            }
        }
        
        modSpecs.clear();
        modSpecs.addAll(newOrder);
    }
    
    protected void checkForConflicts(List<ModSpec> modSpecs, List<String> messages) {
        HashMap<String,ModSpec> existingIds = new HashMap<>(modSpecs.size());
        for ( ModSpec spec : modSpecs ) {
            existingIds.put(spec.getId(), spec);
        }
        //we remove all mods from front to back that have a conflict with another mod in the list or a dependency that is not present in the list.
        int messagesBefore = 0, messagesAfter = -1;
        while ( messagesBefore != messagesAfter ) {
            messagesBefore = messages.size();
            Iterator<ModSpec> it = modSpecs.iterator();
            outer : while ( it.hasNext() ) {
                ModSpec spec = it.next();
                for ( VersionedId conflictingMod : spec.getConflicts() ) {
                    if ( existingIds.containsKey(conflictingMod.getId()) ) {
                        ModSpec mod = existingIds.get(conflictingMod.getId());
                        if ( checkVersionFilter(mod.getVersion(), conflictingMod.getMinModVersion(), conflictingMod.getMaxModVersion()) ) {
                            it.remove();
                            String msg = "Unable to enabled mod "+spec.getName()+" since a conflicting mod "+mod.getName()+" is also enabled.";
                            messages.add(msg);
                            \u00D400000.error(msg);
                            existingIds.remove(spec.getId());
                            continue outer;
                        }
                    }
                }
                for ( VersionedId dependentMod : spec.getDependencies() ) {
                    if ( !existingIds.containsKey(dependentMod.getId()) ) {
                        it.remove();
                        String msg = "Unable to enabled mod "+spec.getName()+" since a dependent mod with id "+dependentMod.getId()+" is not enabled.";
                        messages.add(msg);
                        \u00D400000.error(msg);
                        existingIds.remove(spec.getId());
                        continue outer;
                    } else {
                        ModSpec mod = existingIds.get(dependentMod.getId());
                        if ( !checkVersionFilter(mod.getVersion(), dependentMod.getMinModVersion(), dependentMod.getMaxModVersion()) ) {
                            it.remove();
                            String msg = "Unable to enabled mod "+spec.getName()+" due to version mismatch on dependency for mod "+mod.getName()+" and version restriction "+
                                dependentMod.getMinModVersion()+" - "+dependentMod.getMaxModVersion()+".";
                            messages.add(msg);
                            \u00D400000.error(msg);
                            existingIds.remove(spec.getId());
                            continue outer;
                        }
                    }
                }
            }
            messagesAfter = messages.size();
        }
    }

    private synchronized ModSpec o00000(File paramFile1, File paramFile2) throws IOException, JSONException {
        FileInputStream fileInputStream = new FileInputStream(paramFile2);
        JSONObject jSONObject;
        try {
            jSONObject = (JSONObject) LoadingUtils.class.getMethod("class", String.class, String.class).invoke(null, paramFile2.getAbsolutePath(), LoadingUtils.o00000(fileInputStream));
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            \u00D400000.error("Failed to invoke LoadingUtils method class!", ex);
            return null;
        }
        
        //Versioning start
        versioning(jSONObject);
        //Versioning stop
        
        ModSpec modSpec = new ModSpec();
        modSpec.setPath(paramFile1.getAbsolutePath());
        modSpec.setDirName(paramFile1.getName());
        modSpec.setDesc(jSONObject.getString("description"));
        modSpec.setId(jSONObject.getString("id"));
        modSpec.setName(jSONObject.getString("name"));
        modSpec.setVersion(jSONObject.getString("version"));
        modSpec.setGameVersion(jSONObject.optString("gameVersion", null));
        modSpec.setModPluginClassName(jSONObject.optString("modPlugin", null));
        modSpec.setAuthor(jSONObject.optString("author", null));
        JSONArray jSONArray1 = jSONObject.optJSONArray("replace");
        if (jSONArray1 != null) {
            for (byte b = 0; b < jSONArray1.length(); b++) {
                String str = jSONArray1.getString(b);
                if (!str.endsWith("settings.json")) {
                    modSpec.getFullOverrides().add(str);
                }
            }
        }
        JSONArray jSONArray2 = jSONObject.optJSONArray("jars");
        if (jSONArray2 != null) {
            for (byte b = 0; b < jSONArray2.length(); b++) {
                String str = jSONArray2.getString(b);
                modSpec.getJars().add(str);
            }
        }
        modSpec.setTotalConversion(jSONObject.optBoolean("totalConversion", false));
        modSpec.setUtility(jSONObject.optBoolean("utility", false));
        
        //Additional properties start
        modSpec.getLoadAfter().addAll(toStringList(jSONObject.optJSONArray("loadAfter")));
        modSpec.getLoadBefore().addAll(toStringList(jSONObject.optJSONArray("loadBefore")));
        modSpec.getDependencies().addAll(toVersionedIdList(jSONObject.optJSONArray("dependencies")));
        modSpec.getConflicts().addAll(toVersionedIdList(jSONObject.optJSONArray("conflicts")));
        //Additional properties end
        
        return modSpec;
    }
    
    protected void versioning(JSONObject json) {
        String versionString = com.fs.starfarer.Object.\u00D200000;
        //0.9.1a-RC8
        Matcher m = Pattern.compile("[0-9.]*").matcher(versionString);
        if ( m.find() )
            versionString = m.group();
        //0.9.1
        String[] gameVersionStrings = versionString.split("\\.");
        Integer[] gameVersion = new Integer[gameVersionStrings.length];
        for ( int i = 0; i < gameVersion.length; i++ ) {
            gameVersion[i] = Integer.parseInt(gameVersionStrings[i]);
        }
        
        JSONArray versions = json.optJSONArray("versions");
        if ( versions != null ) {
            for ( int i = 0; i < versions.length(); i++ ) {
                try {
                    JSONObject block = versions.getJSONObject(i);
                    JSONObject filter = block.getJSONObject("gameVersionFilter");
                    if ( checkVersionFilter(gameVersion, filter.optString("min",null), filter.optString("max",null)) ) {
                        //copy all other entries, ignoring a potential versions and the version filter entry, from the block into the root overwriting existing entries.
                        Iterator it = block.keys();
                        while ( it.hasNext() ) {
                            String key = (String) it.next();
                            if ( key == null || key.equals("versions") || key.equals("gameVersionFilter") ) continue;
                            json.put(key, block.get(key));
                        }
                    }
                } catch (JSONException ex) {
                    \u00D400000.error("Failed to process versions block in mod! \n"+versions, ex);
                }
            }
        }
    }
    
    protected boolean checkVersionFilter(String versionString, String minVersion, String maxVersion) {
        if ( minVersion == null && maxVersion == null ) return true;
        String[] versionStrings = versionString.split("\\.");
        Integer[] version = new Integer[versionStrings.length];
        for ( int i = 0; i < version.length; i++ ) {
            version[i] = Integer.parseInt(versionStrings[i]);
        }
        return checkVersionFilter(version,minVersion,maxVersion);
    }
    
    protected boolean checkVersionFilter(Integer[] gameVersion, String minVersion, String maxVersion) {
        return compareVersionNumber(gameVersion,minVersion) >= 0 && compareVersionNumber(gameVersion, maxVersion) <= 0;
    }
    
    protected int compareVersionNumber(Integer[] gameVersion, String version) {
        if ( version == null ) return 0;
        String[] arrVersion = version.split("\\.");

        int i=0;
        while(i<gameVersion.length || i<arrVersion.length){
            if(i<gameVersion.length && i<arrVersion.length){
                if(gameVersion[i] < Integer.parseInt(arrVersion[i])){
                    return -1;
                }else if(gameVersion[i] > Integer.parseInt(arrVersion[i])){
                    return 1;
                }
            } else if(i<gameVersion.length){
                if(gameVersion[i] != 0){
                    return 1;
                }
            } else if(i<arrVersion.length){
               if(Integer.parseInt(arrVersion[i]) != 0){
                    return -1;
                }
            }

            i++;
        }
        
        return 0;
    }
    
    protected List<String> toStringList(JSONArray arr) {        
        if ( arr == null ) return Collections.EMPTY_LIST;
        List<String> lst = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            try {
                lst.add(arr.getString(i));
            } catch (JSONException ex) {
                \u00D400000.error("Failed to load string entry from JSON.", ex);
            }
        }
        return Collections.unmodifiableList(lst);
    }
    
    protected List<VersionedId> toVersionedIdList(JSONArray arr) {
        if ( arr == null ) return Collections.EMPTY_LIST;
        List<VersionedId> lst = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                lst.add(new VersionedId(obj.getString("id"), obj.optString("min",null), obj.optString("max",null)));
            } catch (JSONException ex) {
                \u00D400000.error("Failed to load string entry from JSON.", ex);
            }
        }
        return Collections.unmodifiableList(lst);
    }

    public synchronized List<ModSpec> getAvailableMods() {
        return this.Object;
    }

    public synchronized List<ModSpec> getEnabledMods() {
        return this.\u00D500000;
    }

    @Override
    public synchronized List<ModSpecAPI> getAvailableModsCopy() {
        return new ArrayList<>((Collection) this.Object);
    }

    @Override
    public synchronized List<ModSpecAPI> getEnabledModsCopy() {
        return new ArrayList<>((Collection) this.\u00D500000);
    }

    @Override
    public synchronized boolean isModEnabled(String paramString) {
        for (ModSpec modSpec : getEnabledMods()) {
            if (modSpec.getId().equals(paramString)) {
                return true;
            }
        }
        return false;
    }

    private String o00000() {
        return String.valueOf(StarfarerSettings.\u00D3\u00D80000()) + "/" + "enabled_mods.json";
    }

    public synchronized void saveEnabledModList() {
        //Before saving the mod list we validate it. This happens if fthe launcher modifies the list via checkbox actions.
        List<String> messages = new ArrayList<>();
        checkForConflicts(this.\u00D500000, messages);
        sortMods(this.\u00D500000, messages);
        displayMessages(messages);
        
        String str = "";
        for (ModSpec modSpec : this.\u00D500000) {
            str = String.valueOf(str) + modSpec.getId() + "|";
        }
        if (str.length() > 0) {
            str = str.substring(0, str.length() - 1);
        }
        \u00D400000.info("Saving enabled mod list [" + str + "]");
        try {
            JSONObject jSONObject = new JSONObject();
            JSONArray jSONArray = new JSONArray();
            for (ModSpec modSpec : this.\u00D500000) {
                jSONArray.put(modSpec.getId());
            }
            jSONObject.put(MODS_KEY, jSONArray);
            String str1 = jSONObject.toString(2);
            try (FileOutputStream fileOutputStream = new FileOutputStream(o00000())) {
                fileOutputStream.write(str1.getBytes("UTF-8"));
                fileOutputStream.flush();
            }
        } catch (Exception exception) {
            \u00D400000.error("Error saving enabled mod list", exception);
        }
    }

    public synchronized void loadEnabledModList() {
        String str = "";
        try {
            File file = new File(o00000());
            if (file.exists()) {
                JSONObject jSONObject = LoadingUtils.\u00D800000(o00000());
                if (jSONObject.has(MODS_KEY)) {
                    JSONArray jSONArray = jSONObject.getJSONArray(MODS_KEY);
                    for (byte b = 0; b < jSONArray.length(); b++) {
                        String str1 = jSONArray.getString(b);
                        str = String.valueOf(str) + str1 + "|";
                    }
                    if (!str.isEmpty()) {
                        str = str.substring(0, str.length() - 1);
                    }
                }
            }
        } catch (Exception exception) {
            \u00D400000.error("Error loading enabled mod list", exception);
        }
        if (str == null) {
            return;
        }
        String[] arrayOfString = str.split("\\|");
        this.\u00D500000.clear();
        for (ModSpec modSpec : this.Object) {
            String[] arrayOfString1;
            int i = (arrayOfString1 = arrayOfString).length;
            for (byte b = 0; b < i; b++) {
                String str1 = arrayOfString1[b];
                if (modSpec.getId() != null && modSpec.getId().equals(str1)) {
                    this.\u00D500000.add(modSpec);
                    break;
                }
            }
        }
    }

    @Override
    public synchronized List<ModPlugin> getEnabledModPlugins() {
        if (this.modPlugins == null) {
            ArrayList<ModPlugin> arrayList = new ArrayList();
            arrayList.add((ModPlugin) StarfarerSettings.OO0000("coreLifecyclePlugin"));
            for (ModSpec modSpec : getEnabledMods()) {
                if (modSpec.getModPluginClassName() == null) {
                    continue;
                }
                ModPlugin modPlugin = (ModPlugin) ScriptStore.\u00D300000(modSpec.getModPluginClassName());
                arrayList.add(modPlugin);
            }
            this.modPlugins = arrayList;
        }
        return this.modPlugins;
    }

    public AutofireAIPlugin pickWeaponAIPlugin(final WeaponAPI paramWeaponAPI) {
        AutofireAIPlugin autofireAIPlugin = getPriorityPlugin(new o<AutofireAIPlugin>() {
            @Override
            public PluginPick<AutofireAIPlugin> o00000(ModPlugin param1ModPlugin) {
                return param1ModPlugin.pickWeaponAutofireAI(paramWeaponAPI);
            }
        });
        return (AutofireAIPlugin) ((autofireAIPlugin == null) ? new P((R) paramWeaponAPI) : autofireAIPlugin);
    }

    public ShipAIPlugin pickShipAIPlugin(final FleetMember paramFleetMember, final ShipAPI paramShipAPI) {
        ShipAIPlugin shipAIPlugin = getPriorityPlugin(new o<ShipAIPlugin>() {
            @Override
            public PluginPick<ShipAIPlugin> o00000(ModPlugin param1ModPlugin) {
                return param1ModPlugin.pickShipAI((FleetMemberAPI) paramFleetMember, paramShipAPI);
            }
        });
        return (ShipAIPlugin) ((shipAIPlugin == null) ? (paramShipAPI.isFighter() ? new FighterAI((Ship) paramShipAPI, (L) paramShipAPI.getWing()) : new BasicShipAI((Ship) paramShipAPI)) : shipAIPlugin);
    }

    public ShipAIPlugin pickDroneAIPlugin(final ShipAPI paramShipAPI1, final ShipAPI paramShipAPI2, final DroneLauncherShipSystemAPI paramDroneLauncherShipSystemAPI) {
        ShipAIPlugin shipAIPlugin = getPriorityPlugin(new o<ShipAIPlugin>() {
            @Override
            public PluginPick<ShipAIPlugin> o00000(ModPlugin param1ModPlugin) {
                //TODO verify which parameter is drone and which one is mothership
                return param1ModPlugin.pickDroneAI(paramShipAPI1, paramShipAPI2, paramDroneLauncherShipSystemAPI);
            }
        });
        return (ShipAIPlugin) ((shipAIPlugin == null) ? new DroneAI((Ship) paramShipAPI1, (Ship) paramShipAPI2, (N) paramDroneLauncherShipSystemAPI) : shipAIPlugin);
    }

    public MissileAIPlugin pickMissileAIOverride(final MissileAPI paramMissileAPI, final ShipAPI paramShipAPI) {
        return getPriorityPlugin(new o<MissileAIPlugin>() {
            @Override
            public PluginPick<MissileAIPlugin> o00000(ModPlugin param1ModPlugin) {
                return param1ModPlugin.pickMissileAI(paramMissileAPI, paramShipAPI);
            }
        });
    }

    public <T> T getPriorityPlugin(o<T> paramo) {
        ArrayList<PluginPick<T>> arrayList = new ArrayList();
        for (ModPlugin modPlugin : getEnabledModPlugins()) {
            PluginPick<T> pluginPick = paramo.o00000(modPlugin);
            if (pluginPick != null) {
                arrayList.add(pluginPick);
            }
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        Collections.sort(arrayList, new Comparator<PluginPick<T>>() {
            @Override
            public int compare(PluginPick<T> param1PluginPick1, PluginPick<T> param1PluginPick2) {
                return param1PluginPick1.priority.ordinal() - param1PluginPick2.priority.ordinal();
            }
        });
        return (T) ((PluginPick) arrayList.get(arrayList.size() - 1)).plugin;
    }

    @Override
    public synchronized ModSpec getModSpec(String paramString) {
        for (ModSpec modSpec : getEnabledMods()) {
            if (modSpec.getId().equals(paramString)) {
                return modSpec;
            }
        }
        return null;
    }

    public static interface o<T> {

        PluginPick<T> o00000(ModPlugin param1ModPlugin);
    }

    public static class VersionedId {
        private final String id, minModVersion, maxModVersion;

        public VersionedId(String id, String minModVersion, String maxModVersion) {
            this.id = id;
            this.minModVersion = minModVersion;
            this.maxModVersion = maxModVersion;
        }

        public String getId() {
            return id;
        }

        public String getMinModVersion() {
            return minModVersion;
        }

        public String getMaxModVersion() {
            return maxModVersion;
        }
    }
    
    public static class ModSpec implements DoNotObfuscate, ModSpecAPI {

        private String id;

        private String version;

        private String desc;

        private String name;

        private String gameVersion;

        private String author;

        private String path;

        private String dirName;

        private boolean totalConversion;

        private boolean utility;

        private String modPluginClassName;

        private Set<String> fullOverrides = new HashSet<>();

        private List<String> jars = new ArrayList<>();
        
        //custom properties declaration start
        private List<String> loadAfter = new ArrayList<>(), loadBefore = new ArrayList<>();
        private List<VersionedId> dependencies = new ArrayList<>(), conflicts = new ArrayList<>();
        //custom properties declaration stop

        @Override
        public boolean isUtility() {
            return this.utility;
        }

        public void setUtility(boolean param1Boolean) {
            this.utility = param1Boolean;
        }

        @Override
        public String getModPluginClassName() {
            return this.modPluginClassName;
        }

        public void setModPluginClassName(String param1String) {
            this.modPluginClassName = param1String;
        }

        @Override
        public boolean isTotalConversion() {
            return this.totalConversion;
        }

        public void setTotalConversion(boolean param1Boolean) {
            this.totalConversion = param1Boolean;
        }

        @Override
        public String getName() {
            return this.name;
        }

        public void setName(String param1String) {
            this.name = param1String;
        }

        @Override
        public String getId() {
            return this.id;
        }

        public void setId(String param1String) {
            this.id = param1String;
        }

        @Override
        public String getVersion() {
            return this.version;
        }

        public void setVersion(String param1String) {
            this.version = param1String;
        }

        @Override
        public String getDesc() {
            String str = this.desc;
            if (this.totalConversion) {
                str = String.valueOf(str) + "\n\nTotal conversion.";
            }
            if (this.author != null) {
                str = String.valueOf(str) + "\n\n" + this.author;
            }
            return str;
        }

        public void setDesc(String param1String) {
            this.desc = param1String;
        }

        @Override
        public String getPath() {
            return this.path;
        }

        public void setPath(String param1String) {
            this.path = param1String;
        }

        @Override
        public String getDirName() {
            return this.dirName;
        }

        public void setDirName(String param1String) {
            this.dirName = param1String;
        }

        @Override
        public String getGameVersion() {
            return this.gameVersion;
        }

        public void setGameVersion(String param1String) {
            this.gameVersion = param1String;
        }

        @Override
        public String toString() {
            return isTotalConversion() ? (String.valueOf(getName()) + " " + getVersion() + " (total conversion)") : (String.valueOf(getName()) + " " + getVersion());
        }

        @Override
        public Set<String> getFullOverrides() {
            return this.fullOverrides;
        }

        @Override
        public List<String> getJars() {
            return this.jars;
        }

        @Override
        public String getAuthor() {
            return this.author;
        }

        public void setAuthor(String param1String) {
            this.author = param1String;
        }
        
        //custom properties start
        public List<String> getLoadAfter() {
            return loadAfter;
        }

        public void setLoadAfter(List<String> loadAfter) {
            this.loadAfter = loadAfter;
        }

        public List<String> getLoadBefore() {
            return loadBefore;
        }

        public void setLoadBefore(List<String> loadBefore) {
            this.loadBefore = loadBefore;
        }

        public List<VersionedId> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<VersionedId> dependencies) {
            this.dependencies = dependencies;
        }

        public List<VersionedId> getConflicts() {
            return conflicts;
        }

        public void setConflicts(List<VersionedId> conflicts) {
            this.conflicts = conflicts;
        }
        //custom properties stop
    }
}
