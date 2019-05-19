package zone.dragon.dropwizard.kotlin

import kotlinx.coroutines.Dispatchers
import org.glassfish.hk2.api.Factory
import kotlin.coroutines.CoroutineContext

class CoroutineContextFactory : Factory<CoroutineContext> {
    override fun provide(): CoroutineContext {
        // TODO provide proper implementation
        return Dispatchers.Default
    }

    override fun dispose(instance: CoroutineContext?) {
        // not used
    }
}