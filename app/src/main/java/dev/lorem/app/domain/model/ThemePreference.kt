package dev.lorem.app.domain.model


enum class ThemePreference(val label: String) {
    SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro");

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }
}
