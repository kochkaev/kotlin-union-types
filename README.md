# Kotlin Union & Intersection Types Compiler Plugin

###### In development
[![Build](https://github.com/kochkaev/kotlin-union-types/actions/workflows/build.yml/badge.svg)](https://github.com/kochkaev/kotlin-union-types/actions/workflows/build.yml)

This project is a Kotlin compiler plugin (K2/FIR) that introduces support for **Union and Intersection Types** via annotations. It allows you to create complex type constraints that are verified at compile time, enhancing Kotlin's type system without adding any runtime overhead.

- **Union Types (`@Union`)**: Specify that a value can be one of several distinct types.
- **Intersection Types (`@Intersection`)**: Specify that a value must conform to all of several types simultaneously.

## Philosophy and Core Principles

The design of this plugin is guided by two core principles:

1.  **Zero Runtime Overhead**: All type checks are performed exclusively at compile time. The annotations are effectively erased, and the compiled code operates on the base type (e.g., `Any`). This means there is no performance penalty for using these types.

2.  **Backward Compatibility**: Code written with this plugin remains fully compatible with the standard Kotlin compiler. If you compile a project using these annotations without the plugin, it will still compile successfully, as the annotations will simply be ignored. 

## Installation

The plugin is published to the Gradle Plugin Portal. To use it, apply the plugin to your Gradle project.

### Kotlin DSL (`build.gradle.kts`)
```kotlin
plugins {
    id("io.github.kochkaev.kotlin.uniontypes") version "YOUR_VERSION"
}
```

### Groovy DSL (`build.gradle`)
```groovy
plugins {
    id 'io.github.kochkaev.kotlin.uniontypes' version 'YOUR_VERSION'
}
```

The plugin is applicable to any Kotlin Multiplatform target, including JVM, JS, Native, and Android.

## Features

- **Static Type Checking**: Enforces that only allowed types are assigned or returned.
- **Union and Intersection Types**: Supports both "one-of" (union) and "all-of" (intersection) constraints.
- **Simple and Advanced Annotations**: Use `@Union`/`@Intersection` for basic cases and `@UnionAdv`/`@IntersectionAdv` for generics and type parameters.
- **Type Alias Support**: Create readable and reusable custom types.
- **Generic and Type Parameter Support**: Define constraints that include generic types (e.g., `List<String>`) or forward type parameters from functions/classes.
- **Inheritance and Overriding**: Correctly handles complex types in overridden methods and properties.
- **IDE Integration**: Type mismatches are reported directly in the IDE, just like standard Kotlin type errors.

## Usage

### 1. Union Types with `@Union`

A **union type** allows a value to be one of several types.

```kotlin
import io.github.kochkaev.kotlin.uniontypes.annotations.Union

// This variable can hold either a String or an Int.
typealias StringOrInt = @Union(String::class, Int::class) Any

fun processId(id: StringOrInt) { /* ... */ }

processId("abc") // OK
processId(123)   // OK
processId(true)  // Compilation Error: Type mismatch!
```

### 2. Intersection Types with `@Intersection`

An **intersection type** requires a value to satisfy all specified types simultaneously. It's like a local, ad-hoc `where` clause.

```kotlin
import io.github.kochkaev.kotlin.uniontypes.annotations.Intersection
import java.io.Serializable

// This value must be both a CharSequence and Serializable.
typealias Text = @Intersection(CharSequence::class, Serializable::class) Any

val message: Text = "Hello" // OK, String is both.
val log: Text = 123L // Compilation Error: Long is not CharSequence.
```

### 3. Advanced Usage with `@UnionAdv` and `@IntersectionAdv`

For scenarios involving generics or forwarding type parameters, use the "advanced" annotations.

#### With Generics

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

Forward type parameters from a generic function or class to create flexible APIs.

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

## Known Limitations and Weaknesses

Due to the compile-time-only nature of this plugin, there are scenarios where the type safety guarantees can be bypassed.

1.  **Platform Interoperability**: The Java compiler (and other platform-specific compilers) has no knowledge of these annotations. If you call a Kotlin function with a union/intersection type from another language (e.g., Java, Swift, or JavaScript), the constraints will not be enforced, allowing incorrect types to be passed. This weakness applies to all Kotlin Multiplatform targets.

2.  **Reflection**: At runtime, the underlying type is just its base type (e.g., `Any`). Reflection can be used to inspect or assign values that violate the original compile-time contract.

3.  **Gradle Plugin Distribution**: The compiler plugin is distributed as a Gradle plugin, which is not inherited transitively. If a library `A` uses this plugin, an application `B` that depends on `A` will **not** automatically have the plugin applied. To maintain type safety, application `B` must also explicitly apply the compiler plugin in its own build configuration.

## Building the Project

This project is built with Gradle.

- To build the plugin and annotations: `./gradlew build`
- To run the tests: `./gradlew test`

The core logic is located in the `compiler/` module, which is the K2 FIR plugin. The `annotations/` module contains the annotation definitions.

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.