package org.graalvm.python.javainterfacegen.generator.impl;

import java.io.File;

import org.graalvm.python.javainterfacegen.GeneratorTestBase;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.configuration.DefaultConfigurationLoader;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.TestNodes;
import org.graalvm.python.javainterfacegen.generator.TestTypes;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


public class TypeGeneratorImplTest extends GeneratorTestBase {

    private final String PACKAGE = "org.mycompany.api.types";

    public TypeGeneratorImplTest() {
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

    private GeneratorContext createClassContext() throws Exception {
        Configuration config = new Configuration (new DefaultConfigurationLoader());
        config.load();
        GeneratorContext fileContext = new GeneratorContext(null, config, new TestNodes.FakeMypyFile("test", "test.py"));
        GeneratorContext classContext = new GeneratorContext(fileContext, config, new TestNodes.FakeClassDef("TestClass", "test.TestClass"), true);
        classContext.setJavaFQN("test.TestClass");
        return classContext;
    }

    @Test
    public void testUnionType_01 () throws Exception {
        // Union[mypy.nodes.FuncDef, mypy.nodes.Var, mypy.nodes.Decorator]
        TypeManager.get().registerType("mypy.nodes.FuncDef", PACKAGE + ".mypy.nodes.FuncDef");
        TypeManager.get().registerType("mypy.nodes.Var", PACKAGE + ".mypy.nodes.Var");
        TypeManager.get().registerType("mypy.nodes.Decorator", PACKAGE + ".mypy.nodes.Decorator");

        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeInstance("mypy.nodes.FuncDef"),
                new TestTypes.FakeInstance("mypy.nodes.Var"),
                new TestTypes.FakeInstance("mypy.nodes.Decorator")
        );

        checkGeneratedType(union);
    }


    @Test
    public void testUnionType_02() throws Exception {
        // Union[mypy.nodes.FuncDef, None, mypy.nodes.Decorator]
        TypeManager.get().registerType("mypy.nodes.FuncDef", PACKAGE + ".mypy.nodes.FuncDef");
        TypeManager.get().registerType("mypy.nodes.Decorator", PACKAGE + ".mypy.nodes.Decorator");

        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeInstance("mypy.nodes.FuncDef"),
                new TestTypes.FakeNone(),
                new TestTypes.FakeInstance("mypy.nodes.Decorator")
        );

        checkGeneratedType(union);
    }

    @Test
    public void testSimpleTypes() throws Exception {
        // Union[builtins.int, builtins.float, builtins.complex, builtins.bool, builtins.str, None]
        TypeManager.get().registerType("builtins.complex", PACKAGE + ".Complex");
        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeInstance("builtins.int"),
                new TestTypes.FakeInstance("builtins.float"),
                new TestTypes.FakeInstance("builtins.complex"),
                new TestTypes.FakeInstance("builtins.bool"),
                new TestTypes.FakeInstance("builtins.str"),
                new TestTypes.FakeNone()
        );
        checkGeneratedType(union);
    }

    @Test
    public void testDict_01() throws Exception {
        // Union[builtins.dict[builtins.str, Any], builtins.str]
        TestTypes.FakeInstance instance = new TestTypes.FakeInstance("builtins.dict");
                instance.addArg(new TestTypes.FakeInstance("builtins.str"));
                instance.addArg(new TestTypes.FakeAny());
        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                instance,
                new TestTypes.FakeInstance("builtins.str")
        );
        checkGeneratedType(union);
    }

    private void checkGeneratedType(Type type) throws Exception {
        GeneratorContext context = createClassContext();
        TypeGeneratorImpl generator = new TypeGeneratorImpl();

        String generatedClass = generator.createType(type, context, "org.test", "TestType");

        generatedClass = generatedClass.substring(generatedClass.indexOf("package "));
        File goldenFile = getGoldenFile(".java");
        writeFileIfMissing(goldenFile, generatedClass);
        assertDescriptionMatches(generatedClass, goldenFile);
    }

}
