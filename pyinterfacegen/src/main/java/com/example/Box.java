package com.example;

/**
 * A simple generic container.
 */
public class Box<T> {
    private T value;

    public Box() {}

    public Box(T value) {
        this.value = value;
    }

    /** Get the contained value. */
    public T get() {
        return value;
    }

    /** Set the contained value. */
    public void set(T v) {
        this.value = v;
    }
}

