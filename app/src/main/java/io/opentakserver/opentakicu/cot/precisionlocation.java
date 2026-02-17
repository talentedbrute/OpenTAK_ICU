package io.opentakserver.opentakicu.cot;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class precisionlocation {
    private String altsrc;
    private String geopointsrc;

    public precisionlocation(String altsrc, String geopointsrc) {
        this.altsrc = altsrc;
        this.geopointsrc = geopointsrc;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getAltsrc() {
        return altsrc;
    }

    public void setAltsrc(String altsrc) {
        this.altsrc = altsrc;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getGeopointsrc() {
        return geopointsrc;
    }

    public void setGeopointsrc(String geopointsrc) {
        this.geopointsrc = geopointsrc;
    }
}
