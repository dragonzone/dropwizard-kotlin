package zone.dragon.dropwizard.kotlin.coroutines.scopes

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import org.glassfish.hk2.api.Factory
import org.glassfish.hk2.api.PerLookup
import org.glassfish.hk2.utilities.binding.AbstractBinder
import kotlin.coroutines.CoroutineContext

class CoroutineContextFactory @Inject constructor(private val scopeProvider: Provider<CoroutineScope>) : Factory<CoroutineContext> {
    override fun provide(): CoroutineContext = scopeProvider.get().coroutineContext

    override fun dispose(instance: CoroutineContext?) {
        // ignore
    }

    class Binder : AbstractBinder() {
        override fun configure() {
            bindFactory(CoroutineContextFactory::class.java, Singleton::class.java)
                .to(CoroutineContext::class.java)
                .`in`(PerLookup::class.java)
        }
    }
}
