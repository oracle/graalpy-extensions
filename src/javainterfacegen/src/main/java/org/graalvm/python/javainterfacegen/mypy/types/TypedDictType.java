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

// Genereted at 2024-09-26 10:22:39
// Generated from ../../git/mypy/mypy/types.py class def: TypedDictType

import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Value;

// TODO these are Java types, that replaced unresolved Python types

import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Value;

import java.util.Set;


/**
 * Type of TypedDict object {'k1': v1, ..., 'kn': vn}.
 *
 * A TypedDict object is a dictionary with specific string (literal) keys. Each
 * key has a value with a distinct type that depends on the key. TypedDict objects
 * are normal dict objects at runtime.
 *
 * A TypedDictType can be either named or anonymous. If it's anonymous, its
 * fallback will be typing_extensions._TypedDict (Instance). _TypedDict is a subclass
 * of Mapping[str, object] and defines all non-mapping dict methods that TypedDict
 * supports. Some dict methods are unsafe and not supported. _TypedDict isn't defined
 * at runtime.
 *
 * If a TypedDict is named, its fallback will be an Instance of the named type
 * (ex: "Point") whose TypeInfo has a typeddict_type that is anonymous. This
 * is similar to how named tuples work.
 *
 * TODO: The fallback structure is perhaps overly complicated.
 */
public interface TypedDictType extends ProperType {

    static final String FQN =  "mypy.types.TypedDictType";
    // Python def: TypedDictType(mypy.types.ProperType)

    // Python signature: create_anonymous_fallback def (self: mypy.types.TypedDictType) -> mypy.types.Instance
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.create_anonymous_fallback
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public Instance createAnonymousFallback();



    // Python signature: as_anonymous def (self: mypy.types.TypedDictType) -> mypy.types.TypedDictType
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.as_anonymous
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public TypedDictType asAnonymous();

    // getter for class field 'required_keys', Python type: builtins.set[builtins.str]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.required_keys
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public Set<String> getRequiredKeys();

    // Python signature: zipall def (self: mypy.types.TypedDictType, right: mypy.types.TypedDictType) -> typing.Iterable[tuple[builtins.str, Union[mypy.types.Type, None], Union[mypy.types.Type, None]]]
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.zipall
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
//    public Value zipall();

    // Python signature: accept def [T] (self: mypy.types.TypedDictType, visitor: mypy.type_visitor.TypeVisitor[T`-1]) -> T`-1
    // type class of return type: mypy.types.TypeVarType
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.accept
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
//    public <T> T accept(TypeVisitor<T> visitor);

    // Python property signature: def (self: mypy.types.TypedDictType) -> builtins.bool
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.is_final
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public boolean isFinal();

    // Python signature: is_anonymous def (self: mypy.types.TypedDictType) -> builtins.bool
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.is_anonymous
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public boolean isAnonymous();

    // getter for class field 'items', Python type: builtins.dict[builtins.str, mypy.types.Type]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.items
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public Map<String, Type> getItems();

    // getter for class field 'fallback', Python type: mypy.types.Instance
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.fallback
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public Instance getFallback();

    // Python signature: names_are_wider_than def (self: mypy.types.TypedDictType, other: mypy.types.TypedDictType) -> builtins.bool
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    // TODO provide javadoc for
    //     key: mypy.types.TypedDictType.names_are_wider_than
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public boolean namesAreWiderThan();


    // TODO unprocessed Decorator of function 'deserialize'


}
