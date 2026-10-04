package com.example;

/** A concrete specialization of the generic Box type. */
public class StringBox extends Box<String> {
    public StringBox() {
        super();
    }

    public StringBox(String value) {
        super(value);
    }
}
