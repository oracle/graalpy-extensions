package org.graalvm.python.javainterfacegen;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import static org.junit.Assert.fail;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.ContextFactory;
import org.graalvm.python.javainterfacegen.Main;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.configuration.ConfigurationLoader;
import org.graalvm.python.javainterfacegen.configuration.DefaultConfigurationLoader;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.MypyHook;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import static org.junit.Assert.assertTrue;

public class GeneratorTestBase {

    private static Context context = null;

    protected static final String GOLDEN_FILE_EXT = ".golden";

    protected boolean printDifferenceDetails = false;
    protected int printOnlyDiffIfLenIsBigger = 1000;

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

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected GeneratorTestBase() {

    }

    public static Context getContext() {
        return ContextFactory.getContext();
    }

    @Before
    public void setUp() {
        getContext();
    }

    public static void closeContext() {
        context.close();
        context = null;
    }

    protected String getTestMethodName() {
        return testInfo.getTestMethodName();
    }

    protected String getTestClassName() {
        return testInfo.getTestClassName();
    }

    protected Configuration createDefaultConfiguration() throws Exception {
        ConfigurationLoader defaultLoader = new DefaultConfigurationLoader();
        Map<String, Object> defaultConfiguration = defaultLoader.loadConfiguration();
        defaultConfiguration.put(Configuration.P_TARGET_FOLDER, tempFolder.getRoot().getAbsolutePath() + "/src");
        defaultConfiguration.put(Configuration.P_MYPY_CACHE_FOLDER, tempFolder.getRoot().getAbsolutePath() + "/mypy");
        Map<String, Object> files = new HashMap();

        File testFile = getTestFileFromTestAndTestMethod();
        String testPath = testFile.toPath().toString();//String.format("%s/%s/%s", getTestFilesDir(), getTestClassName(), getTestMethodName());
        files.put(testPath, new HashMap());
        defaultConfiguration.put(Configuration.P_FILES, files);

        File testFolder = testFile.isFile() ? testFile.getParentFile() : testFile;

        String generatorCacheDirPath = testFolder.getPath() + "/generator-cache";
        File generatorCacheDir = new File(generatorCacheDirPath);
        if (!generatorCacheDir.exists()) {
            generatorCacheDir.mkdir();
        }
        System.out.println("Cache directory path: " + generatorCacheDir.getAbsolutePath());
        defaultConfiguration.put(Configuration.P_GENERATOR_CACHE_FOLDER, generatorCacheDir.getAbsolutePath());

        File javadocFolder = new File(testFolder.getPath() + "/javadoc");
        if (!javadocFolder.exists()) {
            javadocFolder.mkdir();
        }
        defaultConfiguration.put(Configuration.P_JAVADOC_FOLDER, javadocFolder.getAbsolutePath());

        Configuration conf = new Configuration(defaultLoader);
        conf.load();
        return conf;
    }

    public void checkGeneratorFromFile(Context context, Configuration config) throws Exception {
        System.out.println("Tested Files: ");
        String[] files = config.getFiles();
        for (String file : files) {
            System.out.println("   " + file);
        }
        String targetFolder = config.getTargetFolder();
        System.out.println("Target Folder: " + targetFolder);

        String cacheFolder = (String) config.getGlobalProperties().get(Configuration.P_GENERATOR_CACHE_FOLDER);
        System.out.println("Cache folder: " + cacheFolder);
        File file = getTestFileFromTestAndTestMethod();
        if (file.isFile()) {
            file = file.getParentFile();
        }
        Path configFolder = file.toPath();
        config.setReferenceFolder(configFolder);
        String[] whatProcess = Main.resolvePaths(config, configFolder);

        TypeManager.get().registerTypes(config);

        String configurationName = getTestMethodName();
        MypyHook mypyHook = MypyHook.fromContext(context);
        mypyHook.set_mypy_cache_folder(config.getPath(Configuration.P_MYPY_CACHE_FOLDER).toAbsolutePath().toString());

        Map<String, MypyFile> moduleDict;
        Path pathCacheFile = config.getPath(Configuration.P_GENERATOR_CACHE_FOLDER)
                .resolve(configurationName + ".json");
        if (!Files.exists(pathCacheFile)) {
            moduleDict = Main.parseWithMypy(context, whatProcess, configurationName, pathCacheFile, false);
            Path builtinsFile = getSharedGeneratorCacheFolder().toPath().resolve("builtins.zip");
            if (!Files.exists(builtinsFile)) {
                saveMypyFileToZip(builtinsFile.toString(), mypyHook, moduleDict.get("builtins"));
            }

            mypyHook.serialize_mypyfile(moduleDict.get("class01").getValue(), cacheFolder + "/" + getTestMethodName() + ".json");
        } else {

            long start = System.currentTimeMillis();
            Map<String, Value> data = getBuiltins(mypyHook);
//            Map<String, Value> data = mypyHook.load_result(cacheFolder + "/builtins.json");
            long end = System.currentTimeMillis();
            System.out.println(String.format("Deserializing of %s took %d ms", "builtins", end - start));
            start = System.currentTimeMillis();
            data = mypyHook.load_result(cacheFolder + "/" + getTestMethodName() + ".json", data);
            moduleDict = Main.convert(data);
            end = System.currentTimeMillis();
            System.out.println(String.format("Deserializing of %s took %d ms", getTestMethodName(), end - start));
            System.out.println("module " + moduleDict);
        }
        
        Map<String, MypyFile> data2 = new HashMap();
        for (Map.Entry<String, MypyFile> entry : moduleDict.entrySet()) {
            String key = entry.getKey();
            MypyFile value = entry.getValue();
            data2.put(value.getPath(), value);
        }
        Main.generateInterfaces(config, moduleDict, mypyHook, whatProcess);
        System.out.println("Hotovo");
//        try {
////            Main.process(config, getTestMethodName(), Path.of(cacheFolder));
////            Main.main(files);
////            MypyHook mypyHook = MypyHook.fromContext(context);
////            TransformerVisitor generator = new TransformerVisitor(config);
////            DefaultImplementationVisitor implVisitor = new DefaultImplementationVisitor(config);
////            TypeManager.get().init();
////            long start;
////            long end;
////            String configurationName = getTestMethodName();
////            Path generatorCacheFolder = Path.of(cacheFolder);
////            Path pathGeneratorCacheFile = generatorCacheFolder.resolve(generatorCacheFolder).resolve(configurationName + ".json");
////            System.out.println("PathGeneratorCacheFile: " + pathGeneratorCacheFile.toAbsolutePath());
////            Map<String, Value> dict;
////            if (!Files.exists(pathGeneratorCacheFile)) {
////                dict = mypyHook.serialize_result(List.of(whatProcess), pathGeneratorCacheFile.toAbsolutePath().toString());
////            } else {
////                dict = 
////            }
//            
////            for (int i = 0; i < whatProcess.length; i++) {
////                start = System.currentTimeMillis();
//////            Path path = Paths.get(whatProcess[i], "ast");
////
////                String fileName = whatProcess[i].substring(whatProcess[i].lastIndexOf('/') + 1) + ".json";
////                Path path = Paths.get(fileName);
////
////                MypyFile mypyFile = null;
////
////                if (!Files.exists(path)) {
////                    System.out.println("####### serializing");
////
//////                    mypyFile = mypyHook.serializeResult(whatProcess[i], fileName);
////                    System.out.println("##############Serialization done " + fileName);
////                } else {
//////                    mypyFile = mypyHook.deserializeResult(fileName, fileName.substring(0, fileName.indexOf('.')));
////                }
////                generator.visit(mypyFile);
////                implVisitor.visit(mypyFile);
////
////                end = System.currentTimeMillis();
////                System.out.println(String.format("Processing %s took %d seconds", whatProcess[i], (end - start) / 1000));
////            }
////            TypeManager.get().handleUnresolved();
//        } catch (Exception e) {
//            System.out.println("Error during serialization: " + e.toString());
//            System.out.println(e.getMessage());
//            e.printStackTrace();
//        }
//        File goldenDir = getGoldenFolderForTest();
//        System.out.println("golden: " + goldenDir.getAbsolutePath());
//        if (goldenDir.listFiles().length == 0) {
//            System.out.println("Golden directory is empty -> generated test are coppied to golden directory.");
//            copyDirectory(Paths.get(targetFolder), goldenDir.toPath());
//        }
//        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        //String source = readFile(testFile);
//        SSTNode resultNew = parse(source, "Test", InputType.FILE);
//        String tree = printTreeToString(source, resultNew);
//        File goldenFile = goldenFileNextToTestFile
//                        ? new File(testFile.getParentFile(), getFileName(testFile) + GOLDEN_FILE_EXT)
//                        : getGoldenFile(GOLDEN_FILE_EXT);
//        writeGoldenFileIfMissing(goldenFile, tree);
//        assertDescriptionMatches(tree, goldenFile);
    }
    

    public static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetPath = targetDir.resolve(sourceDir.relativize(dir));
                if (!Files.exists(targetPath)) {
                    Files.createDirectory(targetPath);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, targetDir.resolve(sourceDir.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static String readFile(File f) throws IOException {
        FileReader r = new FileReader(f);
        int fileLen = (int) f.length();
        CharBuffer cb = CharBuffer.allocate(fileLen);
        r.read(cb);
        cb.rewind();
        return cb.toString();
    }

    public File getTestFilesDir() {
        String dataDirPath = "src/test/resources/testData/testFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The test files folder, was not found.", dataDir.exists());
        return dataDir;
    }
    
    public static File getSharedGeneratorCacheFolder() {
        return new File("src/test/resources/testData/generator-cache");
    }

    public File getTestFileFromTestAndTestMethod() {
        File testFilesDir = getTestFilesDir();
        File testDir = new File(testFilesDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        return new File(testDir, getTestMethodName() + ".py");
    }

    public File getGoldenDataDir() {
        String dataDirPath = "src/test/resources/testData/goldenFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The golden files folder, was not found.", dataDir.exists());
        return dataDir;
    }

    public File getGoldenFolderForTest() {
        File goldenDir = getGoldenDataDir();
        File goldenTestDir = new File(goldenDir + "/" + this.getClass().getSimpleName());
        if (!goldenTestDir.exists()) {
            goldenTestDir.mkdir();
        }
        goldenTestDir = new File(goldenTestDir + "/" + getTestMethodName());
        if (!goldenTestDir.exists()) {
            goldenTestDir.mkdir();
        }
        return goldenTestDir;
    }

    public File getGoldenFile(String ext) {
        File goldenDir = getGoldenDataDir();
        File testDir = new File(goldenDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        return new File(testDir, getTestMethodName() + ext);
    }

    protected String getFileName(File file) {
        return file.getName().substring(0, file.getName().lastIndexOf('.'));
    }

    private static String lineSeparator(int number) {
        final String lineSeparator = System.getProperty("line.separator");
        if (number > 1) {
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < number; i++) {
                sb.append(lineSeparator);
            }
            return sb.toString();
        }
        return lineSeparator;
    }

    protected static void writeFileIfMissing(File goldenFile, String contents) throws IOException {
        if (!goldenFile.exists()) {
            try (FileWriter fw = new FileWriter(goldenFile)) {
                fw.write(contents);
            }
        }
    }

    protected static void writeFile(File file, String contents) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(contents);
        }

    }

    private String getContentDifferences(String expected, String actual) {
        StringBuilder sb = new StringBuilder();
        if (printDifferenceDetails || (expected.length() < printOnlyDiffIfLenIsBigger && actual.length() < printOnlyDiffIfLenIsBigger)) {
            sb.append("Expected content is:").append(lineSeparator(2)).append(expected).append(lineSeparator(2)).append("but actual is:").append(lineSeparator(2)).append(actual).append(
                    lineSeparator(2)).append("It differs in the following things:").append(lineSeparator(2));
        } else {
            sb.append("Expected and actual differ in the following things:").append(lineSeparator(2));
        }

        List<String> expectedLines = Arrays.asList(expected.split("\n"));
        List<String> actualLines = Arrays.asList(actual.split("\n"));

        if (expectedLines.size() != actualLines.size()) {
            sb.append("Number of lines: \n\tExpected: ").append(expectedLines.size()).append("\n\tActual: ").append(actualLines.size()).append("\n\n");
        }

        // Appending lines which are missing in expected content and are present in actual content
        boolean noErrorInActual = true;
        for (String actualLine : actualLines) {
            if (!expectedLines.contains(actualLine)) {
                if (noErrorInActual) {
                    sb.append("Actual content contains following lines which are missing in expected content: ").append(lineSeparator(1));
                    noErrorInActual = false;
                }
                sb.append("\t").append(actualLine).append(lineSeparator(1));
            }
        }

        // Appending lines which are missing in actual content and are present in expected content
        boolean noErrorInExpected = true;
        for (String expectedLine : expectedLines) {
            if (!actualLines.contains(expectedLine)) {
                // If at least one line missing in actual content we want to append header line
                if (noErrorInExpected) {
                    sb.append("Expected content contains following lines which are missing in actual content: ").append(lineSeparator(1));
                    noErrorInExpected = false;
                }
                sb.append("\t").append(expectedLine).append(lineSeparator(1));
            }
        }

        // If both values are true it means the content is the same, but some lines are
        // placed on a different line number in actual and expected content
        if (noErrorInActual && noErrorInExpected && expectedLines.size() == actualLines.size()) {
            for (int lineNumber = 0; lineNumber < expectedLines.size(); lineNumber++) {
                String expectedLine = expectedLines.get(lineNumber);
                String actualLine = actualLines.get(lineNumber);

                if (!expectedLine.equals(actualLine)) {
                    sb.append("Line ").append(lineNumber).append(" contains different content than expected: ").append(lineSeparator(1)).append("Expected: \t").append(expectedLine).append(
                            lineSeparator(1)).append("Actual:  \t").append(actualLine).append(lineSeparator(2));

                }
            }
        }

        return sb.toString();
    }

    protected void assertDescriptionMatches(String actual, File goldenFile) throws Exception {
        String expected = readFile(goldenFile);
        assertDescriptionMatches(actual, expected, goldenFile.getName());
    }

    protected void assertDescriptionMatches(String actual, String expected, String someName) {
        final String expectedTrimmed = expected.trim();
        final String actualTrimmed = actual.trim();

        if (expectedTrimmed.equals(actualTrimmed)) {
            // Actual and expected content are equals --> Test passed

        } else {
            // We want to ignore different line separators (like \r\n against \n) because they
            // might be causing failing tests on a different operating systems like Windows :]
            final String expectedUnified = expectedTrimmed.replaceAll("\r", "");
            final String actualUnified = actualTrimmed.replaceAll("\r", "");

            if (expectedUnified.equals(actualUnified)) {
                return; // Only difference is in line separation --> Test passed
            }

            // There are some differences between expected and actual content --> Test failed
            fail("Not matching results: " + (someName == null ? "" : someName) + lineSeparator(2) + getContentDifferences(expectedUnified, actualUnified));
        }
    }

    protected static void saveMypyFileToZip(String zipFilePath, MypyHook mypyHook, MypyFile mypyFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath); 
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            String builtins = mypyHook.serialize_mypyFile_toStr(mypyFile.getValue());
            ByteArrayInputStream bais = new ByteArrayInputStream(builtins.getBytes(StandardCharsets.UTF_8));

            ZipEntry zipEntry = new ZipEntry(mypyFile.getFullname() + ".json");

            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = bais.read(buffer)) != -1) {
                zos.write(buffer, 0, length);
            }

            zos.closeEntry();
        }
    }

    protected static Map<String, Value> loadMypyFileFromZip(String zipFilePath, MypyHook mypyHook, String moduleName) throws IOException {
        String builtins = "";
        try (ZipFile zipFile = new ZipFile(zipFilePath, StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(moduleName + ".json");
            if (entry != null) {
                try (InputStream is = zipFile.getInputStream(entry); 
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    builtins = sb.toString();
                }
            }
        }
        Map<String, Value> data = null;
        if (!builtins.isEmpty()) {
            data = mypyHook.create_mypyfile(builtins);
        }
        return data;
    }
    
    private static Map<String, Value> builtins_module = null;
    
    protected static Map<String, Value> getBuiltins(MypyHook mypyHook) throws IOException {
        if (builtins_module == null) {
            builtins_module = loadMypyFileFromZip(getSharedGeneratorCacheFolder().toPath().resolve("builtins.zip").toString(), mypyHook, "builtins");
        }
        return builtins_module;
    }
    
    
}
