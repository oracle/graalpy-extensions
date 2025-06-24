package org.graalvm.python.javainterfacegen.generator.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static junit.framework.TestCase.assertTrue;
import org.graalvm.collections.Pair;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.ContextFactory;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.GeneratorTestBase;
import org.graalvm.python.javainterfacegen.mypy.MypyHook;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 *
 * @author petr
 */
public class OverloadArgsGeneratorTest {

    public OverloadArgsGeneratorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    private static class TestInfoRule extends TestWatcher {

        private Description description;

        @Override
        protected void starting(Description description) {
            this.description = description;
        }

        public String getTestClassName() {
            return description.getClassName();
        }

        public String getTestMethodName() {
            return description.getMethodName();
        }

    }

    @Rule
    public TestInfoRule testInfo = new TestInfoRule();

    public File getTestFilesDir() {
        String dataDirPath = "src/test/resources/testData/testFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The test files folder, was not found.", dataDir.exists());
        return dataDir;
    }

    public File getTestFileFromTestAndTestMethod() {
        File testFilesDir = getTestFilesDir();
        File testDir = new File(testFilesDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        return new File(testDir, getTestMethodName() + ".py");
    }

    protected String getTestMethodName() {
        return testInfo.getTestMethodName();
    }

    public File getGeneratorCacheFile() {
        File testFile = getTestFileFromTestAndTestMethod();
        File parent = testFile.getParentFile();
        String name = testFile.getName();
        name = name.substring(0, name.lastIndexOf('.'));
        return new File(parent + "/" + name + ".json");
    }

    private static Context context = null;

    public static Context getContext() {

        if (context == null) {
            context = ContextFactory.getContext();
        }

        return context;
    }
    
//    MypyFile findMypyFile
    @Test
    public void testGenerateVariations_01() throws IOException {
        System.out.println("Test file: " + getTestFileFromTestAndTestMethod().toPath());
        System.out.println("GeneratorCacheFile: " + getGeneratorCacheFile().toPath());
        File cacheFile = getGeneratorCacheFile();
        MypyHook mypyHook = MypyHook.fromContext(getContext());
        Map<String, Value> mypyResult;
//        Value value = mypyHook.extract_type_info(List.of("/home/petr/labs/igen/java-api-from-python/generator/src/test/resources/testData/testFiles/OverloadArgsGeneratorTest/testGenerateVariations_01.py"));
//        System.out.println(value);
//        if (cacheFile.exists()) {
//            System.out.println("exist");
//            mypyResult = mypyHook.load_result(getGeneratorCacheFile().getAbsolutePath(), (List<String>) List.of(getTestFileFromTestAndTestMethod().getAbsolutePath()));
//        } else {
//            System.out.println("dosn't exists");
//
//            mypyResult = mypyHook.serialize_result((List<String>) List.of(getTestFileFromTestAndTestMethod().getAbsolutePath()), getGeneratorCacheFile().getAbsolutePath());
//
//        }
//        for (Map.Entry<String, Value> entry : mypyResult.entrySet()) {
//            String key = entry.getKey();
//            Value value = entry.getValue();
//            System.out.println("key: " + key);
//        }

//        System.out.println("generateVariations");
//        GeneratorContext context = null;
//        FuncDef fn = null;
//        List<List<Pair<String, Type>>> expResult = null;
//        List<List<Pair<String, Type>>> result = OverloadArgsGenerator.generateVariations(context, fn);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }

}
