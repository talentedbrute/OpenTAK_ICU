package io.opentakserver.opentakicu.cot;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class TakProtocolSupport {
    private final int version;

    public TakProtocolSupport() {
        version = 0;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getVersion() { return Integer.toString(version); }

}
