package io.opentakserver.opentakicu.cot;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class TakControl {
    @JacksonXmlProperty(localName = "TakProtocolSupport")
    private final TakProtocolSupport takProtocolSupport;

    public TakControl(final TakProtocolSupport takProtocolSupport) {
        this.takProtocolSupport = takProtocolSupport;
    }

    public TakProtocolSupport getTakProtocolSupport() { return takProtocolSupport; }
}
