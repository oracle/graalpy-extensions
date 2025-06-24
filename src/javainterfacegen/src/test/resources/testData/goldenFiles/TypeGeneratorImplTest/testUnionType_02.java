package org.test;

import java.util.Optional;
import org.mycompany.api.types.mypy.nodes.Decorator;
import org.mycompany.api.types.mypy.nodes.FuncDef;

/**
 * Generated from Python type: Union[mypy.nodes.FuncDef, None, mypy.nodes.Decorator]
 */
public class TestType {

    private final Optional<Decorator> decorator;
    private final Optional<FuncDef> funcDef;

    public TestType() {
        this.decorator = Optional.empty();
        this.funcDef = Optional.empty();
    }

    public TestType(Decorator decorator) {
        this.decorator = Optional.of(decorator);
        this.funcDef = Optional.empty();
    }

    public TestType(FuncDef funcDef) {
        this.funcDef = Optional.of(funcDef);
        this.decorator = Optional.empty();
    }

    public boolean isNone() {
        return decorator.isEmpty() && funcDef.isEmpty();
    }

    public boolean isDecorator() {
        return decorator.isPresent();
    }

    public boolean isFuncDef() {
        return funcDef.isPresent();
    }

    public Optional<Decorator> getDecorator() {
        return decorator;
    }

    public Optional<FuncDef> getFuncDef() {
        return funcDef;
    }

    @Override
    public String toString() {
        if(!decorator.isEmpty()) {
            return decorator.toString();
        }
        if(!funcDef.isEmpty()) {
            return funcDef.toString();
        }
        return "None";
    }


}
