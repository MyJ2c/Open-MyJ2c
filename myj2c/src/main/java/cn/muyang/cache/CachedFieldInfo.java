package cn.muyang.cache;

import java.util.Objects;

public class CachedFieldInfo {

    private final String clazz;
    private final String name;
    private final String desc;
    private final boolean isStatic;
    private int id;

    public CachedFieldInfo(String clazz, String name, String desc, boolean isStatic) {
        this.clazz = clazz;
        this.name = name;
        this.desc = desc;
        this.isStatic = isStatic;
        this.id = -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedFieldInfo that = (CachedFieldInfo) o;
        return isStatic == that.isStatic &&
                clazz.equals(that.clazz) &&
                name.equals(that.name) &&
                desc.equals(that.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, name, desc, isStatic);
    }

    public String getClazz() {
        return clazz;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "CachedFieldInfo{" +
                "clazz='" + clazz + '\'' +
                ", name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", isStatic=" + isStatic +
                '}';
    }
}
