package io.bsoft.bconverter.domain.usecase

/** Rules for the user-visible base name of an output file (no extension, no path). */
object OutputNames {
    private const val MAX_LENGTH = 200
    private const val FORBIDDEN = "/\\:*?\"<>|"

    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH || trimmed.all { it == '.' }) return false
        return name.none { it in FORBIDDEN || it.isISOControl() }
    }
}
