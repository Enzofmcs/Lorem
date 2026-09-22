package dev.lorem.app

import android.content.Context
import dev.lorem.app.data.local.DataStoreLoremRepository
import dev.lorem.app.data.local.profileDataStore
import dev.lorem.app.data.remote.CodeforcesApiRepository
import dev.lorem.app.data.remote.TwoSecondCodeforcesRequestGate
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.LoremRepository

class AppContainer(context: Context) {
    val loremRepository: LoremRepository = DataStoreLoremRepository(context.profileDataStore)
    val codeforcesRepository: CodeforcesRepository = CodeforcesApiRepository(
        gate = TwoSecondCodeforcesRequestGate(),
    )
}
