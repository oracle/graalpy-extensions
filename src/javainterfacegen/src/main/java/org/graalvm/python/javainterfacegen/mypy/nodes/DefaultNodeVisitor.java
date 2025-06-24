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
package org.graalvm.python.javainterfacegen.mypy.nodes;

public abstract class DefaultNodeVisitor<T> implements NodeVisitor<T>{

    abstract protected T defaultVisit(Node node);

    @Override
    public T visit(Decorator decorator) {
        return defaultVisit(decorator);
    }

    @Override
    public T visit(MypyFile file) {
        return defaultVisit(file);
    }

    @Override
    public T visit(ClassDef classDef) {
        return defaultVisit(classDef);
    }

    @Override
    public T visit(FuncDef funcDef) {
        return defaultVisit(funcDef);
    }

    @Override
    public T visit(Argument arg) {
        return defaultVisit(arg);
    }

    @Override
    public T visit(Var v) {
        return defaultVisit(v);
    }

    @Override
    public T visit(Block block) {
        return defaultVisit(block);
    }

    @Override
    public T visit(ExpressionStmt expr) {
        return defaultVisit(expr);
    }

    @Override
    public T visit(AssignmentStmt assignment) {
        return defaultVisit(assignment);
    }

    @Override
    public T visit(NameExpr nameExpr) {
        return defaultVisit(nameExpr);
    }

    @Override
    public T visit(OverloadedFuncDef oFnDef) {
        return defaultVisit(oFnDef);
    }

    @Override
    public T visit(StrExpr strExpr) {
        return defaultVisit(strExpr);
    }

    @Override
    public T visit(TupleExpr tuple) {
        return defaultVisit(tuple);
    }

    @Override
    public T visit(TypeAlias typeAlias) {
        return defaultVisit(typeAlias);
    }

    @Override
    public T visit(TypeInfo typeInfo) {
        return defaultVisit(typeInfo);
    }

    @Override
    public T visit(TypeVarExpr typeVarExpr) {
        return defaultVisit(typeVarExpr);
    }


}
