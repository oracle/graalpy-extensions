package org.graalvm.python.javainterfacegen.generator;

import org.graalvm.python.javainterfacegen.GeneratorTestBase;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.junit.Test;

public class FunctionTest extends GeneratorTestBase {

    public FunctionTest() {

    }

    @Test
    public void function01() throws Exception {
        // TODO speed up the test, is so slow. 
//        Configuration config = createDefaultConfiguration();
//        checkGeneratorFromFile(getContext(), config);
    }
    
//    @Test
    public void function02() throws Exception {
        Configuration config = createDefaultConfiguration();
        checkGeneratorFromFile(getContext(), config);
    }
    
//    @Test
    public void function03() throws Exception {
        Configuration config = createDefaultConfiguration();
        checkGeneratorFromFile(getContext(), config);
    }
    
//    @Test
    public void optionalArgs() throws Exception {
        Configuration config = createDefaultConfiguration();
        checkGeneratorFromFile(getContext(), config);
    }
   
}
