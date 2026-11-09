# utils.json

Jackson helpers: the shared mapper, typed field accessors on `JsonNode`, and a reflection-free `@type` registry with its Jackson adapter. User-facing guide: [README.md](README.md).

`JacksonUtils` is the load-bearing type — about 30 files in the downstream MDT projects call its field accessors (mostly mdt-client model classes and the mdt-instance asset-variable configs). Every API change here needs an MDT compile check (`mdt-client`, `mdt-instance`, and the projects that depend on them).

## Shared mapper

`JacksonUtils.MAPPER = newJsonMapper(false)`: `JavaTimeModule` registered explicitly plus `findAndAddModules()`, `WRITE_DATES_AS_TIMESTAMPS` disabled (ISO strings; reading still accepts numbers). It is JVM-wide — never reconfigure it; build a new mapper (`newJsonMapper`, or `rebuild()`) instead. MDT has its own mapper (`mdt.model.MDTModelSerDe`) wrapping aas4j; don't assume the two are configured alike.

## Field accessor contract

One naming rule for every type `X` ∈ {∅ (`JsonNode`), `String`, `Text`, `Boolean`, `Int`, `Long`, `Double`, `Array`}:

- `getXField(node, name)` — missing **or JSON null** → exception.
- `getXFieldOrNull(node, name)` — missing/null → `null`.
- `getXFieldOrDefault(node, name, default)` — missing/null → `default`. Arrays use `getArrayFieldOrEmpty` instead.
- A parent that is not a JSON object (number, string, array, `NullNode`) throws in all three forms (`invalid node for field 'x': object expected, but number`). `getFieldOrNull` is the deliberate exception: a low-level lookup that never checks the parent (31 MDT call sites; changing it would ripple checked exceptions through them).
- A present value of the wrong type always throws, in all three forms. `String` accepts any scalar as text but rejects containers (kept lenient for config-file compatibility); `Text` (for identifier fields such as `ArgumentSpec.id`) accepts JSON strings only; `Int`/`Long` accept only in-range JSON integers (no strings, no fractions); `Double` accepts any finite JSON number (`1e400` → `Infinity` is rejected); `Boolean` accepts JSON booleans and case-insensitive `"true"`/`"false"`. Arrays come back as unmodifiable `List<JsonNode>`.

## Scalar conversion (`JsonScalars`)

`JsonScalars.toByte/toShort/toInt/toLong/toBigInteger/toFloat/toDouble/toDecimal/toBoolean(node, Policy)` is the single strict converter. It never coerces (`"abc"`→0, 3.9→3, overflow, NaN/∞, `"yes"`→false are all `JacksonDeserializationException`). Two policies, chosen by the user: `STRICT` = JSON types only, except booleans also accept `"true"`/`"false"` text (this matches the field accessors, which delegate to it); `TEXT_TOLERANT` = also numeric strings (BigDecimal syntax only), lossless fractional for integral types (`3.0`), and `"1"`/`"0"` booleans. Messages carry no context or value (`int expected, but string`, `out of int range`); callers prefix context (`invalid field 'x': …` here, `invalid xs:int value: …` in mdt-client `AbstractDataType`). Users: `JacksonUtils` field accessors (STRICT), mdt-client `mdt.aas.DataTypes` (TEXT_TOLERANT), mdt-instance `RobotStateVariable.checkJsonType` (STRICT). Changing a policy's rules changes all three — check their tests.

## Exception contract

All failures are `JacksonDeserializationException`, which extends Jackson's `JsonMappingException` and is therefore a **checked `IOException`**. This is deliberate: `ObjectMapper.treeToValue` re-wraps a plain `IOException` thrown by a custom deserializer into `IllegalArgumentException`, but passes `JsonProcessingException` subtypes through untouched. So:

- Throw `JacksonDeserializationException`, not a bare `IOException`, from code that may run inside a Jackson deserializer.
- Messages name fields only (`available fields=[…]`, max 10), never values — config nodes carry passwords. Don't add node dumps.
- Because it is an `IOException`, `catch (IOException)` blocks in MDT also catch config/type errors (all such blocks in MDT are error-reporting paths).

## `@type` registry and adapter

- `MultiTypeJsonNodeDeserializer<T>` — immutable (`Map.copyOf`) registry keyed by the type-name field (default `JacksonUtils.TYPE_FIELD` = `"@type"`, override with `typeField`). Registrations are `JsonNodeDeserializer<? extends T>` — a custom `@FunctionalInterface` (`T deserialize(JsonNode) throws IOException`), intentionally *not* a `Checked*X` type and *not* Jackson's `com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer` (same simple name — check imports). The type name must be a JSON string (`isTextual()`); numbers/booleans are rejected rather than coerced like `getStringField` does. The registry's map is immutable, but concurrent use is only safe if the registered functions are — say so rather than calling the registry thread-safe outright. The registry itself implements `JsonNodeDeserializer<T>`, so registries nest. `getTypeNames()` order is unspecified (`Set.copyOf`); tests compare message prefixes only.
- `JsonNodeSerDe<T> extends JsonNodeDeserializer<T>` adds `serialize(T)`; implemented by mdt-client `mdt.rt.Ros2SerDes`. Null contract (Javadoc on both interfaces): callers never pass Java `null`, implementations reject it (`IllegalArgumentException` preferred, `IOException` allowed — the latter only so `Ros2SerDes`, which throws `IOException("invalid … (not object): null")`, stays conforming); `NullNode` acceptance is implementation-defined and documented (rejection preferred); `serialize` never returns Java `null` (`NullNode` instead).
- `JsonNodeStdDeserializer<T>` — concrete `StdDeserializer<T>` with a public constructor (usable directly in `SimpleModule.addDeserializer`) that reads a tree and delegates. It reads with `ctxt.readTree(parser)`, **not** `parser.getCodec().readTree(parser)`: the codec path is the root-value path and, with `FAIL_ON_TRAILING_TOKENS` on, treats the next array element/field as trailing garbage. (MDT still has that codec pattern in `InstanceDescriptor`, `SubmodelUtils`, `AssetVariableConfigs`; harmless while no mapper enables the feature.) `@JsonDeserialize(using=…)` needs a no-arg class, so each target type keeps a one-line subclass calling `super(Type.class, DESERIALIZER)`. Rejected alternatives (keep them rejected unless asked): a registry of `StdDeserializer`s, and turning `JsonNodeDeserializer` into an abstract `StdDeserializer` subclass — both lose lambda/method-reference registration.

MDT users: all four `@type` dispatchers in mdt-client use the registry — `ElementReferences` (`parseJsonNode` calls it directly), `TaskArgument` and `ArgumentSpec` (`parseTypedJsonNode`), and `ElementValues` (each type's `deserializeValue` takes the `value` sub-node, so registrations are wrapped by a private `onValue(...)`; a JSON-null `value` is passed through — list/collection reject it, as they reject any non-array/non-object — and a missing one throws). All four Jackson `Deserializer` nested classes extend `JsonNodeStdDeserializer` (one-line constructor).
