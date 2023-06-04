package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import jakarta.ws.rs.ProcessingException
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.internal.LocalizationMessages
import org.glassfish.jersey.server.model.Invocable
import org.glassfish.jersey.server.spi.internal.ResourceMethodInvocationHandlerProvider
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.reflect.jvm.kotlinFunction

/**
 * Interceptor that intercepts resource method invocation for suspending kotlin functions and suspends the jersey
 * processing context when the coroutine suspends and dispatches to a different thread
 */
class CoroutineInvocationHandlerProvider @Inject constructor(asyncContext: Provider<AsyncContext>) :
    ResourceMethodInvocationHandlerProvider {

    class Binder : AbstractBinder() {
        override fun configure() {
            bind(CoroutineInvocationHandlerProvider::class.java)
                .to(ResourceMethodInvocationHandlerProvider::class.java)
                .`in`(Singleton::class.java)
        }
    }

    private val suspendHandler = InvocationHandler { proxy: Any, method: Method, args: Array<Any> ->
        val result = method.invoke(proxy, *args)
        // Coroutine suspended, so suspend the jersey context
        if (result === COROUTINE_SUSPENDED) {
            if (!asyncContext.get().suspend()) {
                throw ProcessingException(LocalizationMessages.ERROR_SUSPENDING_ASYNC_REQUEST())
            }
            return@InvocationHandler null
        }
        // Coroutine didn't suspend, just clean up the coroutine and return the result
        (args[args.size - 1] as? JerseyRequestCoroutineScope)?.dispose()
        return@InvocationHandler result
    }

    override fun create(method: Invocable): InvocationHandler? {
        if (method.handlingMethod.kotlinFunction?.isSuspend == true) {
            return suspendHandler
        }
        return null
    }
}
