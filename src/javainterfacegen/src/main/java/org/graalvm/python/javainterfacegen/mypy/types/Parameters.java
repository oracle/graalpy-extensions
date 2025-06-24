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

// Genereted at 2024-09-26 10:22:39
// Generated from ../../git/mypy/mypy/types.py class def: Parameters

package org.graalvm.python.javainterfacegen.mypy.types;


import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.graalvm.polyglot.Value;

/**
 * Type that represents the parameters to a function.
 *
 * Used for ParamSpec analysis. Note that by convention we handle this
 * type as a Callable without return type, not as a "tuple with names",
 * so that it behaves contravariantly, in particular [x: int] <: [int].
 */
public interface Parameters extends ProperType {
    // Python def: Parameters(mypy.types.ProperType)

    public static final String FQN = "mypy.types.Parameters";

    // Python signature: formal_arguments def (self: mypy.types.Parameters, include_star_args: builtins.bool =) -> builtins.list[tuple[Union[builtins.str, None], Union[builtins.int, None], mypy.types.Type, builtins.bool, fallback=mypy.types.FormalArgument]]
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    /**
     * Yields the formal arguments corresponding to this callable, ignoring *arg and **kwargs.
     *
     * To handle *args and **kwargs, use the 'callable.var_args' and 'callable.kw_args' fields,
     * if they are not None.
     *
     * If you really want to include star args in the yielded output, set the
     * 'include_star_args' parameter to 'True'.
     */
    public List<Value> formalArguments();
    public List<Value> formalArguments(boolean include_star_args);

    // getter for class field 'variables', Python type: typing.Sequence[mypy.types.TypeVarLikeType]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public Set<TypeVarLikeType> getVariables();

    // Python signature: var_arg def (self: mypy.types.Parameters) -> Union[tuple[Union[builtins.str, None], Union[builtins.int, None], mypy.types.Type, builtins.bool, fallback=mypy.types.FormalArgument], None]
    // type class of return type: mypy.types.UnionType
    /**
     * The formal argument for *args.
     */
    public Value varArg();

    // getter for class field 'min_args', Python type: builtins.int
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public int getMinArgs();

    // getter for class field 'imprecise_arg_kinds', Python type: builtins.bool
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public boolean impreciseArgKinds();

    // Python signature: argument_by_position def (self: mypy.types.Parameters, position: Union[builtins.int, None]) -> Union[tuple[Union[builtins.str, None], Union[builtins.int, None], mypy.types.Type, builtins.bool, fallback=mypy.types.FormalArgument], None]
    // type class of return type: mypy.types.UnionType
    public Value argumentByPosition();
    public Value argumentByPosition(int position);

    // getter for class field 'is_ellipsis_args', Python type: builtins.bool
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public boolean isEllipsisArgs();

    // getter for class field 'arg_kinds', Python type: builtins.list[mypy.nodes.ArgKind]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public List<ArgKind> getArgKinds();

    // getter for class field 'arg_names', Python type: builtins.list[Union[builtins.str, None]]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public List<String> getArgNames();

    // Python signature: try_synthesizing_arg_from_vararg def (self: mypy.types.Parameters, position: Union[builtins.int, None]) -> Union[tuple[Union[builtins.str, None], Union[builtins.int, None], mypy.types.Type, builtins.bool, fallback=mypy.types.FormalArgument], None]
    // type class of return type: mypy.types.UnionType
    public Value trySynthesizingArgFromVararg();
    public Value trySynthesizingArgFromVararg(int position);

    // Python signature: kw_arg def (self: mypy.types.Parameters) -> Union[tuple[Union[builtins.str, None], Union[builtins.int, None], mypy.types.Type, builtins.bool, fallback=mypy.types.FormalArgument], None]
    // type class of return type: mypy.types.UnionType
    /**
     * The formal argument for **kwargs.
     */
    public Value kwArg();

    // Python signature: argument_by_name def (self: mypy.types.Parameters, name: Union[builtins.str, None]) -> Union[tuple[Union[builtins.str, None], Union[builtins.int, None], mypy.types.Type, builtins.bool, fallback=mypy.types.FormalArgument], None]
    // type class of return type: mypy.types.UnionType
    public Value argumentByName(String name);

    // Python signature: try_synthesizing_arg_from_kwarg def (self: mypy.types.Parameters, name: Union[builtins.str, None]) -> Union[tuple[Union[builtins.str, None], Union[builtins.int, None], mypy.types.Type, builtins.bool, fallback=mypy.types.FormalArgument], None]
    // type class of return type: mypy.types.UnionType
    public Value trySynthesizingArgFromKwarg(String name);

    // getter for class field 'arg_types', Python type: builtins.list[mypy.types.Type]
    // type class: mypy.types.Instance -> mypy.nodes.TypeInfo
    public List<Type> getArgTypes();

    // Python signature: copy_modified def (self: mypy.types.Parameters, arg_types: Any =, arg_kinds: Any =, arg_names: Any =, *, variables: Any =, is_ellipsis_args: Any =, imprecise_arg_kinds: Any =) -> mypy.types.Parameters
    // type class of return type: mypy.types.Instance -> mypy.nodes.TypeInfo
    public static record CopyModifiedArgs (Value argTypes, Value argKinds, Value argNames, Value variables, Value isEllipsisArgs, Value impreciseArgKinds) {
        public CopyModifiedArgs() {
            this(null, null, null, null, null, null);
        }
    }

    public static final class CopyModifiedArgsBuilder {

        private Value argTypes = null;
        public CopyModifiedArgsBuilder argTypes(Value argTypes) {
            this.argTypes = argTypes;
            return this;
        }

        private Value argKinds = null;
        public CopyModifiedArgsBuilder argKinds(Value argKinds) {
            this.argKinds = argKinds;
            return this;
        }

        private Value argNames = null;
        public CopyModifiedArgsBuilder argNames(Value argNames) {
            this.argNames = argNames;
            return this;
        }

        private Value variables = null;
        public CopyModifiedArgsBuilder variables(Value variables) {
            this.variables = variables;
            return this;
        }

        private Value isEllipsisArgs = null;
        public CopyModifiedArgsBuilder isEllipsisArgs(Value isEllipsisArgs) {
            this.isEllipsisArgs = isEllipsisArgs;
            return this;
        }

        private Value impreciseArgKinds = null;
        public CopyModifiedArgsBuilder impreciseArgKinds(Value impreciseArgKinds) {
            this.impreciseArgKinds = impreciseArgKinds;
            return this;
        }


        public CopyModifiedArgs build() {
            return new CopyModifiedArgs(argTypes, argKinds, argNames, variables, isEllipsisArgs, impreciseArgKinds);
        }
    }

    // TODO provide javadoc for
    //     key: mypy.types.Parameters.copy_modified
    //     file: ./target/javadoc-cache/types.py_javadoc.yaml.
    public Parameters copyModified();
    public Parameters copyModified(CopyModifiedArgs optionalArgs);


    // TODO unprocessed Decorator of function 'deserialize'



}
