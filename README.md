# SSMSUnlock
A tool for Starsector modding that enables reflection and file access for mods.

## Installation
Copy the unlock.jar into starsectors mods directory. Then edit the vmparams file in the root of the starsector installtion. This might require admin access. Look for the text "-classpath " without the quotes and add "..//mods//unlock.jar;" without the quotes behind it. If you are using any of the ".bat" files to launch the application you have to edit the classpath in there instead.

## Explanation
Adding the jar as the first entry in the classpath will instruct the game to load the unlock.jar first. Since the jar contains a class that matches the name of the script-classloader it will replace the script-classloader with one in which all restrictions are lifted.
