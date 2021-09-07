package com.zachary_moore.graphql;

import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.parser.Parser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.*;

public class GraphQLImportParserShould {

    private static final String QUERY_WITH_SIMPLE_EXPORT = "query A {\n" +
            "    Tweet(id: 2) {\n" +
            "        ...simpleTweet\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "fragment simpleTweet on Tweet @export {\n" +
            "    id\n" +
            "    body\n" +
            "    Author {\n" +
            "        username\n" +
            "    }\n" +
            "}";

    public static final String QUERY_WITH_SIMPLE_IMPORT =
            "\n" +
                    "query B {\n" +
                    "    Tweet(id: 2) {\n" +
                    "        ...simpleTweet @import(from: \"query1\")\n" +
                    "    }\n" +
                    "}";

    public static final String QUERY_WITHOUT_IMPORT = "query B {\n" +
            "    Tweet(id: 2) {\n" +
            "        ...idFrag\n" +
            "    }\n" +
            "}";

    private static final String QUERY_WITH_FRAGMENT_AND_NO_EXPORT = "query A {\n" +
            "    Tweet(id: 2) {\n" +
            "        ...simpleTweet\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "fragment simpleTweet on Tweet {\n" +
            "    id\n" +
            "    body\n" +
            "    Author {\n" +
            "        username\n" +
            "    }\n" +
            "}";

    private static final String FRAGMENT_ON_USER =
            "fragment author on User @export {\n" +
                    "    first_name\n" +
                    "}";

    private static final String FRAGMENT_ON_TWEET_REFERENCING_FRAGMENT =
            "\n" +
                    "fragment fullTweet on Tweet @export {\n" +
                    "    id\n" +
                    "    body\n" +
                    "    date\n" +
                    "    Author {\n" +
                    "        ...author @import(from: \"userFragment\")\n" +
                    "    }\n" +
                    "}";

    private static final String QUERY_WITH_FRAGMENT_IMPORT_REFERENCING_FRAGMENT =
            "\n" +
                    "query D {\n" +
                    "    Tweet(id: 2) {\n" +
                    "        ...fullTweet @import(from: \"tweetFragment\")\n" +
                    "    }\n" +
                    "}";

    private static final String SIMPLE_FRAGMENT_EXPORT_WITHOUT_QUERY =
            "fragment simpleTweet on Tweet @export {\n" +
                    "    id\n" +
                    "    body\n" +
                    "    Author {\n" +
                    "        username\n" +
                    "    }\n" +
                    "}";

    private static final String QUERY_WITH_SIMPLE_IMPORT_USED_MULTIPLE_TIMES =
            "\n" +
            "query B {\n" +
            "    Tweet(id: 2) {\n" +
            "        ...aFragment\n" +
            "        ...bFragment\n" +
            "    }\n" +
            "}\n" +
            "fragment aFragment on Tweet{\n" +
            "    ...simpleTweet @import(from: \"query1\")\n" +
            "}\n"  +
            "fragment bFragment on Tweet{\n" +
            "    ...simpleTweet @import(from: \"query1\")\n" +
            "}\n";

    private static final String QUERY_WITH_FRAGMENT_CYCLE_1 =
            "query A {\n" +
                    "    Tweet(id: 2) {\n" +
                    "        ...aFragment\n" +
                    "    }\n" +
                    "}\n" +
                    "\n" +
                    "fragment aFragment on Tweet @export {\n" +
                    "    ...bFragment @import(from: \"query1\")\n" +
                    "}";

    private static final String FRAGMENT_WITH_CYCLE =
            "fragment bFragment on Tweet @export {\n" +
                    "    ...aFragment @import(from: \"query2\")\n" +
                    "}";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void makeSimpleImport() throws Exception {
        File query1 = folder.getRoot().toPath().resolve("query1.graphql").toFile();
        File query2 = folder.getRoot().toPath().resolve("query2.graphql").toFile();

        writeTo(query1.getPath(), QUERY_WITH_SIMPLE_EXPORT);
        writeTo(query2.getPath(), QUERY_WITH_SIMPLE_IMPORT);

        Map<String, Document> finalDocuments = getFinalDocuments(folder.getRoot().getPath());
        Document updatedQuery1 = finalDocuments.get(query1.getPath());
        FragmentDefinition exportedFragment = null;
        for (FragmentDefinition fragmentDefinition : updatedQuery1.getDefinitionsOfType(FragmentDefinition.class)) {
            if (fragmentDefinition.getName().equals("simpleTweet")) {
                exportedFragment = fragmentDefinition;
            }
        }

        assertNotNull(exportedFragment);
        Document updatedQuery2 = finalDocuments.get(query2.getPath());

        boolean hasSeenFragment = false;
        for (FragmentDefinition fragmentDefinition : updatedQuery2.getDefinitionsOfType(FragmentDefinition.class)) {
            if (fragmentDefinition.isEqualTo(exportedFragment)) {
                hasSeenFragment = true;
                break;
            }
        }

        assertTrue(hasSeenFragment);
    }

    @Test(expected = Exception.class)
    public void throwWhenNoImportForFragment() {
        File query1 = folder.getRoot().toPath().resolve("query1.graphql").toFile();
        File query2 = folder.getRoot().toPath().resolve("query2.graphql").toFile();
        try {
            writeTo(query1.getPath(), QUERY_WITH_SIMPLE_EXPORT);
            writeTo(query2.getPath(), QUERY_WITHOUT_IMPORT);
        } catch (IOException e) {
            fail("IO Exception in loading temp files");
        }

        getFinalDocuments(folder.getRoot().getPath());
        fail("Expected to fail with no import declared");
    }

    @Test(expected = Exception.class)
    public void throwWhenNoExportButProperImport() {
        File query1 = folder.getRoot().toPath().resolve("query1.graphql").toFile();
        File query2 = folder.getRoot().toPath().resolve("query2.graphql").toFile();
        try {
            writeTo(query1.getPath(), QUERY_WITH_SIMPLE_IMPORT);
            writeTo(query2.getPath(), QUERY_WITH_FRAGMENT_AND_NO_EXPORT);
        } catch (IOException e) {
            fail("IO Exception in loading temp files");
        }

        getFinalDocuments(folder.getRoot().getPath());
        fail("Expected to fail with import declared but no export to connect");
    }

    @Test
    public void resolveImportsReferencingImports() throws Exception {
        File userFragment = folder.getRoot().toPath().resolve("userFragment.graphql").toFile();
        File tweetFragment = folder.getRoot().toPath().resolve("tweetFragment.graphql").toFile();
        File tweetQuery = folder.getRoot().toPath().resolve("tweetQuery.graphql").toFile();

        writeTo(userFragment.getPath(), FRAGMENT_ON_USER);
        writeTo(tweetFragment.getPath(), FRAGMENT_ON_TWEET_REFERENCING_FRAGMENT);
        writeTo(tweetQuery.getPath(), QUERY_WITH_FRAGMENT_IMPORT_REFERENCING_FRAGMENT);

        Document userFragmentDocument = Parser.parse(FileUtils.readFileToString(userFragment, StandardCharsets.UTF_8));
        Document tweetFragmentDocument = Parser.parse(FileUtils.readFileToString(tweetFragment, StandardCharsets.UTF_8));
        FragmentDefinition userFragmentDefinition = null;
        for (FragmentDefinition fragmentDefinition : userFragmentDocument.getDefinitionsOfType(FragmentDefinition.class)) {
            if (fragmentDefinition.getName().equals("author")) {
                userFragmentDefinition = fragmentDefinition;
                break;
            }
        }

        assertNotNull(userFragmentDefinition);

        FragmentDefinition tweetFragmentDefiniton = null;
        for (FragmentDefinition fragmentDefinition : tweetFragmentDocument.getDefinitionsOfType(FragmentDefinition.class)) {
            if (fragmentDefinition.getName().equals("fullTweet")) {
                tweetFragmentDefiniton = fragmentDefinition;
                break;
            }
        }

        assertNotNull(tweetFragmentDefiniton);

        Map<String, Document> finalDocuments = getFinalDocuments(folder.getRoot().getPath());
        assertFalse(finalDocuments.containsKey(userFragment.getPath()));
        assertFalse(finalDocuments.containsKey(tweetFragment.getPath()));
        assertTrue(finalDocuments.containsKey(tweetQuery.getPath()));

        boolean hasSeenTweet = false;
        boolean hasSeenUser = false;
        for (FragmentDefinition fragmentDefinition : finalDocuments.get(tweetQuery.getPath()).getDefinitionsOfType(FragmentDefinition.class)) {
            if (fragmentDefinition.isEqualTo(tweetFragmentDefiniton)) {
                hasSeenTweet = true;
            }

            if (fragmentDefinition.isEqualTo(userFragmentDefinition)) {
                hasSeenUser = true;
            }
        }

        assertTrue(hasSeenTweet);
        assertTrue(hasSeenUser);
    }

    @Test
    public void removeFragmentOnlyFilesInGeneration() throws IOException {
        File query1 = folder.getRoot().toPath().resolve("query1.graphql").toFile();
        File query2 = folder.getRoot().toPath().resolve("query2.graphql").toFile();

        writeTo(query1.getPath(), SIMPLE_FRAGMENT_EXPORT_WITHOUT_QUERY);
        writeTo(query2.getPath(), QUERY_WITH_SIMPLE_IMPORT);

        Map<String, Document> finalDocuments = getFinalDocuments(folder.getRoot().getPath());
        // check we didn't keep the fragment file
        assertEquals(1, finalDocuments.size());
    }

    @Test
    public void notPullInDuplicateImports() throws Exception {
        File query1 = folder.getRoot().toPath().resolve("query1.graphql").toFile();
        File query2 = folder.getRoot().toPath().resolve("query2.graphql").toFile();

        writeTo(query1.getPath(), QUERY_WITH_SIMPLE_EXPORT);
        writeTo(query2.getPath(), QUERY_WITH_SIMPLE_IMPORT_USED_MULTIPLE_TIMES);

        Map<String, Document> finalDocuments = getFinalDocuments(folder.getRoot().getPath());
        Document updatedQuery1 = finalDocuments.get(query2.getPath());
        // Expect 2 local definitions and a single pulled in definition
        assertEquals(3, updatedQuery1.getDefinitionsOfType(FragmentDefinition.class).size());
    }

    @Test(expected = Exception.class)
    public void throwWithFragmentCycle() throws Exception {
        File query1 = folder.getRoot().toPath().resolve("query1.graphql").toFile();
        File query2 = folder.getRoot().toPath().resolve("query2.graphql").toFile();

        writeTo(query1.getPath(), FRAGMENT_WITH_CYCLE);
        writeTo(query2.getPath(), QUERY_WITH_FRAGMENT_CYCLE_1);

        getFinalDocuments(folder.getRoot().getPath());
    }

    public void writeTo(String path, String content) throws IOException {
        Path target = Paths.get(path);
        if (Files.exists(target)) {
            throw new IOException("file already exists");
        }
        Files.copy(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), target);
    }

    private Map<String, Document> getFinalDocuments(String rootPath) {
        File rootDirectory = Paths.get(rootPath).toFile();
        Collection<File> files = FileUtils.listFiles(
                rootDirectory,
                new RegexFileFilter("^(.*?)"),
                DirectoryFileFilter.DIRECTORY
        );
        return DocumentReader.generateDocuments(new ArrayList<>(files), rootPath);
    }
}
