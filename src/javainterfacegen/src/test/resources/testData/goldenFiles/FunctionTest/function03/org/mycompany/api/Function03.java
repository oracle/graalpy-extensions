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
public interface Function03 extends GuestValue {

    // generated from src/test/resources/testData/testFiles/FunctionTest/function03.py

    interface Function03ImplProvider {
        Function03 createImplementation(Value value);
    }

    public static Function03 fromContext(Context context) {
        Value pythonBindings = context.getBindings(Utils.PYTHON);
        if (!pythonBindings.hasMember("function03_main")) {
            pythonBindings.putMember("function03_main", context.eval(Utils.PYTHON, "import function03"));
        }
        Value function03 = pythonBindings.getMember("function03_main").getMember("function03");
        Optional<Function03ImplProvider> findFirst = ServiceLoader.load(Function03ImplProvider.class).findFirst();
        if (findFirst.isEmpty()) {
            return new Function03Impl(function03);
        }
        return findFirst.get().createImplementation(function03);
    }

    public int cpuCount ();
    public int cpuCount (Value logical);


}
