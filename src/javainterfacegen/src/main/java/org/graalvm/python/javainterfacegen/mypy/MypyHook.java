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
package org.graalvm.python.javainterfacegen.mypy;

import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.KeywordArguments;
import org.graalvm.python.embedding.PositionalArguments;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import org.graalvm.python.javainterfacegen.python.Utils;

public interface MypyHook {

//    static class MypyHookImpl extends GuestValueDefaultImpl implements MypyHook {
//
//        private final org.graalvm.polyglot.Context context;
//
//        public MypyHookImpl(Context context, Value instance) {
//            super(instance);
//            this.context = context;
//        }
//
//        @Override
//        public MypyFile getNodesForFile(String path) {
//            Value value = getValue().invokeMember("getNodesForFile", path);
//            return new MypyFile.MypyFileImpl(value);
//        }
//
//        @Override
//        public void serializeAST(MypyFile ast, String output) {
//            getValue().invokeMember("serialize_ast", ast.getValue(), output);
//        }
//
//        @Override
//        public MypyFile loadAST(String filePath) {
//            return new MypyFile.MypyFileImpl(getValue().invokeMember("load_ast", filePath));
//        }
//
//        /**
//         *
//         * @param inputPath can be folder (if it's module) or path to a file
//         * @param cacheFile where to serialize the parsed result
//         * @return
//         */
//        @Override
//        public Map<MypyFile> serializeResult(List<String> inputPaths, String cacheFile) {
//            Value files = getValue().invokeMember("serialize_result", inputPaths, cacheFile);
//            GuestArray<MypyFile> result = new GuestArray<>(files, (value) -> {
//                String pythonFQN = Utils.getFullyQualifedName(value);
//                if (MypyFile.FQN.equals(pythonFQN)) {
//                    return new MypyFile.MypyFileImpl(value);
//                }
//                throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
//            });
//            return result;
//        }
//
//        @Override
//        public List<MypyFile> deserializeResult(String serializedDataFile, List<String> inputPaths) {
//            Value value = getValue().invokeMember("load_result", serializedDataFile, inputPaths);
//            if (value.hasArrayElements()) {
//                List<MypyFile> result = new ArrayList((int)value.getArraySize());
//                for (int i = 0; i < value.getArraySize(); i++) {
//                    result.add(new MypyFile.MypyFileImpl(value.getArrayElement(i)));
//                }
//                return result;
//            }
//            return null;
//        }
//
//        @Override
//        public void setMypyCacheFolder(String path) {
//            getValue().invokeMember("set_mypy_cache_folder", path);
//        }
//    }
//
    public static MypyHook fromContext(Context context) throws java.io.IOException {
        Value pythonBindings = context.getBindings(Utils.PYTHON);
        if (!pythonBindings.hasMember("mypyhook_main")) {
            pythonBindings.putMember("mypyhook_main", context.eval(Utils.PYTHON, "import analyzePath"));
//            pythonBindings.putMember("mypyhook_main", context.eval(Utils.PYTHON, "import sys; print(sys.path); import analyzePath"));

            // TODO we need to init it somewhere else
            ArgKind.initFromContext(context);
        }
        Value mypyHook = pythonBindings.getMember("mypyhook_main").getMember("analyzePath");

        return mypyHook.as(MypyHook.class);
    }

//    MypyFile getNodesForFile(String path);
//
//    void serializeAST(MypyFile ast, String output);
//
//    MypyFile loadAST(String filePath);

    Map<String, Value> serialize_result(List<String> inputPaths, String cacheFile);

    void serialize_mypyfile(Object mypyFile, String ouputFile);
    String serialize_mypyFile_toStr(Object mypyFile);

    Map<String, Value> load_result(String serializedData);
    Map<String, Value> load_result(String serializedData, Map<String, Value> previous);

    Map<String, Value> create_mypyfile(String data);

    Value extract_type_info (Object file_paths);
    void set_mypy_cache_folder(String path);

    void test_fn_noArgs();

    void test_only_posArgs();
    void test_only_posArgs(PositionalArguments args);

    void test_only_kwArgs();
    void test_only_kwArgs(KeywordArguments kwargs);

    void test_posArgs_kwArgs();
    void test_posArgs_kwArgs(PositionalArguments args);
    void test_posArgs_kwArgs(KeywordArguments kwArgs);
    void test_posArgs_kwArgs(PositionalArguments args, KeywordArguments kwArgs);

    void test_pos_posArgs(Object a1);
    void test_pos_posArgs(Object a1, PositionalArguments args);

    void test_pos_kwArgs(Object a1);
    void test_pos_kwArgs(Object a1, KeywordArguments kwArgs);

    void test_pos_posArgs_kwArgs(Object a1);
    void test_pos_posArgs_kwArgs(Object a1, PositionalArguments args);
    void test_pos_posArgs_kwArgs(Object a1, KeywordArguments kwArgs);
    void test_pos_posArgs_kwArgs(Object a1, PositionalArguments args, KeywordArguments kwArgs);

    void test_pos3_posArgs_kwArgs(Object a1, Object a2, Object a3);
    void test_pos3_posArgs_kwArgs(Object a1, Object a2, Object a3, PositionalArguments args);
    void test_pos3_posArgs_kwArgs(Object a1, Object a2, Object a3, KeywordArguments kwArgs);
    void test_pos3_posArgs_kwArgs(Object a1, Object a2, Object a3, PositionalArguments args, KeywordArguments kwArgs);

    void test_posArgs_named_args(KeywordArguments kwArgs);
    void test_posArgs_named_args(PositionalArguments args, KeywordArguments kwArgs);

    Map<String, String> extract_docstrings(String path, String moduleFQN);

//    void test_posArgs_named_args(Object named1, Object named2); is not possible
//
//    void test_fn(PositionalArguments args);
////    void test_fn(Object... args);
//    void test_pos_args(Object arg1);
//    void test_pos_args(Object arg1, PositionalArguments args);
//    void test_posarg(PositionalArguments args);
//
//    void test_kwargsonly(KeywordArguments kwargs);
//    void test_kwargsonly3(KeywordArguments kwargs);

}
