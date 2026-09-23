package dev.lorem.app

import android.content.Context
import dev.lorem.app.data.local.LocalLoremRepository
import dev.lorem.app.data.local.LoremDatabase
import dev.lorem.app.data.local.profileDataStore
import dev.lorem.app.data.remote.CodeforcesApiRepository
import dev.lorem.app.data.remote.TwoSecondCodeforcesRequestGate
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository

class AppContainer(context: Context) {
    private val database = LoremDatabase.create(context)
    val loremRepository: LoremRepository = LocalLoremRepository(
        dataStore = context.profileDataStore,
        problemHistoryDao = database.problemHistoryDao(),
        problemCatalogDao = database.problemCatalogDao(),
        ipsumDao = database.ipsumDao(),
    )
    val codeforcesRepository: CodeforcesRepository = CodeforcesApiRepository(
        gate = TwoSecondCodeforcesRequestGate(),
    )
}
