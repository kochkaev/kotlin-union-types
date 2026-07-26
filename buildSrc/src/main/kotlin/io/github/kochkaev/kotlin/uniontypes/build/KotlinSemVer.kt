package io.github.kochkaev.kotlin.uniontypes.build

data class KotlinSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String?
) : Comparable<KotlinSemVer> {

    companion object {
        operator fun invoke(version: String): KotlinSemVer {
            val parts = version.substringBefore("-").split(".")
            val suffix = if (version.contains("-")) version.substringAfter("-") else null

            return KotlinSemVer(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                suffix = suffix
            )
        }
    }

    override operator fun compareTo(other: KotlinSemVer): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.patch != other.patch) return this.patch.compareTo(other.patch)

        if (this.suffix == null && other.suffix == null) return 0
        if (this.suffix == null) return 1
        if (other.suffix == null) return -1

        return this.suffix.compareTo(other.suffix, ignoreCase = true)
    }

    override fun toString(): String {
        val base = "${major}.${minor}.${patch}"
        return if (suffix != null) "$base-$suffix" else base
    }
}