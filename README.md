# JSON Partial Plugin
---

The JSON Partial plugin parses a source JSON document replacing all partial definitions
with the content found by recursively reading the target partial file and selecting the
value at the configured path. The parsed object is then serialized to a target output file.

This plugin is licensed under the [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) license.

## Partial Definitions

When encountered, a partial definition is parsed and loaded from the target file and all properties
of the object at the specified property path are merged into the object containing the `##include`.
Properties defined in the containing object will override any conflicting properties from the partial.

Partial definitions take one of two forms, a simple string indicating the relative path to
the partial from some root folder or an object containing the relative path to the partial
and an optional nested path to the target object within the partial for replacement in the
parent document. Multiple partials may be included in an object by providing an array of
definitions as the value of the `##include` attribute.

### Simple Partial

```
{
    "##include": "path/to/my/partial.json"
}
```

### Full Partial
```
{
    "##include": {
        "partial": "path/to/my/partial.json",
        "path": "some.nested.property"
    }
}
```

### Multiple Partials
```
{
    "##include": [
        "path/to/my/partial.json",
        {
            "partial": "path/to/another/partial.json",
            "path": "some.nested.property"
        }
    ]
}
```
