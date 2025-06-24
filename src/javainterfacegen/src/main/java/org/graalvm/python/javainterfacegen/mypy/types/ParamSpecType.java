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

/**
 * Type that refers to a ParamSpec.
 *
 * A ParamSpec is a type variable that represents the parameter
 * types, names and kinds of a callable (i.e., the signature without
 * the return type).
 *
 * This can be one of these forms
 *  * P (ParamSpecFlavor.BARE)
 *  * P.args (ParamSpecFlavor.ARGS)
 *  * P.kwargs (ParamSpecFLavor.KWARGS)
 *
 * The upper_bound is really used as a fallback type -- it's shared
 * with TypeVarType for simplicity. It can't be specified by the user
 * and the value is directly derived from the flavor (currently
 * always just 'object').
 */
public interface ParamSpecType extends TypeVarLikeType {
    // Python def: ParamSpecType(mypy.types.TypeVarLikeType)

    public static final String FQN = "mypy.types.ParamSpecType";

    // getter for class field 'flavor', Python type: builtins.int
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public int getFlavor();

    // Python signature: name_with_suffix def (self: mypy.types.ParamSpecType) -> builtins.str
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public String nameWithSuffix();

    // getter for class field 'prefix', Python type: mypy.types.Parameters
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public Parameters getPrefix();

    // Python signature: with_flavor def (self: mypy.types.ParamSpecType, flavor: builtins.int) -> mypy.types.ParamSpecType
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public ParamSpecType withFlavor(int flavor);

    // Python signature: copy_modified def (self: mypy.types.ParamSpecType, *, id: Any =, flavor: builtins.int =, prefix: Any =, default: Any =, **kwargs: Any) -> mypy.types.ParamSpecType
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public static record CopyModifiedArgs (AnyType id, Integer flavor, AnyType prefix, AnyType defaultValue) {
        public CopyModifiedArgs() {
            this(null, null, null, null);
        }
    }

    public static final class CopyModifiedArgsBuilder {

        private AnyType id = null;
        public CopyModifiedArgsBuilder id(AnyType id) {
            this.id = id;
            return this;
        }

        private Integer flavor = null;
        public CopyModifiedArgsBuilder flavor(Integer flavor) {
            this.flavor = flavor;
            return this;
        }

        private AnyType prefix = null;
        public CopyModifiedArgsBuilder prefix(AnyType prefix) {
            this.prefix = prefix;
            return this;
        }

        private AnyType defaultValue = null;
        public CopyModifiedArgsBuilder defaultValue(AnyType defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }


        public CopyModifiedArgs build() {
            return new CopyModifiedArgs(id, flavor, prefix, defaultValue);
        }
    }

    public ParamSpecType copyModified();
    public ParamSpecType copyModified(CopyModifiedArgs optionalArgs);

}
