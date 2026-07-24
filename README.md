# Kotlin Union & Intersection Types Compiler Plugin

###### In development
[![Build](https://github.com/kochkaev/kotlin-union-types/actions/workflows/build.yml/badge.svg)](https://github.com/kochkaev/kotlin-union-types/actions/workflows/build.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.kochkaev.kotlin.uniontypes.svg)](https://plugins.gradle.org/plugin/io.github.kochkaev.kotlin.uniontypes)

This project is a Kotlin compiler plugin (K2/FIR) that introduces support for **Union and Intersection Types** via annotations. It allows you to create complex type constraints that are verified at compile time, enhancing Kotlin's type system without adding any runtime overhead.

- **Union Types (`@Union`)**: Specify that a value can be one of several distinct types.
- **Intersection Types (`@Intersection`)**: Specify that a value must conform to all of several types simultaneously.

## Philosophy and Core Principles

The design of this plugin is guided by two core principles:

1.  **Zero Runtime Overhead**: All type checks are performed exclusively at compile time. The annotations are effectively erased, and the compiled code operates on the base type (e.g., `Any`). This means there is no performance penalty for using these types.

2.  **Backward Compatibility**: Code written with this plugin remains fully compatible with the standard Kotlin compiler. If you compile a project using these annotations without the plugin, it will still compile successfully, as the annotations will simply be ignored. 

## Installation

The plugin is published to the Gradle Plugin Portal. To use it, first ensure the Gradle Plugin Portal is included in your plugin repositories in `settings.gradle.kts` (or `settings.gradle`):

### `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral() // Or other repositories
    }
}
```

Then, apply the plugin to your module's `build.gradle.kts` (or `build.gradle`).

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

Forward type parameters from a generic function or class to create flexible APIs. The type parameter must be in the current scope (e.g., from a surrounding class or function). The plugin will search for it by name and report an error if it is not found.

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

## Fundamental Limitations of the Kotlin K2 Compiler

1.  **Bottom-Up Type Resolution**: The K2 compiler resolves types from the "bottom up". This prevents the plugin from checking individual `vararg` members against a union/intersection type without an explicit type declaration.
    ```kotlin
    // Error: The `listOf` function doesn't know about the union type. It infers the common
    // supertype of "string" and 123 as `Serializable & Comparable<*>`, which does not match the union.
    val list: List<@Union(String::class, Int::class) Any> = listOf("string", 123)
    
    // OK: Explicitly providing the type parameter for `listOf` allows each element to be
    // checked individually against the union type.
    val list1 = listOf<@Union(String::class, Int::class) Any>("string", 123)
    ```

2.  **Immutable Types**: The plugin cannot change the types defined in the source code. It can only validate them. This imposes strict rules on how base types must relate to each other:
    - Each member of a union type must be a subtype of the base type.
    - The combination of intersection type members must be a subtype of the base type.
    - In all operations, base types must be compatible.
    ```kotlin
    class MyType<out O> {
        fun produce(): @UnionAdv(Type(typeParameter = "O")) Any? = null
    }
    
    val impl: MyType<CharSequence> = MyType()
    
    // Error: The type `CharSequence?` is not a subtype of `Any?` (the base type of the union).
    val x: CharSequence? = impl.produce()
    
    // OK: The cast is permitted because `CharSequence` is a member of the union type.
    val y: CharSequence? = impl.produce() as? CharSequence
    ```

## Plugin-Specific Limitations

These are limitations of the current plugin implementation, not of Kotlin itself.

1.  **Concrete Base Types Required**: Union and intersection types can only be declared on a concrete base type, not on a type parameter.
2.  **No Simultaneous Annotations**: A type cannot be annotated as both a union and an intersection type.
3.  **Conflicting `type` and `typeParameter`**: In advanced annotations (`@UnionAdv`, `@IntersectionAdv`), the `Type` annotation cannot specify both `type` and `typeParameter` simultaneously.
    ```kotlin
    class MyClass<T> {
        // Compilation Error: `type` and `typeParameter` cannot be used together.
        val x: @UnionAdv(
            Type(
                type = List::class,
                typeParameter = "T" 
            )
        ) Any? = null
    }
    ```
4.  **Usage Restrictions**: The plugin's union/intersection types cannot be used as:
    - A receiver for an extension function/property (`fun (@Union(...) Any).f()`).
    - A contextual argument (`context(c: @Union(...) Any) fun f()`).
    - A parent type (`class A : @Union(...) B`).

## Known Weaknesses

Due to the compile-time-only nature of this plugin, there are scenarios where the type safety guarantees can be bypassed.

1.  **Platform Interoperability**: The Java compiler (and other platform-specific compilers) has no knowledge of these annotations. If you call a Kotlin function with a union/intersection type from another language (e.g., Java, Swift, or JavaScript), the constraints will not be enforced, allowing incorrect types to be passed. This weakness applies to all Kotlin Multiplatform targets.

2.  **Reflection**: At runtime, the underlying type is just its base type (e.g., `Any`). Reflection can be used to inspect or assign values that violate the original compile-time contract.

3.  **Gradle Plugin Distribution**: The compiler plugin is distributed as a Gradle plugin, which is not inherited transitively. If a library `A` uses this plugin, an application `B` that depends on `A` will **not** automatically have the plugin applied. To maintain type safety, application `B` must also explicitly apply the compiler plugin in its own build configuration.

## Project Structure

- `kotlin-union-types-annotations`: Annotation definitions (`@Union`, `@Intersection`, etc.).
- `kotlin-union-types-compiler`: The K2/FIR compiler plugin.
- `kotlin-union-types-gradle-plugin`: The Gradle plugin for easy setup.
- `kotlin-union-types-idea-plugin`: IDEA plugin (not yet implemented).

## Building the Project

This project is built with Gradle.

- To build the plugin and annotations: `./gradlew build`
- To run the tests: `./gradlew test`

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.