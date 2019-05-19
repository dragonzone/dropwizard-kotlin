package zone.dragon.dropwizard.kotlin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.glassfish.hk2.api.Factory

class CoroutineScopeFactory : Factory<CoroutineScope> {
    override fun provide(): CoroutineScope {
        return CoroutineScope(Dispatchers.Default)
    }

    override fun dispose(instance: CoroutineScope?) {
        if (instance != null) {
            instance.cancel()
        }
    }
}