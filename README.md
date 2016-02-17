# JSON Partial Plugin
---

The JSON Partial plugin parses a source JSON document replacing all partial definitions
with the content found by recursively reading the target partial file and selecting the
value at the configured path. The parsed object is then serialized to a target output file.

This plugin is licensed under the [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) license.

## Partial Definitions

Partial definitions take one of two forms, a simple string indicating the relative path to
the partial from some root folder or an object containing the relative path to the partial
and an optional nested path to the target object within the partial for replacement in the
parent document.

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
