# SSMSUnlock
A tool for Starsector modding that enables reflection, file access and new loading options for mods.

## Mod Loading

Mods can utilize new properties in mod_info.json to specify load order, dependencies, conflicts and support multiple game versions. If a list of enabled mods is invalid a popup will inform the user of the problem and the list is altered to avoid any conflict.

### loadAfter
An array of mod ids that need to be loaded before the mod if they are enabled. Unlike dependencies it is not an error if a mod id is missing in the list of enabled mods.

```
"loadAfter":["otherModsId"],
```

### loadBefore
An array of mod ids that need to be loaded after the mod if they are enabled. Unlike dependencies it is not an error if a mod id is missing in the list of enabled mods.

```
"loadBefore":["otherModsId"],
```

### dependencies
A array of versioned mod ids that need to be enabled alongside the mod. Specific versions can be used by defining the optional entries "min" and "max". They will filter the other mods version property and support only a format of numbers and dots. If min or max are missing the respective boundary is open ended.

```
"dependencies":[{"id":"otherModsId","min":"0.5.0","max":"1"}],
```

### conflicts
A array of versioned mod ids that can not be enabled alongside the mod. Specific versions can be used by defining the optional entries "min" and "max". They will filter the other mods version property and support only a format of numbers and dots. If min or max are missing the respective boundary is open ended.

```
"conflicts":[{"id":"otherModsId","min":"0.5.0","max":"1"}
```

### versions
All properties in the mod_info.json can be versioned. The propertiy holds an array of version block which specify a "gameVersionFilter". If the filter matches the games version all properties in the version block get copied into the root level of the json data replacing or adding new entries. The exclusion are "gameVersionFilter" and a possible "versions" property. The version filter may specify "min" and "max" if left out the respective boundary is open ended.

```
"versions":[{
	"gameVersionFilter": {"min":"0.9.0","max":"1.0.0"},
	"name": "Name for version 0.9 - 1.0",
	"conflicts":[{"id":"otherModsId"}]
}]
```

## Installation
Copy the unlock.jar into starsectors mods directory. Then edit the vmparams file in the root of the starsector installtion. This might require admin access. Look for the text "-classpath " without the quotes and add "..//mods//unlock.jar;" without the quotes behind it. If you are using any of the ".bat" files to launch the application you have to edit the classpath in there instead.

## Explanation
Adding the jar as the first entry in the classpath will instruct the game to load the unlock.jar first. Since the jar contains classes that matches the name of vanilla classes it will use the new classes over the original classes.
