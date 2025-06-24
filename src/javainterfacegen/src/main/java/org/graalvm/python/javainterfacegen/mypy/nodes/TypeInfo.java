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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.InstanceImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypesFactory;
import org.graalvm.python.javainterfacegen.python.GuestArray;
import org.graalvm.python.javainterfacegen.python.Utils;

public interface TypeInfo extends SymbolNode {

    public static final String FQN = "mypy.nodes.TypeInfo";

    static class TypeInfoImpl extends SymbolNode.SymbolNodeImpl implements TypeInfo {

        public TypeInfoImpl(Value instance) {
            super(instance);
//            String instanceFQN = Utils.getFullyQualifedName(instance);
//            if (!FQN.equals(instanceFQN)) {
//                throw new UnsupportedOperationException("Can not create new TypeInfoImpl from Guest instance " + instanceFQN);
//            }
        }

        @Override
        public <T> T accept(NodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String getModuleName() {
            return getValue().getMember("module_name").asString();
        }

        @Override
        public ClassDef getDefn() {
            return new ClassDef.ClassDefImpl(getValue().getMember("defn"));
        }

        @Override
        public List<TypeInfo> getMro() {
            Value original = getValue().getMember("mro");
            GuestArray<TypeInfo> result = new GuestArray<>(original, (value) -> {
                String pythonFQN = Utils.getFullyQualifedName(value);
                switch (pythonFQN) {
                    case TypeInfo.FQN:
                        return new TypeInfo.TypeInfoImpl(value);
                }
                throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
            });
            return result;
        }

        @Override
        public List<String> getMroRefs() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public boolean badMro() {
            return getValue().getMember("bad_mro").asBoolean();
        }

        @Override
        public boolean isFinal() {
            return getValue().getMember("bad_mro").asBoolean();
        }

        @Override
        public Value getDeclardMetaclass() {
            return getValue().getMember("declared_metaclass");
        }

        @Override
        public Value getMetaClassType() {
            return getValue().getMember("metaclass_type");
        }

        @Override
        public SymbolTable getNames() {
            Value table = getValue().getMember("names");

            String pythonFQN = Utils.getFullyQualifedName(table);
            switch (pythonFQN) {
                case SymbolTable.FQN:
                    return new SymbolTable.SymbolTableImpl(table);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        }

        @Override
        public boolean isAbstract() {
            return getValue().getMember("is_abstract").asBoolean();
        }

        @Override
        public boolean isProtocol() {
            return getValue().getMember("is_protocol").asBoolean();
        }

        @Override
        public boolean runtimeProtocol() {
            return getValue().getMember("runtime_protocol").asBoolean();
        }

        @Override
        public Value getAbstractAttributes() {
            return getValue().getMember("abstract_attributes");
        }

        @Override
        public List<String> getDeletableAttributes() {
            Value original = getValue().getMember("deletable_attributes");
            List<String> result = new ArrayList();
            for (int i = 0; i < original.getArraySize(); i++) {
                result.add(original.getArrayElement(i).asString());
            }
            return result;
        }

        @Override
        public Set<String> getSlots() {
            Value original = getValue().getMember("slots");
            Value iterator = original.getIterator();
            Set<String> result = new HashSet<>();
            while (iterator.hasIteratorNextElement()) {
                result.add(iterator.getIteratorNextElement().asString());
            }

            return result;
        }

        @Override
        public Value getAssuming() {
            return getValue().getMember("assuming");
        }

        @Override
        public Value getAssumingProper() {
            return getValue().getMember("assuming_proper");
        }

        @Override
        public Value getInferring() {
            return getValue().getMember("inferring");
        }

        @Override
        public boolean isEnum() {
            return getValue().getMember("is_enum").asBoolean();
        }

        @Override
        public boolean fallbackToAny() {
            return getValue().getMember("fallback_to_any").asBoolean();
        }

        @Override
        public boolean metaFallbackToAny() {
            return getValue().getMember("meta_fallback_to_any").asBoolean();
        }

        @Override
        public List<String> getTypeVars() {
            Value original = getValue().getMember("type_vars");
            List<String> result = new ArrayList();
            for (int i = 0; i < original.getArraySize(); i++) {
                result.add(original.getArrayElement(i).asString());
            }
            return result;
        }

        @Override
        public boolean hasParamSpecType() {
            return getValue().getMember("has_param_spec_type").asBoolean();
        }

        @Override
        public List<Instance> getBases() {
            Value original = getValue().getMember("bases");
            GuestArray<Instance> result = new GuestArray<>(original, (value) -> {
                String pythonFQN = Utils.getFullyQualifedName(value);
                switch (pythonFQN) {
                    case Instance.FQN:
                        return new InstanceImpl(value);
                }
                throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
            });
            return result;
        }

        @Override
        public Value getPromote() {
            return getValue().getMember("_promote");
        }

        @Override
        public Value getAltPromote() {
            return getValue().getMember("alt_promote");
        }

        @Override
        public Type getTupleType() {
            Value value = getValue().getMember("tuple_type");
            return TypesFactory.createType(value);
        }

        @Override
        public boolean isNamedTuple() {
            return getValue().getMember("is_named_tuple").asBoolean();
        }

        @Override
        public Value getTypeddictType() {
            return getValue().getMember("tuple_type");
        }

        @Override
        public boolean isNewType() {
            return getValue().getMember("is_newtype").asBoolean();
        }

        @Override
        public boolean isIntersection() {
            return getValue().getMember("is_intersection").asBoolean();
        }

        @Override
        public Value getMetadata() {
            return getValue().getMember("metadata");
        }

        @Override
        public Value getSpecialAlias() {
            return getValue().getMember("special_alias");
        }

        @Override
        public Value getSelfType() {
            return getValue().getMember("self_type");
        }

        @Override
        public Value getDataclassTransformSpec() {
            return getValue().getMember("dataclass_transform_spec");
        }

        @Override
        public boolean isTypeCheckOnly() {
            return getValue().getMember("is_type_check_only").asBoolean();
        }

        @Override
        public String getName() {
            return getValue().getMember("name").asString();
        }

        @Override
        public String getFullname() {
            return getValue().getMember("fullname").asString();
        }

        @Override
        public boolean isGeneric() {
            return getValue().invokeMember("is_generic").asBoolean();
        }

        @Override
        public Value get(String name) {
            return getValue().invokeMember("get", name);
        }

        @Override
        public TypeInfo getContainingTypeInfo(String name) {
            return new TypeInfo.TypeInfoImpl(getValue().invokeMember("get_containing_type_info", name));
        }

        @Override
        public List<String> getProtocolMembers() {
            Value original = getValue().getMember("protocol_members");
            List<String> result = new ArrayList();
            for (int i = 0; i < original.getArraySize(); i++) {
                result.add(original.getArrayElement(i).asString());
            }
            return result;
        }

        @Override
        public boolean hasReadableMember(String name) {
            return getValue().invokeMember("has_readable_member").asBoolean();
        }

        @Override
        public Value getMethod(String name) {
            return getValue().invokeMember("get_method", name);
        }

        @Override
        public Value calculateMetaclassType() {
            return getValue().invokeMember("calculate_metaclass_type");
        }

        @Override
        public boolean isMetaclass() {
            return getValue().invokeMember("is_metaclass").asBoolean();
        }

        @Override
        public boolean hasBase(String fullname) {
            return getValue().invokeMember("has_base", fullname).asBoolean();
        }

        @Override
        public List<TypeInfo> directBaseClasses() {
            Value original = getValue().invokeMember("direct_base_classes");
            GuestArray<TypeInfo> result = new GuestArray<>(original, (value) -> {
                String pythonFQN = Utils.getFullyQualifedName(value);
                switch (pythonFQN) {
                    case TypeInfo.FQN:
                        return new TypeInfo.TypeInfoImpl(value);
                }
                throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
            });
            return result;
        }

        @Override
        public void updateTypleType(Value typ) {
            getValue().invokeMember("update_tuple_type", typ);
        }

        @Override
        public void updateTypeddictType(Value typ) {
            getValue().invokeMember("update_typeddict_type", typ);
        }

    }

    String getModuleName();

    ClassDef getDefn();

    List<TypeInfo> getMro();

    List<String> getMroRefs();

    boolean badMro();

    boolean isFinal();

    Value getDeclardMetaclass();

    Value getMetaClassType();

    SymbolTable getNames();

    boolean isAbstract();

    boolean isProtocol();

    boolean runtimeProtocol();

    Value getAbstractAttributes();

    List<String> getDeletableAttributes();

    Set<String> getSlots();

    Value getAssuming();

    Value getAssumingProper();

    Value getInferring();

    boolean isEnum();

    boolean fallbackToAny();

    boolean metaFallbackToAny();

    List<String> getTypeVars();

    boolean hasParamSpecType();

    List<Instance> getBases();

    Value getPromote();

    Value getAltPromote();

    Type getTupleType();

    boolean isNamedTuple();

    Value getTypeddictType();

    boolean isNewType();

    boolean isIntersection();

    Value getMetadata();

    Value getSpecialAlias();

    Value getSelfType();

    Value getDataclassTransformSpec();

    boolean isTypeCheckOnly();

    String getName();

    String getFullname();

    boolean isGeneric();

    Value get(String name);

    TypeInfo getContainingTypeInfo(String name);

    List<String> getProtocolMembers();

    boolean hasReadableMember(String name);

    Value getMethod(String name);

    Value calculateMetaclassType();

    boolean isMetaclass();

    boolean hasBase(String fullname);

    List<TypeInfo> directBaseClasses();

    void updateTypleType(Value typ);

    void updateTypeddictType(Value typ);
}
