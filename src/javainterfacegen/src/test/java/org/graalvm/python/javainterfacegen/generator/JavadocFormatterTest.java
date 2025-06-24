package org.graalvm.python.javainterfacegen.generator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;


public class JavadocFormatterTest {
    
    public JavadocFormatterTest() {
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
    public void testRemoveLeadingSpaces_01() {
        String text = "";
        String expResult = "";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_02() {
        String text = "   ";
        String expResult = "";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_03() {
        String text = "   \n";
        String expResult = "";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_04() {
        String text = "   \n     ";
        String expResult = "";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_05() {
        String text = "Hello";
        String expResult = "Hello";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_06() {
        String text = "    Hello";
        String expResult = "Hello";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_07() {
        String text = "    Hello     ";
        String expResult = "Hello";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_08() {
        String text = "    Hello     \n    ";
        String expResult = "Hello";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_09() {
        String text = "   \n   \n   Hello     \n    ";
        String expResult = "Hello";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_10() {
        String text = "   Hello     \nThis is a test";
        String expResult = "Hello\nThis is a test";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_11() {
        String text = "   Hello     \n    This is a test";
        String expResult = "Hello\nThis is a test";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_12() {
        String text = "   Hello     \n    This is a test     ";
        String expResult = "Hello\nThis is a test";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_13() {
        String text = "   Hello     \n    This is a test   \n \n  ";
        String expResult = "Hello\nThis is a test";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_14() {
        String text = "   Hello     \n\n    This is a test";
        String expResult = "Hello\n\nThis is a test";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_15() {
        String text = "   Hello     \n      \n    This is a test";
        String expResult = "Hello\n\nThis is a test";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_16() {
        String text = "Hello\nFirst line\nSecond line";
        String expResult = "Hello\nFirst line\nSecond line";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_17() {
        String text = "Hello\n  First line\nSecond line";
        String expResult = "Hello\nFirst line\nSecond line";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_18() {
        String text = "Hello\n  First line\n  Second line";
        String expResult = "Hello\nFirst line\nSecond line";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
    
    @Test
    public void testRemoveLeadingSpaces_19() {
        String text = "Hello\n  First line\n    Second line";
        String expResult = "Hello\nFirst line\n  Second line";
        String result = JavadocFormatter.removeLeadingSpaces(text);
        assertEquals(expResult, result);
    }
}
