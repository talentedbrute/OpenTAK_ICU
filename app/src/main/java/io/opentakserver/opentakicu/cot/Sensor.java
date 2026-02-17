package io.opentakserver.opentakicu.cot;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Sensor {
    private double fov = 65.0;
    private double vfov = 40.0;
    private double azimuth = 180;
    private double elevation = 0;
    private double range = 100;
    private double north;
    private double displayMagneticReference = 0;
    private double fovRed = 1.0;
    private double fovGreen = 1.0;
    private double fovBlue = 1.0;
    private double fovAlpha = 0.3;
    private String model;
    private String type;

    public Sensor(double fov, double vfov, double azimuth, double range) {
        this.fov = fov;
        this.vfov = vfov;
        this.azimuth = azimuth;
        this.range = range;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getFov() {
        return fov;
    }

    public void setFov(double fov) {
        this.fov = fov;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getVfov() {
        return vfov;
    }

    public void setVfov(double vfov) {
        this.vfov = vfov;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getAzimuth() {
        return azimuth;
    }

    public void setAzimuth(double azimuth) {
        this.azimuth = azimuth;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getElevation() {
        return elevation;
    }

    public void setElevation(double elevation) {
        this.elevation = elevation;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        this.range = range;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getNorth() {
        return north;
    }

    public void setNorth(double north) {
        this.north = north;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getDisplayMagneticReference() {
        return displayMagneticReference;
    }

    public void setDisplayMagneticReference(double displayMagneticReference) {
        this.displayMagneticReference = displayMagneticReference;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getFovRed() {
        return fovRed;
    }

    public void setFovRed(double fovRed) {
        this.fovRed = fovRed;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getFovGreen() {
        return fovGreen;
    }

    public void setFovGreen(double fovGreen) {
        this.fovGreen = fovGreen;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getFovBlue() {
        return fovBlue;
    }

    public void setFovBlue(double fovBlue) {
        this.fovBlue = fovBlue;
    }

    @JacksonXmlProperty(isAttribute = true)
    public double getFovAlpha() {
        return fovAlpha;
    }

    public void setFovAlpha(double fovAlpha) {
        this.fovAlpha = fovAlpha;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @JacksonXmlProperty(isAttribute = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
