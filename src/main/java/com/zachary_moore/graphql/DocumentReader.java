package com.zachary_moore.graphql;

import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentReader {

    private static final String GRAPHQL_FILE_EXTENSION = ".graphql";

    public static Map<String, Document> generateDocuments(List<File> files,
                                                          String rootPath) throws IllegalStateException {
        Map<File, Document> fileToDocument = files.stream()
                .collect(Collectors.toMap(Function.identity(),
                        file -> {
                            try {
                                return Parser.parse(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                throw new IllegalStateException("Couldn't create file map");
                            }
                        }));

        Map<String, Document> importToDocument = fileToDocument.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> getFileImportFromFile(entry.getKey(), rootPath),
                        Map.Entry::getValue));

        Map<String, Map<String, FragmentDefinition>> fileImportToExportedFragments = importToDocument.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ExportParser.getExportedFragments(entry.getValue())))
                .entrySet().stream().filter(entry -> entry.getValue().size() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<File, Document> finalDocuments = fileToDocument.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new DocumentGenerator(importToDocument).getFinalDocument(entry.getValue(), fileImportToExportedFragments)))
                .entrySet().stream().filter(entry -> entry.getValue().getDefinitionsOfType(OperationDefinition.class).size() != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return finalDocuments.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().getPath(),
                Map.Entry::getValue));
    }

    private static String getFileImportFromFile(File file,
                                                String rootPath) {
        List<String> filePathSplitFromRoot = Arrays.stream(
                file.getPath()
                        .split(rootPath)[1]
                        .split("/")
        ).filter(string -> !string.isEmpty()).collect(Collectors.toList());

        if (filePathSplitFromRoot.size() > 0) {
            StringBuilder finalImportBuilder = new StringBuilder();
            for (int i = 0; i < filePathSplitFromRoot.size(); i++) {
                finalImportBuilder.append(filePathSplitFromRoot.get(i));
                if (i != filePathSplitFromRoot.size() - 1) {
                    finalImportBuilder.append(".");
                }
            }
            return finalImportBuilder.toString().split(GRAPHQL_FILE_EXTENSION)[0];
        }

        throw new IllegalStateException("Could not generate import from file");
    }
}
