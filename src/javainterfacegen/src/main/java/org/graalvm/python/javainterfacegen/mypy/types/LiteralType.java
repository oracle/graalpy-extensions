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

import org.graalvm.polyglot.Value;

/**
 * The type of a Literal instance. Literal[Value]
 *
 * A Literal always consists of:
 *
 * 1. A native Python object corresponding to the contained inner value
 * 2. A fallback for this Literal. The fallback also corresponds to the
 *    parent type this Literal subtypes.
 *
 * For example, 'Literal[42]' is represented as
 * 'LiteralType(value=42, fallback=instance_of_int)'
 *
 * As another example, `Literal[Color.RED]` (where Color is an enum) is
 * represented as `LiteralType(value="RED", fallback=instance_of_color)'.
 */
public interface LiteralType extends ProperType {
    // Python def: LiteralType(mypy.types.ProperType)

    public static final String FQN = "mypy.types.LiteralType";

    // Python signature: def (self: mypy.types.LiteralType) -> builtins.str
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    /**
     * Returns the string representation of the underlying type.
     *
     * This function is almost equivalent to running `repr(self.value)`,
     * except it includes some additional logic to correctly handle cases
     * where the value is a string, byte string, a unicode string, or an enum.
     */
    public String valueRepr();

    // Python signature: def (self: mypy.types.LiteralType) -> builtins.bool
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public boolean isSingletonType();

    // Python signature: def (self: mypy.types.LiteralType) -> builtins.bool
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public boolean isEnumLiteral();

    // getter for class field 'value', Python type: Union[builtins.int, builtins.str, builtins.bool, builtins.float]
    // type class: mypy.types.TypeAliasType
    public Value getValueOf();

    // getter for class field 'fallback', Python type: mypy.types.Instance
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public Instance getFallback();


    // TODO unprocessed Decorator of function 'deserialize'



}
