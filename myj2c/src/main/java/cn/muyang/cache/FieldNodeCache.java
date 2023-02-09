package cn.muyang.cache;

import java.util.HashMap;
import java.util.Map;

public class FieldNodeCache {

    private final String pointerPattern;
    private final Map<CachedFieldInfo, Integer> cache;

    private ClassNodeCache classNodeCache;

    public FieldNodeCache(String pointerPattern, ClassNodeCache classNodeCache) {
        this.pointerPattern = pointerPattern;
        this.classNodeCache = classNodeCache;
        cache = new HashMap<>();
    }

    public String getPointer(CachedFieldInfo fieldInfo) {
        return String.format(pointerPattern, getId(fieldInfo));
    }

    public int getId(CachedFieldInfo fieldInfo) {
        if (!cache.containsKey(fieldInfo)) {
            //System.out.println("======" + fieldInfo);
            CachedClassInfo classInfo = classNodeCache.getClass(fieldInfo.getClazz());
            classInfo.addCachedField(fieldInfo);
            cache.put(fieldInfo, cache.size());
        }
        return cache.get(fieldInfo);
    }

    public int size() {
        return cache.size();
    }

    public boolean isEmpty() {
        return cache.isEmpty();
    }

    public Map<CachedFieldInfo, Integer> getCache() {
        return cache;
    }

    public void clear() {
        cache.clear();
    }
}
