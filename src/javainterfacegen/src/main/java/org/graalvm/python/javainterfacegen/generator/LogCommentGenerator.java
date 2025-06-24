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
package org.graalvm.python.javainterfacegen.generator;

import java.util.List;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.python.javainterfacegen.mypy.nodes.Decorator;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.OverloadedFuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolNode;


public class LogCommentGenerator extends DefaultNodeVisitor<String>{

    private static final String FN_TEMPLATE = "{{indent}}// Python signature for function {{name}}: {{signature}}";
    private static final String Fn_PROPERTY_TEMPLATE = "{{indent}}// Python signature for function property {{name}}: {{signature}}";
    private static final String OVERLOAD_FUNCTIONS_TEMPLATE = "{{indent}}// Python signatures for overloaded function {{name}}:";
    private static final String OVERLOAD_FUNCTION_TEMPLATE  = "{{indent}}//     {{signature}}";

    private final GeneratorContext context;

    public static String getLogComment(GeneratorContext context, Node node) {
        LogCommentGenerator instance = new LogCommentGenerator(context);
        return node.accept(instance);
    }

    private LogCommentGenerator(GeneratorContext context) {
        this.context = context;
    }

    @Override
    protected String defaultVisit(Node node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String visit(FuncDef funcDef) {
        String signature;
        try {
            signature = funcDef.getType().toString();
        } catch (PolyglotException ex) {
            signature = "TODO the type was not possible to read";
        }
        String comment;
        if (context.getCurrentNode() instanceof OverloadedFuncDef) {
            comment = OVERLOAD_FUNCTION_TEMPLATE;
        } else {
            comment = funcDef.isProperty() ?
                    Fn_PROPERTY_TEMPLATE.replace("{{name}}", funcDef.getName()) :
                    FN_TEMPLATE.replace("{{name}}", funcDef.getName());
        }
        comment = comment.replace("{{signature}}", signature);
        return comment;
    }

    @Override
    public String visit(OverloadedFuncDef oFnDef) {
        StringBuilder sb = new StringBuilder();
        sb.append(OVERLOAD_FUNCTIONS_TEMPLATE.replace("{{name}}", oFnDef.name()));
        List<SymbolNode> items = oFnDef.items();
        for (int i = 0; i < items.size(); i++) {
            sb.append("\n");
            sb.append(items.get(i).accept(this));
        }
        return sb.toString();
    }

    @Override
    public String visit(Decorator decorator) {
        return decorator.getFunc().accept(this);
    }

}
