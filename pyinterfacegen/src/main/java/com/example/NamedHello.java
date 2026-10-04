package com.example;

/** A greeting object that inherits its greeting behavior and also exposes a name. */
public class NamedHello extends Hello implements Nameable {
    private final String name;

    public NamedHello(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }
}
