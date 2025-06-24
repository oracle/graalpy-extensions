package org.test;

import java.util.Map;
import java.util.Optional;
import org.graalvm.polyglot.Value;

/**
 * Generated from Python type: Union[builtins.dict[builtins.str, Any], builtins.str]
 */
public class TestType {

    private final Optional<Map<String, Value>> dictOfStrAny;
    private final Optional<String> str;

    public TestType(Map<String, Value> dictOfStrAny) {
        this.dictOfStrAny = Optional.of(dictOfStrAny);
        this.str = Optional.empty();
    }

    public TestType(String str) {
        this.str = Optional.of(str);
        this.dictOfStrAny = Optional.empty();
    }

    public boolean isDictOfStrAny() {
        return dictOfStrAny.isPresent();
    }

    public boolean isStr() {
        return str.isPresent();
    }

    public Optional<Map<String, Value>> getDictOfStrAny() {
        return dictOfStrAny;
    }

    public Optional<String> getStr() {
        return str;
    }

    @Override
    public String toString() {
        if(!dictOfStrAny.isEmpty()) {
            return dictOfStrAny.toString();
        }
        if(!str.isEmpty()) {
            return str.toString();
        }
        return "None";
    }


}
