# GraphQLImportDirectives
Utilities to allow GraphQL fragments to be imported across files with graphql-java.

Library similar to [GraphQL Import](https://www.npmjs.com/package/graphql-import) but for use with [GraphQLJava](https://www.graphql-java.com/).

### What
Utility function that takes in a list of Files and a root path. 

We will:
* Take in a list of files and a root path
* Generate expected import paths for each file, replacing path directory slashes with dots
* Convert all Query files to GraphQL documents
* Parse all exported fragments for each query file
* Parse each import for each query file and see if there is an available export
* Re-generate a final version of the GraphQL Document with the fragment inlined
* Return a map of filepaths to final re-generated documents

### How

##### Build Pipeline
Pull in the library to your build pipeline.  
Call `DocumentReader.generateDocuments` with the root path of your query folder as well as all of the queries as files.

##### Schema Directives
Add
```graphql
directive @export on FRAGMENT_DEFINITION
directive @import(from: String!) on FRAGMENT_SPREAD
```

To your main graphQL schema.

##### Query Usage
Assume 2 files.  

File1 -> Path: `root/package1/nested/QueryA.graphql`
With content
```graphql
query A {
    Tweet(id: 2) {
        ...simpleTweet
    }
}

fragment simpleTweet on Tweet @export {
    id
    body
    Author {
        username
    }
}
```

We see that we export the fragment simpleTweet for others to use.

File2 -> Path: `root/package1/QueryB.graphql`
With content
```graphql
query B {
    Tweet(id: 3) {
        ...simpleTweet @import(from: "package1.nested.QueryA")
    }
}
```

With these 2 files we feed to the build pipeline and the result generated file for File2 is
```graphql
query B {
    Tweet(id: 3) {
        ...simpleTweet @import(from: "package1.nested.QueryA")
    }
}

fragment simpleTweet on Tweet @export {
    id
    body
    Author {
        username
    }
}
```
This allows us to look at this document individually and execute it with our import inlined.

### Why

When we build GraphQL queries at scale we often will have common ways for selecting upon certain fields and types. 

If we have common, complicated queries, it is better to allow devs to pull this in and avoid diverging from the source of truth definition for the fragment.

We can do this via allowing all fragments to be used everywhere, however if we do this without any explicit import export we allow every fragment to be accessible and thus begin polluting the global namespace.  

If we also blanket allow fragment sharing it allows potential unknown dependencies that might break things if we start to modify fragments that were being depended on without any explicit opt-in indicator.  

By allowing fragment sharing but also giving explicit directives to do so we:
* Keep the global namespace clean 
* Protect from unkown dependencies (a dev needs to opt-in to sharing a fragment and maintaining it)
* Give immediate information on the fragment spread for where this shared fragment originates

### Shortcomings 
Couple of issues with this approach.
1. By using directives and strings, if we move files it will be a pain to find and replace all imports
2. Ergonomics of marking every fragment spread explicitly vs. say a top level file import for queries is not that great
3. We transitively expose underlying fragments if for example exported fragment A uses non-exported, local fragment B, it will still be shared
4. We don't completely avoid name collisions.  If an imported fragment has dependencies on fragments that collide with the recipient file's fragment definitions, we will have confusing build errors for the dev.