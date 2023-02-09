package cn.muyang.special;

import cn.muyang.MethodContext;

public interface SpecialMethodProcessor {
    String preProcess(MethodContext context);

    void postProcess(MethodContext context);
}
