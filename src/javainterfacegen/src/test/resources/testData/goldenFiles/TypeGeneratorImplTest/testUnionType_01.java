package org.test;

import java.util.Optional;
import org.mycompany.api.types.mypy.nodes.Decorator;
import org.mycompany.api.types.mypy.nodes.FuncDef;
import org.mycompany.api.types.mypy.nodes.Var;

/**
 * Generated from Python type: Union[mypy.nodes.FuncDef, mypy.nodes.Var, mypy.nodes.Decorator]
 */
public class TestType {

    private final Optional<Decorator> decorator;
    private final Optional<FuncDef> funcDef;
    private final Optional<Var> var;

    public TestType(Decorator decorator) {
        this.decorator = Optional.of(decorator);
        this.funcDef = Optional.empty();
        this.var = Optional.empty();
    }

    public TestType(FuncDef funcDef) {
        this.funcDef = Optional.of(funcDef);
        this.decorator = Optional.empty();
        this.var = Optional.empty();
    }

    public TestType(Var var) {
        this.var = Optional.of(var);
        this.decorator = Optional.empty();
        this.funcDef = Optional.empty();
    }

    public boolean isDecorator() {
        return decorator.isPresent();
    }

    public boolean isFuncDef() {
        return funcDef.isPresent();
    }

    public boolean isVar() {
        return var.isPresent();
    }

    public Optional<Decorator> getDecorator() {
        return decorator;
    }

    public Optional<FuncDef> getFuncDef() {
        return funcDef;
    }

    public Optional<Var> getVar() {
        return var;
    }

    @Override
    public String toString() {
        if(!decorator.isEmpty()) {
            return decorator.toString();
        }
        if(!funcDef.isEmpty()) {
            return funcDef.toString();
        }
        if(!var.isEmpty()) {
            return var.toString();
        }
        return "None";
    }


}
