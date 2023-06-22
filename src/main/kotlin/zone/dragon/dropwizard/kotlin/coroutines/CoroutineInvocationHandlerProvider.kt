package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.process.internal.RequestScope
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.model.Invocable
import org.glassfish.jersey.server.spi.internal.ResourceMethodInvocationHandlerProvider
import java.lang.reflect.InvocationHandler
import kotlin.reflect.jvm.kotlinFunction

/**
 * Interceptor that intercepts resource method invocation for suspending kotlin functions and suspends the jersey
 * processing context when the coroutine suspends and dispatches to a different thread
 */
class CoroutineInvocationHandlerProvider @Inject constructor(
    asyncContext: Provider<AsyncContext>, scope: Provider<CoroutineScope>
) : ResourceMethodInvocationHandlerProvider {

    /**
     * Binder for configuring [CoroutineInvocationHandlerProvider] to intercept resource handlers that are coroutines
     */
    class Binder : AbstractBinder() {
        override fun configure() {
            bind(CoroutineInvocationHandlerProvider::class.java)
                .to(ResourceMethodInvocationHandlerProvider::class.java)
                .`in`(Singleton::class.java)
        }
    }

    private var invocationHandler = CoroutineInvocationHandler(scope, asyncContext)

    override fun create(method: Invocable): InvocationHandler? {
        if (method.handlingMethod.kotlinFunction?.isSuspend == true) {
            return invocationHandler
        }
        return null
    }
}
