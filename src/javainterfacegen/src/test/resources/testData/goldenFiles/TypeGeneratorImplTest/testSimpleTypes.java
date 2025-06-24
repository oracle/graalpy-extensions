package org.test;

import java.util.Optional;
import org.mycompany.api.types.Complex;

/**
 * Generated from Python type: Union[builtins.int, builtins.float, builtins.complex, builtins.bool, builtins.str, None]
 */
public class TestType {

    private final Optional<Boolean> bool;
    private final Optional<Complex> complex;
    private final Optional<Double> floatValue;
    private final Optional<Long> intValue;
    private final Optional<String> str;

    public TestType() {
        this.bool = Optional.empty();
        this.complex = Optional.empty();
        this.floatValue = Optional.empty();
        this.intValue = Optional.empty();
        this.str = Optional.empty();
    }

    public TestType(boolean bool) {
        this.bool = Optional.of(bool);
        this.complex = Optional.empty();
        this.floatValue = Optional.empty();
        this.intValue = Optional.empty();
        this.str = Optional.empty();
    }

    public TestType(Complex complex) {
        this.complex = Optional.of(complex);
        this.bool = Optional.empty();
        this.floatValue = Optional.empty();
        this.intValue = Optional.empty();
        this.str = Optional.empty();
    }

    public TestType(double floatValue) {
        this.floatValue = Optional.of(floatValue);
        this.bool = Optional.empty();
        this.complex = Optional.empty();
        this.intValue = Optional.empty();
        this.str = Optional.empty();
    }

    public TestType(long intValue) {
        this.intValue = Optional.of(intValue);
        this.bool = Optional.empty();
        this.complex = Optional.empty();
        this.floatValue = Optional.empty();
        this.str = Optional.empty();
    }

    public TestType(String str) {
        this.str = Optional.of(str);
        this.bool = Optional.empty();
        this.complex = Optional.empty();
        this.floatValue = Optional.empty();
        this.intValue = Optional.empty();
    }

    public boolean isNone() {
        return bool.isEmpty() && complex.isEmpty() && floatValue.isEmpty() && intValue.isEmpty() && str.isEmpty();
    }

    public boolean isBool() {
        return bool.isPresent();
    }

    public boolean isComplex() {
        return complex.isPresent();
    }

    public boolean isFloatValue() {
        return floatValue.isPresent();
    }

    public boolean isIntValue() {
        return intValue.isPresent();
    }

    public boolean isStr() {
        return str.isPresent();
    }

    public Optional<Boolean> getBool() {
        return bool;
    }

    public Optional<Complex> getComplex() {
        return complex;
    }

    public Optional<Double> getFloatValue() {
        return floatValue;
    }

    public Optional<Long> getIntValue() {
        return intValue;
    }

    public Optional<String> getStr() {
        return str;
    }

    @Override
    public String toString() {
        if(!bool.isEmpty()) {
            return bool.toString();
        }
        if(!complex.isEmpty()) {
            return complex.toString();
        }
        if(!floatValue.isEmpty()) {
            return floatValue.toString();
        }
        if(!intValue.isEmpty()) {
            return intValue.toString();
        }
        if(!str.isEmpty()) {
            return str.toString();
        }
        return "None";
    }


}
