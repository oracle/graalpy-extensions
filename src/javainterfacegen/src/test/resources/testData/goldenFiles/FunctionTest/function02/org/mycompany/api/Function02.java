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
public interface Function02 extends GuestValue {

    // generated from src/test/resources/testData/testFiles/FunctionTest/function02.py

    interface Function02ImplProvider {
        Function02 createImplementation(Value value);
    }

    public static Function02 fromContext(Context context) {
        Value pythonBindings = context.getBindings(Utils.PYTHON);
        if (!pythonBindings.hasMember("function02_main")) {
            pythonBindings.putMember("function02_main", context.eval(Utils.PYTHON, "import function02"));
        }
        Value function02 = pythonBindings.getMember("function02_main").getMember("function02");
        Optional<Function02ImplProvider> findFirst = ServiceLoader.load(Function02ImplProvider.class).findFirst();
        if (findFirst.isEmpty()) {
            return new Function02Impl(function02);
        }
        return findFirst.get().createImplementation(function02);
    }

    public void func (String text, int count);


}
