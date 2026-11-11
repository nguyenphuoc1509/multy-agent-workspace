package com.workspace.document.application;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Structural check of an API_CONTRACT document (brief section 6: "must parse as valid OpenAPI", errors with line
 * numbers). It parses YAML (JSON is a subset) without constructing objects and verifies the parts of the
 * OpenAPI 3.0/3.1 structure that catch real mistakes: syntax, duplicate keys, version, info, paths shape,
 * required responses (3.0), unique operationIds, unknown path-item fields and unresolved local $refs.
 *
 * <p>It is NOT a full JSON-Schema validation of the OpenAPI specification (schema objects, parameter rules and
 * so on are not inspected). Remote $refs are rejected on purpose: the backend never fetches URLs.
 */
final class ContractValidator {

    record Issue(int line, String message) {}

    private static final Pattern VERSION = Pattern.compile("^3\\.[01]\\.\\d+(-.+)?$");
    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final Set<String> OTHER_PATH_ITEM_FIELDS =
            Set.of("$ref", "summary", "description", "servers", "parameters");
    private static final int MAX_ISSUES = 50;

    private record Field(Node key, Node value) {}

    private final List<Issue> issues = new ArrayList<>();

    private ContractValidator() {}

    static List<Issue> validate(String content) {
        ContractValidator v = new ContractValidator();
        v.run(content);
        return v.issues;
    }

    private void run(String content) {
        Node root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(50);
            options.setCodePointLimit(2_000_000);
            root = new Yaml(new SafeConstructor(options)).compose(new StringReader(content));
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark() != null ? e.getProblemMark() : e.getContextMark();
            String problem = e.getProblem() != null ? e.getProblem() : "invalid YAML";
            add(mark == null ? 1 : mark.getLine() + 1, "Invalid YAML: " + problem);
            return;
        } catch (RuntimeException e) {
            add(1, "Invalid YAML: " + (e.getMessage() == null ? e.getClass().getSimpleName() : firstLine(e.getMessage())));
            return;
        }
        if (!(root instanceof MappingNode top)) {
            add(root == null ? 1 : line(root), "The document must be an object (mapping) at the top level");
            return;
        }

        Map<String, Field> fields = fields(top);
        String version = null;
        Field openapi = fields.get("openapi");
        if (openapi == null) {
            add(line(top), "Missing required field 'openapi'");
        } else if (!(openapi.value() instanceof ScalarNode s) || !VERSION.matcher(s.getValue()).matches()) {
            add(line(openapi.value()), "'openapi' must be a 3.0.x or 3.1.x version string, for example 3.1.0");
        } else {
            version = ((ScalarNode) openapi.value()).getValue();
        }
        boolean v30 = version != null && version.startsWith("3.0");

        checkInfo(top, fields.get("info"));

        Field paths = fields.get("paths");
        if (paths == null) {
            if (v30) {
                add(line(top), "Missing required field 'paths'");
            } else if (!fields.containsKey("components") && !fields.containsKey("webhooks")) {
                add(line(top), "At least one of 'paths', 'components' or 'webhooks' is required");
            }
        } else if (!(paths.value() instanceof MappingNode pm)) {
            add(line(paths.value()), "'paths' must be an object");
        } else {
            checkPaths(pm, v30);
        }

        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        checkRefs(top, top, seen);
    }

    private void checkInfo(MappingNode top, Field info) {
        if (info == null) {
            add(line(top), "Missing required field 'info'");
            return;
        }
        if (!(info.value() instanceof MappingNode im)) {
            add(line(info.value()), "'info' must be an object");
            return;
        }
        Map<String, Field> f = fields(im);
        requireText(im, f, "title");
        requireText(im, f, "version");
    }

    private void requireText(MappingNode parent, Map<String, Field> f, String name) {
        Field field = f.get(name);
        if (field == null) {
            add(line(parent), "Missing required field 'info." + name + "'");
        } else if (!(field.value() instanceof ScalarNode s) || s.getValue().isBlank()) {
            add(line(field.value()), "'info." + name + "' must be a non-empty value");
        }
    }

    private void checkPaths(MappingNode paths, boolean v30) {
        Map<String, Node> operationIds = new LinkedHashMap<>();
        for (Field p : fields(paths).values()) {
            String path = scalar(p.key());
            if (path == null || !path.startsWith("/")) {
                add(line(p.key()), "Path '" + path + "' must start with '/'");
            }
            if (!(p.value() instanceof MappingNode item)) {
                add(line(p.value()), "Path item '" + path + "' must be an object");
                continue;
            }
            for (Field f : fields(item).values()) {
                String name = scalar(f.key());
                if (name == null) continue;
                if (HTTP_METHODS.contains(name)) {
                    checkOperation(path, name, f.value(), v30, operationIds);
                } else if (!OTHER_PATH_ITEM_FIELDS.contains(name) && !name.startsWith("x-")) {
                    add(line(f.key()), "Unknown field '" + name + "' in path item '" + path + "'");
                }
            }
        }
    }

    private void checkOperation(String path, String method, Node op, boolean v30, Map<String, Node> operationIds) {
        String label = method.toUpperCase(java.util.Locale.ROOT) + " " + path;
        if (!(op instanceof MappingNode om)) {
            add(line(op), "Operation '" + label + "' must be an object");
            return;
        }
        Map<String, Field> f = fields(om);
        Field responses = f.get("responses");
        if (responses == null) {
            if (v30) add(line(om), "Operation '" + label + "' has no 'responses'");
        } else if (!(responses.value() instanceof MappingNode rm) || rm.getValue().isEmpty()) {
            add(line(responses.value()), "'responses' of '" + label + "' must be a non-empty object");
        }
        Field id = f.get("operationId");
        if (id != null) {
            String value = scalar(id.value());
            if (value == null || value.isBlank()) {
                add(line(id.value()), "'operationId' of '" + label + "' must be a non-empty string");
            } else if (operationIds.putIfAbsent(value, id.value()) != null) {
                add(line(id.value()), "Duplicate operationId '" + value + "' (first used on line "
                        + line(operationIds.get(value)) + ")");
            }
        }
    }

    /** Local $refs must resolve; remote ones are refused (the backend never fetches URLs). */
    private void checkRefs(Node node, Node root, Set<Node> seen) {
        if (!seen.add(node)) return; // anchors/aliases can share nodes, even cyclically
        if (node instanceof MappingNode m) {
            for (NodeTuple t : m.getValue()) {
                if (t.getKeyNode() instanceof ScalarNode k && "$ref".equals(k.getValue())
                        && t.getValueNode() instanceof ScalarNode v) {
                    String ref = v.getValue();
                    if (!ref.startsWith("#")) {
                        add(line(v), "Only local references ('#/...') are supported: '" + ref + "'");
                    } else if (resolve(root, ref) == null) {
                        add(line(v), "Unresolved reference '" + ref + "'");
                    }
                }
                checkRefs(t.getValueNode(), root, seen);
            }
        } else if (node instanceof SequenceNode s) {
            for (Node item : s.getValue()) checkRefs(item, root, seen);
        }
    }

    private static Node resolve(Node root, String ref) {
        if (ref.equals("#")) return root;
        if (!ref.startsWith("#/")) return null;
        Node current = root;
        for (String raw : ref.substring(2).split("/", -1)) {
            String segment = raw.replace("~1", "/").replace("~0", "~");
            if (current instanceof MappingNode m) {
                Node next = null;
                for (NodeTuple t : m.getValue()) {
                    if (t.getKeyNode() instanceof ScalarNode k && segment.equals(k.getValue())) {
                        next = t.getValueNode();
                        break;
                    }
                }
                if (next == null) return null;
                current = next;
            } else if (current instanceof SequenceNode s) {
                int index;
                try {
                    index = Integer.parseInt(segment);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (index < 0 || index >= s.getValue().size()) return null;
                current = s.getValue().get(index);
            } else {
                return null;
            }
        }
        return current;
    }

    /** Keys of a mapping by name; non-scalar and duplicate keys are reported. */
    private Map<String, Field> fields(MappingNode m) {
        Map<String, Field> result = new LinkedHashMap<>();
        for (NodeTuple t : m.getValue()) {
            String key = scalar(t.getKeyNode());
            if (key == null) {
                add(line(t.getKeyNode()), "Mapping keys must be plain strings");
            } else if (result.putIfAbsent(key, new Field(t.getKeyNode(), t.getValueNode())) != null) {
                add(line(t.getKeyNode()), "Duplicate key '" + key + "' (first used on line "
                        + line(result.get(key).key()) + ")");
            }
        }
        return result;
    }

    private static String scalar(Node n) {
        return n instanceof ScalarNode s ? s.getValue() : null;
    }

    private static int line(Node n) {
        return n.getStartMark() == null ? 1 : n.getStartMark().getLine() + 1;
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    private void add(int line, String message) {
        if (issues.size() < MAX_ISSUES) issues.add(new Issue(line, message));
    }
}
