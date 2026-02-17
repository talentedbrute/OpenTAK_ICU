package io.opentakserver.opentakicu.cot;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class __group {
    private String name;
    private String role;

    public __group(String name, String role) {
        this.name = name;
        this.role = role;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
