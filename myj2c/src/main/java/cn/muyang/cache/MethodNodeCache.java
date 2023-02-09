package cn.muyang.cache;

import java.util.HashMap;
import java.util.Map;

public class MethodNodeCache {

    private final String pointerPattern;
    private final Map<CachedMethodInfo, Integer> cache;
    private ClassNodeCache classNodeCache;

    public MethodNodeCache(String pointerPattern, ClassNodeCache classNodeCache) {
        this.pointerPattern = pointerPattern;
        this.classNodeCache = classNodeCache;
        cache = new HashMap<>();
    }

    public String getPointer(CachedMethodInfo methodInfo) {
        return String.format(pointerPattern, getId(methodInfo));
    }

    public int getId(CachedMethodInfo methodInfo) {
        if (!cache.containsKey(methodInfo)) {
            //System.out.println("======" + methodInfo);
            CachedClassInfo classInfo = classNodeCache.getClass(methodInfo.getClazz());
            classInfo.addCachedMethod(methodInfo);
            cache.put(methodInfo, cache.size());
        }
        return cache.get(methodInfo);
    }

    public int size() {
        return cache.size();
    }

    public boolean isEmpty() {
        return cache.isEmpty();
    }

    public Map<CachedMethodInfo, Integer> getCache() {
        return cache;
    }

    public void clear() {
        cache.clear();
    }
}
