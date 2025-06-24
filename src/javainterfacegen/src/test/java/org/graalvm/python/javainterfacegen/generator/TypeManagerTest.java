package org.graalvm.python.javainterfacegen.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.configuration.DefaultConfigurationLoader;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeManagerTest {

    private static String TEST_SOURCE = """
package org.test.api.mypy.nodes;

import mypy.nodes.FuncItem#;
import mypy.nodes.Statement#;
import mypy.nodes.SymbolNode#;

public interface FuncDef extends mypy.nodes.FuncItem#, mypy.nodes.SymbolNode#, mypy.nodes.Statement# {
}
""";

    public TypeManagerTest() {
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

    @Test
    public void testInExtendsExpr() {
        String content = "public& interface FuncDef ext&ends &mypy.nodes.FuncItem#, mypy.nodes.SymbolNode#, my&py.nodes.Statement# {\n &}";
        String source = content.replaceAll("&", "");

        TypeManager typeManager = TypeManager.get();

        int index = content.indexOf('&');
        assertFalse(typeManager.inExtendsExpr(source, index));

        index = content.indexOf('&', index + 1);
        assertFalse(typeManager.inExtendsExpr(source, index - 1));

        index = content.indexOf('&', index + 1);
        assertTrue(typeManager.inExtendsExpr(source, index - 2));

        index = content.indexOf('&', index + 1);
        assertTrue(typeManager.inExtendsExpr(source, index - 3));

        index = content.indexOf('&', index + 1);
        assertFalse(typeManager.inExtendsExpr(source, index - 4));
    }

    @Test
    public void testInExtendsExpr_02() {
        String content = """
            package &org.graalvm.python.matplotlib.api.types;
            &public class AxesImageOrPcolorImageOrQuadMesh {

                private final &Optional<&matplotlib.image.AxesImage#> axesImage;
                private final Optional<matplotlib.image.PcolorImage#> pcolorImage;
                private final &Optional<Value> quadMesh;
            """;
            String source = content.replaceAll("&", "");

        TypeManager typeManager = TypeManager.get();

        int index = content.indexOf('&');
        int delta = 0;
        while(index > 0) {
            assertFalse(typeManager.inExtendsExpr(source, index + delta++));
            index = content.indexOf('&', index + 1);
        }
    }


    @Test
    public void testReplaceInExtends01() {
        TypeManager typeManager = TypeManager.get();
        String content = TEST_SOURCE;
        content = typeManager.replace(content, "mypy.nodes.FuncItem#", "org.test.api.mypy.nodes.FuncItem", false);
        content = typeManager.replace(content, "mypy.nodes.Statement#", "org.test.api.mypy.nodes.Statement", false);
        content = typeManager.replace(content, "mypy.nodes.SymbolNode#", "org.test.api.mypy.nodes.SymbolNode", false);

        String expectedContent = """
package org.test.api.mypy.nodes;

import org.test.api.mypy.nodes.FuncItem;
import org.test.api.mypy.nodes.Statement;
import org.test.api.mypy.nodes.SymbolNode;

public interface FuncDef extends FuncItem, SymbolNode, Statement {
}
""";
        assertEquals(expectedContent, content);
    }

    @Test
    public void testReplaceInExtends02() {
        TypeManager typeManager = TypeManager.get();
        String content = TEST_SOURCE;
        content = typeManager.replace(content, "mypy.nodes.FuncItem#", "org.test.api.python.GuestValue", true);
        content = typeManager.replace(content, "mypy.nodes.Statement#", "org.test.api.mypy.nodes.Statement", false);
        content = typeManager.replace(content, "mypy.nodes.SymbolNode#", "org.test.api.mypy.nodes.SymbolNode", false);

        String expectedContent = """
package org.test.api.mypy.nodes;

import org.test.api.python.GuestValue;
import org.test.api.mypy.nodes.Statement;
import org.test.api.mypy.nodes.SymbolNode;

public interface FuncDef extends SymbolNode, Statement {
}
""";
        assertEquals(expectedContent, content);
    }

    @Test
    public void testReplaceInExtends03() {
        TypeManager typeManager = TypeManager.get();
        String content = TEST_SOURCE;
        content = typeManager.replace(content, "mypy.nodes.FuncItem#", "org.test.api.mypy.nodes.FuncItem", false);
        content = typeManager.replace(content, "mypy.nodes.Statement#", "org.test.api.python.GuestValue", true);
        content = typeManager.replace(content, "mypy.nodes.SymbolNode#", "org.test.api.mypy.nodes.SymbolNode", false);

        String expectedContent = """
package org.test.api.mypy.nodes;

import org.test.api.mypy.nodes.FuncItem;
import org.test.api.python.GuestValue;
import org.test.api.mypy.nodes.SymbolNode;

public interface FuncDef extends FuncItem, SymbolNode {
}
""";
        assertEquals(expectedContent, content);
    }

    @Test
    public void testReplaceInExtends04() {
        TypeManager typeManager = TypeManager.get();
        String content = TEST_SOURCE;
        content = typeManager.replace(content, "mypy.nodes.FuncItem#", "org.test.api.mypy.nodes.FuncItem", false);
        content = typeManager.replace(content, "mypy.nodes.Statement#", "org.test.api.mypy.nodes.Statement", false);
        content = typeManager.replace(content, "mypy.nodes.SymbolNode#", "org.test.api.python.GuestValue", true);

        String expectedContent = """
package org.test.api.mypy.nodes;

import org.test.api.mypy.nodes.FuncItem;
import org.test.api.mypy.nodes.Statement;
import org.test.api.python.GuestValue;

public interface FuncDef extends FuncItem, Statement {
}
""";
        assertEquals(expectedContent, content);
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
    public void testResolveJavaTypeBasic() throws Exception {

        GeneratorContext context = createClassContext();

        TypeManager typeManager = TypeManager.get();

        TestTypes.FakeInstance typeInstance = new TestTypes.FakeInstance("builtins.str");
        assertEquals("String", typeManager.resolveJavaType(context, typeInstance, false, context.getDefaultJavaType()));

        typeInstance = new TestTypes.FakeInstance("builtins.int");
        assertEquals("long", typeManager.resolveJavaType(context, typeInstance, false, context.getDefaultJavaType()));

        typeInstance = new TestTypes.FakeInstance("builtins.bool");
        assertEquals("boolean", typeManager.resolveJavaType(context, typeInstance, false, context.getDefaultJavaType()));

        typeInstance = new TestTypes.FakeInstance("builtins.float");
        assertEquals("double", typeManager.resolveJavaType(context, typeInstance, false, context.getDefaultJavaType()));

    }

    @Test
    public void testResolveJavaTypeList() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        TestTypes.FakeInstance typeListInstance = new TestTypes.FakeInstance("builtins.list");
        typeListInstance.addArg(new TestTypes.FakeInstance("builtins.str"));
        assertEquals("List<String>", typeManager.resolveJavaType(context, typeListInstance, false, context.getDefaultJavaType()));

        typeListInstance = new TestTypes.FakeInstance("builtins.list");
        typeListInstance.addArg(new TestTypes.FakeInstance("module1.Uknown"));
        assertEquals("List<module1.Uknown#org.graalvm.polyglot.Value>", typeManager.resolveJavaType(context, typeListInstance, false, context.getDefaultJavaType()));

        typeManager.registerType("mypy.types.Instance", "org.examples.mypy.types.Instance");
        typeListInstance = new TestTypes.FakeInstance("builtins.list");
        typeListInstance.addArg(new TestTypes.FakeInstance("mypy.types.Instance"));
        assertEquals("List<Instance>", typeManager.resolveJavaType(context, typeListInstance, false, context.getDefaultJavaType()));
        assertTrue(Arrays.asList(context.getImports()).contains("org.examples.mypy.types.Instance"));

        //builtins.set[builtins.int]
        typeListInstance = new TestTypes.FakeInstance("builtins.set");
        typeListInstance.addArg(new TestTypes.FakeInstance("builtins.int"));
        assertEquals("Set<Long>", typeManager.resolveJavaType(context, typeListInstance, false, context.getDefaultJavaType()));
        assertTrue(Arrays.asList(context.getImports()).contains("java.util.Set"));

    }

    @Test
    public void testResolveJavaTypeDir() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        //builtins.dict[builtins.str, builtins.set[builtins.str]]
        TestTypes.FakeInstance instance = new TestTypes.FakeInstance("builtins.dict");
        instance.addArg(new TestTypes.FakeInstance("builtins.str"));
        TestTypes.FakeInstance setInstance = new TestTypes.FakeInstance("builtins.set");
        setInstance.addArg(new TestTypes.FakeInstance("builtins.str"));
        instance.addArg(setInstance);
        assertEquals("Map<String, Set<String>>", typeManager.resolveJavaType(context, instance, false, context.getDefaultJavaType()));

        //builtins.dict[builtins.str, Any]
        instance = new TestTypes.FakeInstance("builtins.dict");
        instance.addArg(new TestTypes.FakeInstance("builtins.str"));
        instance.addArg(new TestTypes.FakeAny());
        assertEquals("Map<String, Value>", typeManager.resolveJavaType(context, instance, false, context.getDefaultJavaType()));

    }

    @Test
    public void testResolveTypeAlias() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        //builtins.dict[builtins.str, Any]
        TestTypes.FakeInstance instance = new TestTypes.FakeInstance("builtins.dict");
        instance.addArg(new TestTypes.FakeInstance("builtins.str"));
        instance.addArg(new TestTypes.FakeAny());
        TypeAliasType typeAliasType
                = new TestTypes.FakeTypeAliasType(new TestNodes.FakeTypeAlias(instance));
        assertEquals("Map<String, Value>", typeManager.resolveJavaType(context, typeAliasType, false, context.getDefaultJavaType()));
    }

    @Test
    public void testResolveJavaTypeUnion() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeInstance("builtins.str"), new TestTypes.FakeInstance("builtins.int"));
        assertEquals("Value", typeManager.resolveJavaType(context, union, false, context.getDefaultJavaType()));
    }

    @Test
    public void testResolveJavaTypeUnionNone() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeInstance("builtins.str"), new TestTypes.FakeNone());
        assertEquals("String", typeManager.resolveJavaType(context, union, false, context.getDefaultJavaType()));
    }

    @Test
    public void testResolveJavaTypeGeneral() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        TestTypes.FakeInstance typeInstance = new TestTypes.FakeInstance("module1.Uknown");
        assertEquals("module1.Uknown#org.graalvm.polyglot.Value", typeManager.resolveJavaType(context, typeInstance, false, context.getDefaultJavaType()));
    }

    @Test
    public void testResolveJavaTypeVarType() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        // mypy.visitor.NodeVisitor[T`-1]
        TestTypes.FakeInstance instance = new TestTypes.FakeInstance("mypy.visitor.NodeVisitor");
        instance.addArg(new TestTypes.FakeTypeVarType("T"));
        typeManager.registerType("mypy.visitor.NodeVisitor", "org.examples.NodeVisitor");
        assertEquals("NodeVisitor", typeManager.resolveJavaType(context, instance, false, context.getDefaultJavaType()));

        TestTypes.FakeTypeVarType fakeTypeVar = new TestTypes.FakeTypeVarType("T");
        assertEquals("Value", typeManager.resolveJavaType(context, fakeTypeVar, false, context.getDefaultJavaType()));
    }

    @Test
    public void testResolveJavaTypeLiteralTypes() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        TestTypes.FakeInstance fakeStr = new TestTypes.FakeInstance("builtins.str");
        // Union[matplotlib._enums.CapStyle, Literal['butt'], Literal['projecting'], Literal['round']]
        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeLiteralType("butt", fakeStr),
                new TestTypes.FakeLiteralType("projecting", fakeStr),
                new TestTypes.FakeLiteralType("round", fakeStr)
        );
        assertEquals("String", typeManager.resolveJavaType(context, union, false, context.getDefaultJavaType()));
    }

    @Test
    public void testCreateTypeName() throws Exception {
        GeneratorContext context = createClassContext();
        TypeManager typeManager = TypeManager.get();

        // Union[None, mypy.nodes.FuncDef, mypy.nodes.Var, mypy.nodes.Decorator]
        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeNone(),
                new TestTypes.FakeInstance("mypy.nodes.Var"),
                new TestTypes.FakeInstance("mypy.nodes.Decorator")
        );
        assertEquals("DecoratorOrVarOrNone", TypeNameGenerator.createName(union));

        // Union[builtins.dict[builtins.str, Any], builtins.str]
        TestTypes.FakeInstance instance = new TestTypes.FakeInstance("builtins.dict");
                instance.addArg(new TestTypes.FakeInstance("builtins.str"));
                instance.addArg(new TestTypes.FakeAny());
        union = new TestTypes.FakeUnion(
                instance,
                new TestTypes.FakeInstance("builtins.str")
        );
        assertEquals("DictOfStrAny_OrStr", TypeNameGenerator.createName(union));
    }

    @Test
    public void testCreateTypeName_Union_Literal_01() throws Exception {

        TypeManager typeManager = TypeManager.get();

        typeManager.registerType("test.CapStyle", "test.CapStyle");

        TestTypes.FakeInstance fakeStr = new TestTypes.FakeInstance("builtins.str");
        // Union[matplotlib._enums.CapStyle, Literal['butt'], Literal['projecting'], Literal['round']]
        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeInstance("test.CapStyle"),
                new TestTypes.FakeLiteralType("butt", fakeStr),
                new TestTypes.FakeLiteralType("projecting", fakeStr),
                new TestTypes.FakeLiteralType("round", fakeStr)
        );

        assertEquals("CapStyleOrStr", TypeNameGenerator.createName(union));
    }

    @Test
    public void testCreateTypeName_Union_Literal_02() throws Exception {

        TypeManager typeManager = TypeManager.get();


        TestTypes.FakeInstance fakeStr = new TestTypes.FakeInstance("builtins.str");
        // Union[matplotlib._enums.CapStyle, Literal['butt'], Literal['projecting'], Literal['round']]
        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                new TestTypes.FakeLiteralType("butt", fakeStr),
                new TestTypes.FakeLiteralType("projecting", fakeStr),
                new TestTypes.FakeLiteralType("round", fakeStr)
        );

        assertEquals("Str", TypeNameGenerator.createName(union));
    }

    @Test
    public void testCreateTypeName_Union_02() throws Exception {

        TypeManager typeManager = TypeManager.get();

        TupleType tt = new TestTypes.FakeTupleType(
                List.of(new TestTypes.FakeInstance("builtins.float"), new TestTypes.FakeInstance("builtins.int")),
                new TestTypes.FakeInstance("psutil._pslinux.scputimes"));
        TypeAliasType typeAliasType
                = new TestTypes.FakeTypeAliasType(new TestNodes.FakeTypeAlias(tt));

        TestTypes.FakeInstance fakeList = new TestTypes.FakeInstance("builtins.list",
                List.of(tt));
        // Union[matplotlib._enums.CapStyle, Literal['butt'], Literal['projecting'], Literal['round']]
        TestTypes.FakeUnion union = new TestTypes.FakeUnion(
                typeAliasType, fakeList);

        assertEquals("ListOfScputimes_OrScputimes", TypeNameGenerator.createName(union));
    }
}
