package org.graalvm.python.javainterfacegen.generator;

import java.nio.file.Path;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratorUtilsTest {
    
    public GeneratorUtilsTest() {
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
    public void testConvertToJavaIdentifierName() {
        assertEquals("funcDef", GeneratorUtils.convertToJavaIdentifierName("FuncDef"));
        assertEquals("someField", GeneratorUtils.convertToJavaIdentifierName("some_field"));
        assertEquals("intValue", GeneratorUtils.convertToJavaIdentifierName("int"));
        assertEquals("classValue", GeneratorUtils.convertToJavaIdentifierName("Class"));
    }
    
}
