package cn.muyang.xml;

import org.simpleframework.xml.Element;

import java.text.SimpleDateFormat;

public class Options {
    @Element(name = "stringObf", required = false)
    private String stringObf;

    @Element(name = "flowObf", required = false)
    private String flowObf;

    @Element(name = "expireDate", required = false)
    private String expireDate;

    public String getStringObf() {
        return stringObf;
    }

    public void setStringObf(String stringObf) {
        this.stringObf = stringObf;
    }

    public String getFlowObf() {
        return flowObf;
    }

    public void setFlowObf(String flowObf) {
        this.flowObf = flowObf;
    }

    public String getExpireDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            sdf.parse(expireDate);
            return expireDate;
        } catch (Exception e) {
            return null;
        }
    }

    public void setExpireDate(String expireDate) {
        this.expireDate = expireDate;
    }

}
