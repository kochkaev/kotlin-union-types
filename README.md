# Kotlin Union Types Compiler Plugin

###### In development
[![Build](https://github.com/kochkaev/kotlin-union-types/actions/workflows/build.yml/badge.svg)](https://github.com/kochkaev/kotlin-union-types/actions/workflows/build.yml)

This project is a Kotlin compiler plugin (K2/FIR) that introduces support for **Union Types** via annotations. It allows you to specify that a variable, parameter, or return value can be one of several distinct types, and the compiler will enforce these constraints.

This provides a form of type-safe polymorphism without requiring a common sealed hierarchy, bringing more flexibility to your type system.

## Features

- **Static Type Checking**: Enforces that only allowed types are assigned or returned.
- **Simple and Advanced Annotations**: Use `@Union` for basic cases and `@UnionAdv` for generics and type parameters.
- **Type Alias Support**: Create readable and reusable union types.
- **Generic and Type Parameter Support**: Define unions that include generic types (e.g., `List<String>`) or forward type parameters from functions/classes.
- **Inheritance and Overriding**: Correctly handles union types in overridden methods and properties, supporting covariant return types.
- **IDE Integration**: Type mismatches are reported directly in the IDE, just like standard Kotlin type errors.

## How It Works

The plugin hooks into the Kotlin compiler's analysis phase. When it encounters the `@Union` or `@UnionAdv` annotations on a type, it performs the following checks:

1.  **Assignment/Return Check**: Verifies that any value assigned to the annotated type matches one of the types specified in the union.
2.  **Inheritance Check**: Ensures that when overriding a method, the new return type is a valid subset (or equal to) the parent's union type.
3.  **Type Resolution**: Expands type aliases and resolves type parameters to check compatibility correctly.

## Usage

First, add the `annotations` dependency to your project. The compiler plugin itself will be applied separately.

### 1. Basic Union Types with `@Union`

The `@Union` annotation is the simplest way to define a union. It takes a `vararg` of `KClass` references.

```kotlin
import io.github.kochkaev.kotlin.uniontypes.annotations.Union

// This variable can hold either a String or an Int.
val id: @Union(String::class, Int::class) Any

id = "user-123" // OK
id = 456        // OK
id = 1.0        // Compilation Error: Type mismatch!
```

For better readability, it's highly recommended to use a `typealias`:

```kotlin
typealias StringOrInt = @Union(String::class, Int::class) Any

fun processId(id: StringOrInt) { /* ... */ }

processId("abc") // OK
processId(123)   // OK
processId(true)  // Compilation Error: Type mismatch!
```

### 2. Advanced Union Types with `@UnionAdv`

For more complex scenarios involving generics or type parameters from the enclosing scope, use `@UnionAdv`.

#### With Generics

You can define unions of specific generic types.

```kotlin
import io.github.kochkaev.kotlin.uniontypes.annotations.UnionAdv
import io.github.kochkaev.kotlin.uniontypes.annotations.Type

// A union of List<String> or a single Int
typealias ListOfStringOrInt = @UnionAdv(
    Type(List::class, generics = [Type(String::class)]),
    Type(Int::class)
) Any

val data: ListOfStringOrInt = listOf("a", "b") // OK
val data2: ListOfStringOrInt = 100             // OK
val data3: ListOfStringOrInt = listOf(1.0)     // Compilation Error!
```

#### With Type Parameters

`@UnionAdv` can forward type parameters from a generic function or class. This is useful for creating flexible APIs.

```kotlin
// This function accepts a value of type T (which must be a Number) or a String.
fun <T : Number> process(value: @UnionAdv(Type(typeParameter = "T"), Type(String::class)) Any) {
    // ...
}

process<Int>(123)       // OK, T is Int
process<Double>(1.23)   // OK, T is Double
process<Int>("hello")   // OK
process<Int>(true)      // Compilation Error!
```

### 3. Inheritance and Overriding

The plugin correctly handles variance for overridden methods. A method can be overridden with a more specific (smaller) union type, but not a broader one.

```kotlin
open class Base {
    open fun getId(): @Union(String::class, Int::class, Long::class) Any = "base"
}

class Derived : Base() {
    // OK: Overriding with a subset of the original union is allowed.
    override fun getId(): @Union(String::class, Int::class) Any = 123
}

class InvalidDerived : Base() {
    // Error: Cannot override with a wider union.
    override fun getId(): @Union(String::class, Int::class, Double::class) Any = 1.0
}
```

### 4. Combining and Constraining Unions

You can create new unions from existing ones, effectively expanding or constraining them.

```kotlin
// Base union
typealias StringOrNumber = @Union(String::class, Number::class) Any

// Expands the union by adding Boolean
typealias Expanded = @Union(StringOrNumber::class, Boolean::class) Any
val v1: Expanded = true // OK

// Constrains the union to only allow String or Int (since Int is a Number)
typealias Constrained = @Union(String::class, Int::class) StringOrNumber
val v2: Constrained = "hello" // OK
val v3: Constrained = 123     // OK
val v4: Constrained = 1.0     // Error: Double is not part of the constrained union
```

## Future Work

- **Intersection Types**: Support for `@Intersection` and `@IntersectionAdv` annotations.
- **Smart Casting**: Improved smart casting for `when` expressions and `is` checks to narrow down union types.
- **Tooling**: Potential IDE inspections to improve the development experience.

## Building the Project

This project is built with Gradle.

- To build the plugin and annotations: `./gradlew build`
- To run the tests: `./gradlew test`

The core logic is located in the `compiler/` module, which is the K2 FIR plugin. The `annotations/` module contains the annotation definitions.

## License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
