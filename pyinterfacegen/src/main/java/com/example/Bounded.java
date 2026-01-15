package com.example;

/**
 * A generic type with an upper bound on the type variable.
 */
public class Bounded<T extends Number> {
    /** Identity function for values of type T. */
    public T id(T x) {
        return x;
    }
}

