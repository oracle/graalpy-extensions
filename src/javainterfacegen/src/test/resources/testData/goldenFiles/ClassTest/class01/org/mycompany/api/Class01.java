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
public interface Class01 extends GuestValue {

    // generated from src/test/resources/testData/testFiles/ClassTest/class01.py

    interface Class01ImplProvider {
        Class01 createImplementation(Value value);
    }

    public static Class01 fromContext(Context context) {
        Value pythonBindings = context.getBindings(Utils.PYTHON);
        if (!pythonBindings.hasMember("class01_main")) {
            pythonBindings.putMember("class01_main", context.eval(Utils.PYTHON, "import class01"));
        }
        Value class01 = pythonBindings.getMember("class01_main").getMember("class01");
        Optional<Class01ImplProvider> findFirst = ServiceLoader.load(Class01ImplProvider.class).findFirst();
        if (findFirst.isEmpty()) {
            return new Class01Impl(class01);
        }
        return findFirst.get().createImplementation(class01);
    }


}
