package cn.muyang.xml;


import org.simpleframework.xml.Attribute;

public class Match {

    @Attribute(name = "className", required=false)
    private String className;

    @Attribute(name = "methodName",required=false)
    private String methodName;

    @Attribute(name = "methodDesc",required=false)
    private String methodDesc;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public void setMethodDesc(String methodDesc) {
        this.methodDesc = methodDesc;
    }
}
