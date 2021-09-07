package com.zachary_moore.graphql;

import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.FragmentDefinition;

import java.util.HashMap;
import java.util.Map;

public class ExportParser {

    private final static String EXPORT_DIRECTIVE_NAME = "export";

    public static Map<String, FragmentDefinition> getExportedFragments(Document document) {
        Map<String, FragmentDefinition> exportedFragments = new HashMap<>();
        for (FragmentDefinition fragmentDefinition : document.getDefinitionsOfType(FragmentDefinition.class)) {
            if (shouldExportFragment(fragmentDefinition)) {
                exportedFragments.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }
        return exportedFragments;
    }

    private static boolean shouldExportFragment(FragmentDefinition fragmentDefinition) {
        for (Directive directive : fragmentDefinition.getDirectives()) {
            if (directive.getName().equals(EXPORT_DIRECTIVE_NAME)) {
                return true;
            }
        }
        return false;
    }
}
