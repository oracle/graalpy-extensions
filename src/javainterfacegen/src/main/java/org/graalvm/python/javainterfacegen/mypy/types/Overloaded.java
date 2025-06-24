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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;

/**
 * Overloaded function type T1, ... Tn, where each Ti is CallableType.
 *
 * The variant to call is chosen based on static argument
 * types. Overloaded function types can only be defined in stub
 * files, and thus there is no explicit runtime dispatch
 * implementation.
 */
public interface Overloaded extends FunctionLike {
    // Python def: Overloaded(mypy.types.FunctionLike)

    public static final String FQN = "mypy.types.Overloaded";

    // Python signature: def (self: mypy.types.Overloaded, name: builtins.str) -> mypy.types.Overloaded
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public Overloaded withName(String name);

    // Python signature: def (self: mypy.types.Overloaded) -> builtins.bool
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public boolean isTypeObj();

    // Python signature: def (self: mypy.types.Overloaded) -> mypy.nodes.TypeInfo
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public TypeInfo typeObject();

    // Python signature: def (self: mypy.types.Overloaded) -> builtins.dict[builtins.str, Any]
    // type class of return type: mypy.types.TypeAliasType
    public Map<String, Value> serialize();

    // Python signature: def (self: mypy.types.Overloaded) -> mypy.types.Overloaded
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public Overloaded withUnpackedKwargs();

    // Python signature: def (self: mypy.types.Overloaded) -> Union[builtins.str, None]
    // type class of return type: mypy.types.UnionType
    public Optional<String> name();


    // Python property signature: def (self: mypy.types.Overloaded) -> builtins.list[mypy.types.CallableType]
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public List<CallableType> getItems();


    // TODO unprocessed Decorator of function 'deserialize'



}
