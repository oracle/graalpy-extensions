package com.example;

import java.util.Locale;

/** A named greeting that overrides behavior while redundantly declaring an inherited interface. */
public class EnthusiasticHello extends NamedHello implements Nameable {
    public EnthusiasticHello(String name) {
        super(name);
    }

    @Override
    public String greet(String name) {
        return super.greet(name).toUpperCase(Locale.ROOT);
    }
}
