package com.fs.starfarer.loading.scripts;

/**
 *
 * @author Malte Schulze
 */
public class RenameMe extends ClassLoader {
    public RenameMe(ClassLoader paramClassLoader) {
        super(paramClassLoader);
    }

    @Override
    public Class<?> loadClass(String paramString, boolean paramBoolean) throws ClassNotFoundException {
        return super.loadClass(paramString, paramBoolean);
    }
}
