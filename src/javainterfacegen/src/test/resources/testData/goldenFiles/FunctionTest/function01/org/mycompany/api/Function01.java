/*
 * TODO handle license
 * Copyright (c) 2024, Petr and/or its affiliates
 * */

package org.mycompany.api;

import java.util.Optional;
import java.util.ServiceLoader;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.GuestValue;
import org.graalvm.python.javainterfacegen.Utils;


/**
 * TODO handle JavaDoc
 */
public interface Function01 extends GuestValue {

    // generated from src/test/resources/testData/testFiles/FunctionTest/function01.py

    interface Function01ImplProvider {
        Function01 createImplementation(Value value);
    }

    public static Function01 fromContext(Context context) {
        Value pythonBindings = context.getBindings(Utils.PYTHON);
        if (!pythonBindings.hasMember("function01_main")) {
            pythonBindings.putMember("function01_main", context.eval(Utils.PYTHON, "import function01"));
        }
        Value function01 = pythonBindings.getMember("function01_main").getMember("function01");
        Optional<Function01ImplProvider> findFirst = ServiceLoader.load(Function01ImplProvider.class).findFirst();
        if (findFirst.isEmpty()) {
            return new Function01Impl(function01);
        }
        return findFirst.get().createImplementation(function01);
    }

    public String hello ();


}
