package org.graalvm.python.javainterfacegen.generator;

import static org.junit.Assert.assertTrue;

import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.configuration.DefaultConfigurationLoader;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


public class GeneratorContextTest {
    
    public GeneratorContextTest() {
    }
    
//    @BeforeClass
//    public static void setUpClass() {
//    }
//    
//    @AfterClass
//    public static void tearDownClass() {
//    }
//    
//    @Before
//    public void setUp() {
//    }
//    
//    @After
//    public void tearDown() {
//    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testAddImport() throws Exception {
        Configuration config = new Configuration (new DefaultConfigurationLoader());
        config.load();
        GeneratorContext fileContext = new GeneratorContext(null, config, new TestNodes.FakeMypyFile("test", "test.py"));
        GeneratorContext classContext = new GeneratorContext(fileContext, config, new TestNodes.FakeClassDef("TestClass", "a.b.c.TestClass"), true);
        classContext.setJavaFQN(((ClassDef)classContext.getCurrentNode()).getFullname());
        
        assertTrue(classContext.getImports().length == 0);
        
        classContext.addImport("int");
        assertTrue(classContext.getImports().length == 0);
        
        classContext.addImport("String");
        assertTrue(classContext.getImports().length == 0);
        
        classContext.addImport("java.lang.String");
        assertTrue(classContext.getImports().length == 0);
        
        classContext.addImport("a.b.c.YourClass");
        assertTrue(classContext.getImports().length == 0);
        
        classContext.addImport("a.b.c.d.YourClass");
        assertTrue(classContext.getImports().length == 1);
        
    }
    
    
}
