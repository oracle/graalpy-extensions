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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.python.GuestValue;

/**
 * Summary of module attributes and types.
 *
 * This is used for instances of types.ModuleType, because they can have different
 * attributes per instance, and for type narrowing with hasattr() checks.
 */
public interface ExtraAttrs extends GuestValue {
    // Python def: ExtraAttrs(builtins.object)

    // Python signature: def (self: mypy.types.ExtraAttrs) -> builtins.dict[builtins.str, Any]
    // type class of return type: mypy.types.TypeAliasType
    public Map<String, Value> serialize();

    // getter for class field 'immutable', Python type: builtins.set[builtins.str]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public Set<String> getImmutable();

    // Python signature: def (self: mypy.types.ExtraAttrs) -> mypy.types.ExtraAttrs
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
//    public ExtraAttrs copy();

    // getter for class field 'mod_name', Python type: Union[builtins.str, None]
    // type class: mypy.types.UnionType
    public Optional<String> getModName();


    // TODO unprocessed Decorator of function 'deserialize'

    // getter for class field 'attrs', Python type: builtins.dict[builtins.str, mypy.types.Type]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public Map<String, Type> getAttrs();

}
