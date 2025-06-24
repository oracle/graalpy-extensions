package org.graalvm.python.javainterfacegen.generator.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.python.javainterfacegen.generator.JavadocStorageManager;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class JavadocStorageManagerYamlTest {
    
    public JavadocStorageManagerYamlTest() {
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
    public void testSave_01() throws Exception {
        Map<String, String> map = new HashMap();
        map.put("key", "value");
        Writer writer = new StringWriter();
        JavadocStorageManager manager = new JavadocStorageManagerYaml();
        manager.save(map, writer);
        assertEquals("key: |+\n    value\n\n", writer.toString());
    }
    
    @Test
    public void testSave_02() throws Exception {
        Map<String, String> map = new HashMap();
        map.put("key", "first line\nsecond line");
        Writer writer = new StringWriter();
        JavadocStorageManager manager = new JavadocStorageManagerYaml();
        manager.save(map, writer);
        assertEquals("key: |+\n    first line\n    second line\n\n", writer.toString());
    }
    
    @Test
    public void testSave_03() throws Exception {
        Map<String, String> map = new HashMap();
        map.put("key", "first line\n  second line");
        Writer writer = new StringWriter();
        JavadocStorageManager manager = new JavadocStorageManagerYaml();
        manager.save(map, writer);
        assertEquals("key: |+\n    first line\n      second line\n\n", writer.toString());
    }
    
    @Test
    public void testSave_04() throws Exception {
        Map<String, String> map = new HashMap();
        map.put("key1", "first line\n  second line");
        map.put("key2", "value2");
        Writer writer = new StringWriter();
        JavadocStorageManager manager = new JavadocStorageManagerYaml();
        manager.save(map, writer);
        assertEquals("key1: |+\n    first line\n      second line\n\nkey2: |+\n    value2\n\n", writer.toString());
    }
    
    @Test
    public void testLoad_01() throws Exception {
        Map<String, String> data = new HashMap();
        data.put("mypy.nodes.Argument", "A single argument in a FuncItem.");
        data.put("mypy.nodes.AssignmentStmt", "Assignment statement.\nMultiple lines of text.");
        
        Writer writer = new StringWriter();
        JavadocStorageManager manager = new JavadocStorageManagerYaml();
        manager.save(data, writer);
       
        InputStream inputStream = new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8));
        Map<String, String> loadedData = manager.load(inputStream);
        assertEquals(data, loadedData);
    }    
}
