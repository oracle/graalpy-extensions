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

package org.graalvm.python.javainterfacegen;

import java.util.List;
import java.util.Set;
import org.graalvm.python.javainterfacegen.mypy.nodes.*;
import org.graalvm.python.javainterfacegen.python.GuestArray;

public class PrintNodeVisitor implements NodeVisitor<String>{

    private int indent = 0;

    private String indentString() {
        String space = "";
        for (int i = 0; i < indent * 2; i++) {
            space += " ";
        }
        return space;
    }

    @Override
    public String visit(MypyFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("File ").append(file.getName());
        indent++;
        sb.append("\n").append(indentString()).append("Full Name: ").append(file.getFullname());
        sb.append("\n").append(indentString()).append("Path: ").append(file.getPath());
        GuestArray<Statement> defs = (GuestArray)file.getDefs();
        System.out.println("defs: " + file.getDefs().toString());
        System.out.println("defs.size: " + file.getDefs().size());
        System.out.println("defs[0]: " + file.getDefs().get(0));
        for (int i = 0; i < defs.size(); i++) {
            sb.append(defs.get(i).accept(this));
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visit(FuncDef funcDef) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Fn def: ").append(funcDef.getName());
        indent++;
        List<Argument> args = funcDef.getArguments();
        if (args.isEmpty()) {
            sb.append("\n").append(indentString()).append("No Arguments");
        } else {
            sb.append("\n").append(indentString()).append("Arguments");
            indent++;
            for(int i = 0; i < args.size(); i++) {
                sb.append(args.get(i).accept(this));
            }
            indent--;
        }

        indent--;
        return sb.toString();
    }

    @Override
    public String visit(Argument arg) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Arg: ").append(arg.getVariable().getName());
        indent++;
        sb.append(arg.getVariable().accept(this));
        sb.append("\n").append(indentString()).append("Type Annotation: ").append(arg.getTypeAnnotation().toString());
        sb.append("\n").append(indentString()).append("Initializer: ").append(arg.getInitializer().toString());
        sb.append("\n").append(indentString()).append("Kind: ").append(arg.getKind().toString());
        sb.append("\n").append(indentString()).append("Pos Only: ").append(arg.getPosOnly());
        indent--;
        return sb.toString();
    }


    @Override
    public String visit(Var v) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Var: ").append(v.getName());
        indent++;
        sb.append("\n").append(indentString()).append("Fullname: ").append(v.getFullname() != null ? v.getFullname().toString() : "");
        sb.append("\n").append(indentString()).append("Type: ").append(v.getType() != null ? v.getType().toString() : "");
        //sb.append("\n").append(indentString()).append("Info: ").append(v.getInfo() != null ? v.getInfo().toString() : "");
        sb.append("\n").append(indentString()).append("Flags: ");
        sb.append((v.isSelf() ? "self, " : ""));
        sb.append((v.isCls()? "cls, " : ""));
        sb.append((v.isReady()? "ready, " : ""));
        sb.append((v.isInferred()? "inferred, " : ""));
        sb.append((v.isInitializedInClass()? "initialized_in_class, " : ""));
        sb.append((v.isStaticMethod()? "staticmethod, " : ""));
        sb.append((v.isClassMethod()? "classmethod, " : ""));
        sb.append((v.isProperty()? "property, " : ""));
        sb.append((v.isSettableProperty()? "setttable_property, " : ""));
        sb.append((v.isClassVar()? "classvar, " : ""));
        sb.append((v.isAbstractVar()? "abstract_var, " : ""));
        sb.append((v.isSuppressedImport() ? "suppressed_import, " : ""));
        sb.append((v.hasExplicitValue() ? "has_explicit_value, " : ""));
        sb.append((v.allowIncompatibleOverride() ? "allow_incompatible_override, " : ""));
        sb.append((v.isFinalUnsetInClass() ? "final_unset_in_class, " : ""));
        sb.append((v.isFinalSetInInit() ? "final_set_in_init, " : ""));
        sb.append((v.isFromModuleGetAttr() ? "from_module_getattr, " : ""));
        sb.append((v.isExplicitSelfType()? "explicit_self_type, " : ""));
        sb.append((v.isInvalidPartialType()? "invalid_partial_type, " : ""));


        sb.append("\n").append(indentString()).append("FinalValue: ").append(v.getFinalValue().toString());

        indent--;
        return sb.toString();
    }

    @Override
    public String visit(ClassDef classDef) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Class: ").append(classDef.getName());
        indent++;
        sb.append(classDef.getDefs().accept(this));
        sb.append("\n").append(indentString()).append("BaseTypeExpression: ").append(classDef.getBaseTypeExprs().toString());
        sb.append("\n").append(indentString()).append("Analyzed: ").append(classDef.getAnalyzed().toString());
        sb.append("\n").append(indentString()).append("Decorators: ").append(classDef.getDecorators().toString());
        sb.append("\n").append(indentString()).append("Fullname: ").append(classDef.getFullname());
        sb.append("\n").append(indentString()).append("Info: ");
        indent++;
        sb.append(classDef.getInfo().accept(this));
        indent--;
        sb.append("\n").append(indentString()).append("Keywords: ").append(classDef.getKeywords().toString());
        sb.append("\n").append(indentString()).append("Metaclass: ").append(classDef.getMetaclass().toString());
        sb.append("\n").append(indentString()).append("RemovedBaseTypeExpression: ").append(classDef.getRemovedBaseTypeExprs().toString());
        sb.append("\n").append(indentString()).append("RemovedStatemetns: ").append(classDef.getRemovedStatements().toString());
        sb.append("\n").append(indentString()).append("TypeArgs: ").append(classDef.getTypeArgs() != null ? classDef.getTypeArgs().toString() : "type_args is not a memeber");
        sb.append("\n").append(indentString()).append("TypeVars: ").append(classDef.getTypeVars().toString());

        sb.append("\n").append(indentString()).append("Flags: ");
        sb.append((classDef.isGeneric()? "generic, " : ""));
        sb.append((classDef.hasIncompatibleBaseclass()? "hasIncompatibleBaseclass, " : ""));

        if (classDef.getDocstring() != null) {
            sb.append("\n").append(indentString()).append("===== DocString ===== ");
            sb.append("\n").append(classDef.getDocstring());
            sb.append("\n").append(indentString()).append("===== End of DocString ===== ");
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visit(Block block) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Block: ");
        indent++;
        sb.append("\n").append(indentString()).append("isUnreachable: ").append(block.isUnreachable());
        sb.append("\n").append(indentString()).append("Body: ");
        List<Statement> body = block.getBody();
        for(int i = 0; i < body.size(); i++) {
            sb.append(body.get(i).accept(this));
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visit(ExpressionStmt expr) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Expression: ");
        indent++;
        sb.append(expr.getExpr().accept(this));
        indent--;
        return sb.toString();
    }

    @Override
    public String visit(AssignmentStmt assignment) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Assignment: ");
        indent++;
        sb.append("\n").append(indentString()).append("Left Values: ");
        indent++;
        List<Expression> lValues = assignment.getLvalues();
        for(int i = 0; i < lValues.size(); i++) {
            sb.append(lValues.get(i).accept(this));
        }
        indent--;
        sb.append("\n").append(indentString()).append("Right Value: ");
        indent++;
        sb.append(assignment.getRvalue().accept(this));
        indent--;
        sb.append("\n").append(indentString()).append("Type: ").append(assignment.getType().toString());
        sb.append("\n").append(indentString()).append("Unanalyzed Type: ").append(assignment.getUnanalyzedType().toString());
        sb.append("\n").append(indentString()).append("Flags: ");
        sb.append((assignment.newSyntax()? "new_syntax, " : ""));
        sb.append((assignment.isAliasDef()? "alias_def, " : ""));
        sb.append((assignment.isFinalDef()? "final_def, " : ""));
        sb.append((assignment.invalidRecursiveAlias()? "invalid_recursive_alias, " : ""));
        indent--;
        return sb.toString();
    }


//    public String visit(RefExpr refExpr) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("\n").append(indentString()).append("Ref Expr: ");
//        indent++;
//        sb.append("\n").append(indentString()).append("Kind: ").append(refExpr.getKind());
//        sb.append("\n").append(indentString()).append("Full Name: ").append(refExpr.getFullName());
//        sb.append("\n").append(indentString()).append("Flags: ");
//        sb.append((refExpr.isNewDef()? "new_def, " : ""));
//        sb.append((refExpr.isInferredDef()? "inferred_def, " : ""));
//        sb.append((refExpr.isAliasRvalue()? "alias_rvalue, " : ""));
//        sb.append("\n").append(indentString()).append("Node: ").append(refExpr.getNode().toString());
//        indent--;
//        return sb.toString();
//    }

    @Override
    public String visit(NameExpr nameExpr) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Name Expr: ");
        indent++;
        sb.append("\n").append(indentString()).append("Name: ").append(nameExpr.getName());
        SymbolNode symbol = nameExpr.getNode();
        if (symbol != null) {
            sb.append(symbol.accept(this));
        }
        sb.append("\n").append(indentString()).append("Flags: ");
        sb.append((nameExpr.isSpecialForm()? "special_form, " : ""));
        sb.append((nameExpr.isNewDef()? "new_def, " : ""));
        sb.append((nameExpr.isInferredDef()? "inferred_def, " : ""));
        sb.append((nameExpr.isAliasRvalue()? "alias_rvalue, " : ""));
        indent--;
        return sb.toString();
    }

    @Override
    public String visit(StrExpr strExpr) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Str Expr: ");
        String text = strExpr.getText();
        String[] lines = text.split("\n");
        StringBuilder nonEmptyText = new StringBuilder();

        boolean foundNonEmptyLine = false;
        for (String line : lines) {
            if (!foundNonEmptyLine && line.trim().isEmpty()) {
                continue;
            }
            foundNonEmptyLine = true;
            nonEmptyText.append(line).append("\n");
        }

        String displayText = nonEmptyText.toString().trim();
        if (displayText.length() < text.length()) {
            displayText = displayText + "...";
        }
        displayText = displayText.replace("\n", "\\n");
        sb.append('"').append(displayText).append('"');

        return sb.toString();
    }

    @Override
    public String visit(TupleExpr tuple) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Tuple Expr: ");
        indent++;
        List<Expression> items = tuple.getItems();
        for(int i = 0; i < items.size(); i++) {
            sb.append(items.get(i).accept(this));
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visit(TypeInfo typeInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indentString()).append("Type Info: ");
        indent++;
        sb.append("\n").append(indentString()).append("name: ").append(typeInfo.getName());
        sb.append("\n").append(indentString()).append("fullname: ").append(typeInfo.getFullname());
        sb.append("\n").append(indentString()).append("module name: ").append(typeInfo.getModuleName());
        sb.append("\n").append(indentString()).append("ClassDef: ").append(typeInfo.getDefn().getFullname());
        sb.append("\n").append(indentString()).append("mro: ");
        List<TypeInfo> items = typeInfo.getMro();
        for(int i = 0; i < items.size(); i++) {
            sb.append(items.get(i).getFullname()).append(", ");
        }

        sb.append("\n").append(indentString()).append("Declared metaclass: ").append(typeInfo.getDeclardMetaclass().toString());
        sb.append("\n").append(indentString()).append("Metaclass type: ").append(typeInfo.getMetaClassType().toString());
        sb.append("\n").append(indentString()).append("Names: ").append(typeInfo.getNames().toString());
        sb.append("\n").append(indentString()).append("Abstract Attributes: ").append(typeInfo.getAbstractAttributes().toString());
        sb.append("\n").append(indentString()).append("Deletable Attributes: ").append(typeInfo.getDeletableAttributes().toString());

        sb.append("\n").append(indentString()).append("Slots: ");
        Set<String> slots = typeInfo.getSlots();
        for (String slot : slots) {
            sb.append(slot).append(", ");
        }

        sb.append("\n").append(indentString()).append("Assuming: ").append(typeInfo.getAssuming().toString());
        sb.append("\n").append(indentString()).append("Assuming proper: ").append(typeInfo.getAssumingProper().toString());
        sb.append("\n").append(indentString()).append("Inferring: ").append(typeInfo.getInferring().toString());

        sb.append("\n").append(indentString()).append("Type vars: ");
        List<String> vars = typeInfo.getTypeVars();
        for (String var : vars) {
            sb.append(var).append(", ");
        }

        sb.append("\n").append(indentString()).append("Bases: ").append(typeInfo.getBases().toString());
        sb.append("\n").append(indentString()).append("Promote: ").append(typeInfo.getPromote().toString());
        sb.append("\n").append(indentString()).append("Alt Promote: ").append(typeInfo.getAltPromote().toString());
        sb.append("\n").append(indentString()).append("Tuple Type: ").append(typeInfo.getTupleType().toString());
        sb.append("\n").append(indentString()).append("Typeddict Type: ").append(typeInfo.getTypeddictType().toString());
        sb.append("\n").append(indentString()).append("Metadata: ").append(typeInfo.getMetadata().toString());
        sb.append("\n").append(indentString()).append("Special alias: ").append(typeInfo.getSpecialAlias().toString());
        sb.append("\n").append(indentString()).append("Self type: ").append(typeInfo.getSelfType().toString());
        sb.append("\n").append(indentString()).append("dataclass trasformspec: ").append(typeInfo.getDataclassTransformSpec().toString());

        sb.append("\n").append(indentString()).append("Protocol members: ");
        List<String> members = typeInfo.getProtocolMembers();
        for (String member : members) {
            sb.append(member).append(", ");
        }

        sb.append("\n").append(indentString()).append("Direct base classes: ");
        List<TypeInfo> baseclasses = typeInfo.directBaseClasses();
        for(int i = 0; i < baseclasses.size(); i++) {
            sb.append(items.get(i).getFullname()).append(", ");
        }

        sb.append("\n").append(indentString()).append("Flags: ");
        sb.append((typeInfo.badMro()? "bad_mro, " : ""));
        sb.append((typeInfo.isFinal()? "final, " : ""));
        sb.append((typeInfo.isAbstract()? "abstract, " : ""));
        sb.append((typeInfo.isProtocol()? "protocol, " : ""));
        sb.append((typeInfo.runtimeProtocol()? "runtime protocol, " : ""));
        sb.append((typeInfo.isEnum()? "enum, " : ""));
        sb.append((typeInfo.fallbackToAny()? "fallback to any, " : ""));
        sb.append((typeInfo.metaFallbackToAny()? "met fallback to any, " : ""));
        sb.append((typeInfo.hasParamSpecType()? "has param spec type, " : ""));
        sb.append((typeInfo.isNamedTuple()? "named tuple, " : ""));
        sb.append((typeInfo.isNewType()? "newtype, " : ""));
        sb.append((typeInfo.isIntersection()? "intersection, " : ""));
        sb.append((typeInfo.isTypeCheckOnly()? "type check only, " : ""));
        sb.append((typeInfo.isGeneric() ? "generic, " : ""));
        sb.append((typeInfo.isMetaclass()? "metaclass, " : ""));



        indent--;
        return sb.toString();
    }

    @Override
    public String visit(Decorator decorator) {
        return "// TODO handle decorator\n";
    }

    @Override
    public String visit(OverloadedFuncDef oFnDef) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(TypeAlias typeAlias) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(TypeVarExpr typeVarExpr) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }


}
