package cn.muyang.xml;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

@Root(name = "myj2c")
public class Config {

    @ElementList(name = "targets")
    private List<String> targets;

    @Element(name = "options", required = false)
    private Options options;

    @ElementList(name = "include", type = Match.class, required = false)
    private List<Match> includes;

    @ElementList(name = "exclude", type = Match.class, required = false)
    private List<Match> excludes;



    public List<String> getTargets() {
        return targets;
    }

    public void setTargets(List<String> targets) {
        this.targets = targets;
    }

    public List<Match> getIncludes() {
        return includes;
    }

    public void setIncludes(List<Match> includes) {
        this.includes = includes;
    }

    public List<Match> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<Match> excludes) {
        this.excludes = excludes;
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }


}
