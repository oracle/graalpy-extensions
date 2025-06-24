/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.python.javainterfacegen.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class Utils {
    public static String PYTHON = "python";

    private static String HELP_FN_ARRAYS_KWARGS = "__help_fn_arrays_and_kwargs";
    private static String HELP_FN_KWARGS = "__help_fn_kwargs";


    public static Value getHelpFnArraysKWArgs(Context context) {

        Value bindings = context.getBindings(PYTHON);
        if (!bindings.hasMember(HELP_FN_ARRAYS_KWARGS)) {

            context.eval(PYTHON, "def " + HELP_FN_ARRAYS_KWARGS + " (fn, arrays, kwargs):\n" +
                                    "    return fn(*arrays, **kwargs)");
        }
        return bindings.getMember(HELP_FN_ARRAYS_KWARGS);
    }

    public static Value getHelpFnKWArgs(Context context) {

        Value bindings = context.getBindings(PYTHON);
        if (!bindings.hasMember(HELP_FN_KWARGS)) {
            context.eval(PYTHON, "def " + HELP_FN_KWARGS + " (fn, kwargs):\n" +
                                    "    return fn(**kwargs)");
        }
        return bindings.getMember(HELP_FN_KWARGS);
    }

    public static String getFullyQualifedName(Value instance) {
        Context context = instance.getContext();
        Value bindings = context.getBindings(PYTHON);
        if (!bindings.hasMember("utils_main")) {
            bindings.putMember("utils_main", context.eval(Utils.PYTHON, "import utils"));
        }
        return bindings.getMember("utils").invokeMember("getFQN", instance).asString();
    }

    public static String typeOf(Context context, Value instance) {
        return context.getBindings("python").getMember("type").newInstance(instance).toString();
    }

    public static String dir(Value instance) {
        return instance.getContext().getBindings("python").getMember("dir").execute(instance).toString();
    }

}
