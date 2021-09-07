package com.zachary_moore.graphql;

import graphql.language.*;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentGenerator {

    private static final String IMPORT_DIRECTIVE_NAME = "import";
    private static final String IMPORT_DIRECTIVE_FROM_ARGUMENT = "from";

    // Map of file import to Map of fragment name to all dependent fragments
    // We hold this to lazily compute dependent fragments of imported fragments
    private final Map<String, Map<String, Set<FragmentDefinition>>> resolvedDefinitions = new HashMap<>();

    // A map or imports to fragment name to fragment definition to be used for internal fragment lookups
    private final Map<String, Map<String, FragmentDefinition>> importToAllFragmentDefinitions;

    public DocumentGenerator(Map<String, Document> importToDocument) {
        this.importToAllFragmentDefinitions = importToDocument.entrySet()
                .stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            List<FragmentDefinition> allDefinitions = entry.getValue().getDefinitionsOfType(FragmentDefinition.class);
                            return allDefinitions.stream().collect(Collectors.toMap(
                                    FragmentDefinition::getName,
                                    Function.identity()
                            ));
                        }));
    }

    public Document getFinalDocument(Document original,
                                     Map<String, Map<String, FragmentDefinition>> allFragmentExports) {
        Set<Definition> allDefinitions = new HashSet<>(original.getDefinitions());
        Map<String, FragmentDefinition> inDocumentDefinitions = original.getDefinitionsOfType(FragmentDefinition.class)
                .stream().collect(Collectors.toMap(FragmentDefinition::getName, Function.identity()));

        NodeVisitorStub fragmentSpreadVisitor = new NodeVisitorStub() {
            @Override
            public TraversalControl visitFragmentSpread(FragmentSpread fragmentSpread, TraverserContext<Node> context) {
                Directive importDirective = getImportDirective(fragmentSpread);
                if (importDirective == null) {
                    if (!inDocumentDefinitions.containsKey(fragmentSpread.getName())) {
                        throw new IllegalStateException("No import for fragment and not defined in file");
                    }
                    return TraversalControl.CONTINUE;
                }

                String importPath = ((StringValue) importDirective.getArgument(IMPORT_DIRECTIVE_FROM_ARGUMENT).getValue()).getValue();
                if (importPath == null) {
                    throw new IllegalStateException("Import directive does not have required argument from:");
                }

                if (!allFragmentExports.containsKey(importPath)) {
                    throw new IllegalStateException("Trying to import fragment that is not exported");
                }

                Map<String, FragmentDefinition> fragmentExports = allFragmentExports.get(importPath);
                if (!fragmentExports.containsKey(fragmentSpread.getName())) {
                    throw new IllegalStateException("Trying to import fragment that is not exported");
                }

                allDefinitions.addAll(
                        resolveFragments(
                                importPath,
                                fragmentExports.get(fragmentSpread.getName()),
                                allFragmentExports,
                                new HashSet<>(Collections.singletonList(fragmentSpread.getName()))));
                return TraversalControl.CONTINUE;
            }
        };

        AstTransformer astTransformer = new AstTransformer();
        astTransformer.transform(original, fragmentSpreadVisitor);

        return Document.newDocument().definitions(new ArrayList<>(allDefinitions)).build();
    }

    private List<FragmentDefinition> resolveFragments(String importPath,
                                                      FragmentDefinition fragmentDefinition,
                                                      Map<String, Map<String, FragmentDefinition>> allFragmentExports,
                                                      Set<String> alreadyVisitedFragments) {
        if (!resolvedDefinitions.containsKey(importPath)) {
            resolvedDefinitions.put(importPath, new HashMap<>());
        }

        if (resolvedDefinitions.get(importPath).containsKey(fragmentDefinition.getName())) {
            return new ArrayList<>(resolvedDefinitions.get(importPath).get(fragmentDefinition.getName()));
        }

        Set<FragmentDefinition> dependentDefinitions = new HashSet<>(Collections.singletonList(fragmentDefinition));
        NodeVisitorStub fragmentSpreadVisitor = new NodeVisitorStub() {
            @Override
            public TraversalControl visitFragmentSpread(FragmentSpread fragmentSpread, TraverserContext<Node> context) {
                Directive importDirective = getImportDirective(fragmentSpread);
                if (importDirective == null) {
                    Map<String, FragmentDefinition> definitionsForFile = importToAllFragmentDefinitions.get(importPath);
                    if (definitionsForFile == null) {
                        throw new IllegalStateException("Trying to get fragment reference but not found in documents");
                    }

                    FragmentDefinition referencedDefinition = definitionsForFile.get(fragmentSpread.getName());
                    if (referencedDefinition == null) {
                        throw new IllegalStateException("Trying to get fragment reference but not found in documents");
                    }


                    dependentDefinitions.add(referencedDefinition);
                    return TraversalControl.CONTINUE;
                }

                String importPath = ((StringValue) importDirective.getArgument(IMPORT_DIRECTIVE_FROM_ARGUMENT).getValue()).getValue();
                if (importPath == null) {
                    throw new IllegalStateException("Import directive does not have required argument from:");
                }

                if (!allFragmentExports.containsKey(importPath)) {
                    throw new IllegalStateException("Trying to import fragment that is not exported");
                }

                Map<String, FragmentDefinition> fragmentExports = allFragmentExports.get(importPath);
                if (!fragmentExports.containsKey(fragmentSpread.getName())) {
                    throw new IllegalStateException("Trying to import fragment that is not exported");
                }

                if (alreadyVisitedFragments.contains(fragmentSpread.getName())) {
                    throw new IllegalStateException("Cycle in fragment resolution");
                }
                alreadyVisitedFragments.add(fragmentSpread.getName());
                dependentDefinitions.addAll(resolveFragments(importPath,
                        fragmentExports.get(fragmentSpread.getName()), allFragmentExports, alreadyVisitedFragments));
                return TraversalControl.CONTINUE;
            }
        };
        AstTransformer astTransformer = new AstTransformer();
        astTransformer.transform(fragmentDefinition, fragmentSpreadVisitor);

        resolvedDefinitions.get(importPath).put(fragmentDefinition.getName(), dependentDefinitions);
        alreadyVisitedFragments.clear();
        return new ArrayList<>(dependentDefinitions);
    }

    private static Directive getImportDirective(FragmentSpread fragmentSpread) {
        for (Directive directive : fragmentSpread.getDirectives()) {
            if (directive.getName().equals(IMPORT_DIRECTIVE_NAME)) {
                return directive;
            }
        }
        return null;
    }
}
