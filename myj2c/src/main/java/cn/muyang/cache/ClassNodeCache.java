package cn.muyang.cache;

import java.util.HashMap;
import java.util.Map;

public class ClassNodeCache {

    private final String pointerPattern;
    private final Map<String, CachedClassInfo> cache;

    public ClassNodeCache(String pointerPattern) {
        this.pointerPattern = pointerPattern;
        cache = new HashMap<>();
    }

    public String getPointer(String key) {
        return String.format(pointerPattern, getId(key));
    }

    public int getId(String clazz) {
        if (clazz.endsWith(";") && !clazz.startsWith("[")) {
            if (clazz.startsWith("L")) {
                clazz = clazz.substring(1);
            }
            clazz = clazz.replace(";", "");
        }
        if (clazz.startsWith("native/magic/1/linkcallsite/obfuscator")) {
            return 0;
        }
        if (!cache.containsKey(clazz)) {
            CachedClassInfo classInfo = new CachedClassInfo(clazz, clazz, "", cache.size(), false);
            //System.out.println(classInfo);
            cache.put(clazz, classInfo);
        }
        return cache.get(clazz).getId();
    }

    public CachedClassInfo getClass(String clazz) {
        if (clazz.endsWith(";") && !clazz.startsWith("[")) {
            if (clazz.startsWith("L")) {
                clazz = clazz.substring(1);
            }
            clazz = clazz.replace(";", "");
        }
        if (clazz.startsWith("native/magic/1/linkcallsite/obfuscator")) {
            return null;
        }
        if (!cache.containsKey(clazz)) {
            CachedClassInfo classInfo = new CachedClassInfo(clazz, clazz, "", cache.size(), false);
            //System.out.println(classInfo);
            cache.put(clazz, classInfo);
        }
        return cache.get(clazz);
    }

    public int size() {
        return cache.size();
    }

    public boolean isEmpty() {
        return cache.isEmpty();
    }

    public Map<String, CachedClassInfo> getCache() {
        return cache;
    }

    public void clear() {
        cache.clear();
    }
}
