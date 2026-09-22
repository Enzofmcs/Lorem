package dev.lorem.app.ui.navigation

enum class LoremDestination(val route: String, val title: String) {
    Configuration("configuration", "Configuração"),
    Home("home", "Início"),
    Ipsum("ipsum", "Ipsum"),
    Result("result", "Resultado"),
    History("history", "Histórico"),
    Statistics("statistics", "Estatísticas"),
    ;

    companion object {
        val all: List<LoremDestination> = entries
    }
}
