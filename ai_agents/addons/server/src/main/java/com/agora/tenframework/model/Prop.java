package com.agora.tenframework.model;

/**
 * Property Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Prop {
    private String extensionName;
    private String property;

    public Prop(String extensionName, String property) {
        this.extensionName = extensionName;
        this.property = property;
    }

    public Prop() {
    }

    // Getters and Setters
    public String getExtensionName() {
        return extensionName;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }
}