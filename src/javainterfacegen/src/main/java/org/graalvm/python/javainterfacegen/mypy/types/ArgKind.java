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
package org.graalvm.python.javainterfacegen.mypy.types;

import java.util.Optional;
import java.util.ServiceLoader;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.ArgKindItemImpl;
import org.graalvm.python.javainterfacegen.python.GuestValue;
import org.graalvm.python.javainterfacegen.python.Utils;


public enum ArgKind {
    ARG_POS,
    ARG_OPT,
    ARG_STAR,
    ARG_NAMED,
    ARG_STAR2,
    ARG_NAMED_OPT;

    public interface ArgKindItem extends GuestValue {

        boolean isPositional();

        boolean isPositional(boolean star);

        boolean isNamed();

        boolean isNamed(boolean star);

        boolean isRequired();

        boolean isOptional();

        boolean isStar();

    }

//    public interface ArgKindItemImplementationProvider {
//        ArgKindItem createInstance(Value value);
//    }

    private ArgKindItem item;

    ArgKind() {
        this.item = null;
    }

    public ArgKindItem getItem() {
       return item;
    }

    private void setItem(ArgKindItem item) {
        this.item = item;
    }

    public static final String FQN = "mypy.nodes.ArgKind";

    public static void initFromContext(Context context) {
//        Optional<ArgKindItemImplementationProvider> findFirst
//                = ServiceLoader.load(ArgKindItemImplementationProvider.class).findFirst();
        context.eval(Utils.PYTHON, "import mypy");
//        if (!findFirst.isEmpty()) {
//            ArgKind.ARG_POS.setItem(findFirst.get().createInstance(context.eval(Utils.PYTHON, FQN + ArgKind.ARG_POS.name())));
//            ArgKind.ARG_OPT.setItem(findFirst.get().createInstance(context.eval(Utils.PYTHON, FQN + ArgKind.ARG_OPT.name())));
//            ArgKind.ARG_STAR.setItem(findFirst.get().createInstance(context.eval(Utils.PYTHON, FQN + ArgKind.ARG_STAR.name())));
//            ArgKind.ARG_NAMED.setItem(findFirst.get().createInstance(context.eval(Utils.PYTHON, FQN + ArgKind.ARG_NAMED.name())));
//            ArgKind.ARG_STAR2.setItem(findFirst.get().createInstance(context.eval(Utils.PYTHON, FQN + ArgKind.ARG_STAR2.name())));
//            ArgKind.ARG_NAMED_OPT.setItem(findFirst.get().createInstance(context.eval(Utils.PYTHON, FQN + ArgKind.ARG_NAMED_OPT.name())));
//        } else {
            ArgKind.ARG_POS.setItem(new ArgKindItemImpl(context.eval(Utils.PYTHON, FQN + "." + ArgKind.ARG_POS.name())));
            ArgKind.ARG_OPT.setItem(new ArgKindItemImpl(context.eval(Utils.PYTHON, FQN + "." + ArgKind.ARG_OPT.name())));
            ArgKind.ARG_STAR.setItem(new ArgKindItemImpl(context.eval(Utils.PYTHON, FQN + "." + ArgKind.ARG_STAR.name())));
            ArgKind.ARG_NAMED.setItem(new ArgKindItemImpl(context.eval(Utils.PYTHON, FQN + "." + ArgKind.ARG_NAMED.name())));
            ArgKind.ARG_STAR2.setItem(new ArgKindItemImpl(context.eval(Utils.PYTHON, FQN + "." + ArgKind.ARG_STAR2.name())));
            ArgKind.ARG_NAMED_OPT.setItem(new ArgKindItemImpl(context.eval(Utils.PYTHON, FQN + "." + ArgKind.ARG_NAMED_OPT.name())));
//        }
    }


}
