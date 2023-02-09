package cn.muyang.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CachedClassInfo {

    private final String clazz;
    private final String name;
    private final int id;
    private final String desc;
    private final boolean isStatic;

    private final List<CachedFieldInfo> cachedFields = new ArrayList<>();

    private final List<CachedMethodInfo> cachedMethods = new ArrayList<>();

    public CachedClassInfo(String clazz, String name, String desc, int id, boolean isStatic) {
        this.clazz = clazz;
        this.name = name;
        this.desc = desc;
        this.id = id;
        this.isStatic = isStatic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedClassInfo that = (CachedClassInfo) o;
        return isStatic == that.isStatic &&
                clazz.equals(that.clazz) &&
                name.equals(that.name) &&
                desc.equals(that.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, name, desc, isStatic);
    }


    public List<CachedFieldInfo> getCachedFields() {
        return cachedFields;
    }

    public List<CachedMethodInfo> getCachedMethods() {
        return cachedMethods;
    }

    public void addCachedField(CachedFieldInfo cachedFieldInfo) {
        boolean contains = false;
        for (CachedFieldInfo cachedField : cachedFields) {
            if (cachedField.equals(cachedFieldInfo)) {
                contains = true;
            }
        }
        if (!contains) {
            cachedFieldInfo.setId(cachedFields.size());
            cachedFields.add(cachedFieldInfo);
        }
    }

    public int getCachedFieldId(CachedFieldInfo cachedFieldInfo) {
        for (CachedFieldInfo cachedField : cachedFields) {
            if (cachedField.equals(cachedFieldInfo)) {
                return cachedField.getId();
            }
        }
        cachedFieldInfo.setId(cachedFields.size());
        cachedFields.add(cachedFieldInfo);
        return cachedFieldInfo.getId();
    }

    public int getCachedMethodId(CachedMethodInfo cachedMethodInfo) {
        for (CachedMethodInfo methodInfo : cachedMethods) {
            if (methodInfo.equals(cachedMethodInfo)) {
                return methodInfo.getId();
            }
        }
        cachedMethodInfo.setId(cachedMethods.size());
        cachedMethods.add(cachedMethodInfo);
        return cachedMethodInfo.getId();
    }

    public void addCachedMethod(CachedMethodInfo cachedMethodInfo) {
        boolean contains = false;
        for (CachedMethodInfo methodInfo : cachedMethods) {
            if (methodInfo.equals(cachedMethodInfo)) {
                contains = true;
            }
        }
        if (!contains) {
            cachedMethodInfo.setId(cachedMethods.size());
            cachedMethods.add(cachedMethodInfo);
        }
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "CachedClassInfo{" +
                "clazz='" + clazz + '\'' +
                ", name='" + name + '\'' +
                ", id=" + id +
                ", desc='" + desc + '\'' +
                ", isStatic=" + isStatic +
                ", cachedFields=" + cachedFields +
                ", cachedMethods=" + cachedMethods +
                '}';
    }
}
