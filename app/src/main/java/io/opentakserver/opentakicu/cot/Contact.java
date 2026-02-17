package io.opentakserver.opentakicu.cot;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Contact {
    private String callsign;
    private String endpoint;

    public Contact(String callsign) {
        this.callsign = callsign;
    }

    public Contact(String callsign, String endpoint) {
        this.callsign = callsign;
        this.endpoint = endpoint;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
