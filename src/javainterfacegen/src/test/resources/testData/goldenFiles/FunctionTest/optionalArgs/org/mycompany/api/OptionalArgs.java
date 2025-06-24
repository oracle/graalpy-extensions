/*
 * TODO handle license
 * Copyright (c) 2024, Petr and/or its affiliates
 * */

package org.mycompany.api;

import java.util.Optional;
import java.util.ServiceLoader;
import org.mycompany.api.optionalArgs.MyResult;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.GuestValue;
import org.graalvm.python.javainterfacegen.Utils;


/**
 * TODO handle JavaDoc
 */
public interface OptionalArgs extends GuestValue {

    // generated from src/test/resources/testData/testFiles/FunctionTest/optionalArgs.py

    interface OptionalArgsImplProvider {
        OptionalArgs createImplementation(Value value);
    }

    public static OptionalArgs fromContext(Context context) {
        Value pythonBindings = context.getBindings(Utils.PYTHON);
        if (!pythonBindings.hasMember("optionalArgs_main")) {
            pythonBindings.putMember("optionalArgs_main", context.eval(Utils.PYTHON, "import optionalArgs"));
        }
        Value optionalArgs = pythonBindings.getMember("optionalArgs_main").getMember("optionalArgs");
        Optional<OptionalArgsImplProvider> findFirst = ServiceLoader.load(OptionalArgsImplProvider.class).findFirst();
        if (findFirst.isEmpty()) {
            return new OptionalArgsImpl(optionalArgs);
        }
        return findFirst.get().createImplementation(optionalArgs);
    }

    public static record PipelineArgs (String task, String config, Boolean useFast, Value trustRemoteCode, Value pipelineClass) {
        public PipelineArgs() {
            this(null, null, null, null, null);
        }
    }

    public static final class PipelineArgsBuilder {

        private String task = null;
        public PipelineArgsBuilder task(String task) {
            this.task = task;
            return this;
        }

        private String config = null;
        public PipelineArgsBuilder config(String config) {
            this.config = config;
            return this;
        }

        private Boolean useFast = null;
        public PipelineArgsBuilder useFast(Boolean useFast) {
            this.useFast = useFast;
            return this;
        }

        private Value trustRemoteCode = null;
        public PipelineArgsBuilder trustRemoteCode(Value trustRemoteCode) {
            this.trustRemoteCode = trustRemoteCode;
            return this;
        }

        private Value pipelineClass = null;
        public PipelineArgsBuilder pipelineClass(Value pipelineClass) {
            this.pipelineClass = pipelineClass;
            return this;
        }


        public PipelineArgs build() {
            return new PipelineArgs(task, config, useFast, trustRemoteCode, pipelineClass);
        }
    }

    public MyResult pipeline ();
    public MyResult pipeline (PipelineArgs optionalArgs);

// TODO reflect variable 'Optional'// TODO reflect variable 'Any'
}
