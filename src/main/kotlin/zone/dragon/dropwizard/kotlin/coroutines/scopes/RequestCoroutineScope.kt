package zone.dragon.dropwizard.kotlin.coroutines.scopes

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.glassfish.hk2.api.Factory
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.process.internal.RequestContext
import org.glassfish.jersey.process.internal.RequestScope
import org.glassfish.jersey.process.internal.RequestScoped
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class RequestCoroutineScope private constructor(
    parentContext: CoroutineContext, private val job: CompletableJob
) : CoroutineScope by CoroutineScope(parentContext + job) {

    constructor(parentContext: CoroutineContext, requestContext: ScopeFactory.RequestScopeContext) : this(
        parentContext + requestContext, Job(parentContext[Job])
    ) {
        println("Start Scope $job")
        job.invokeOnCompletion(requestContext)
    }

    fun complete() {
        println("Completing scope $job")
        job.complete()
    }

    companion object Key : KLogging(), CoroutineContext.Key<ScopeFactory.RequestScopeContext> {
        const val NAME = "zone.dragon.dropwizard.kotlin.REQUEST_SCOPE"
    }

    class ScopeFactory @Inject constructor(
        @Named(ApplicationCoroutineScope.NAME) private val applicationContext: Provider<CoroutineContext>,
        private val requestScope: RequestScope
    ) : Factory<RequestCoroutineScope> {

        companion object {
            private val currentRequestContextField: Field

            init {
                try {
                    currentRequestContextField = RequestScope::class.java.getDeclaredField("currentRequestContext")
                    currentRequestContextField.isAccessible = true
                } catch (e: NoSuchFieldException) {
                    throw RuntimeException(
                        "This version of dropwizard-kotlin is not compatible with the version of jersey in use", e
                    )
                }
            }
        }

        private val threadLocal = currentRequestContextField.get(requestScope) as ThreadLocal<RequestContext?>

        override fun provide(): RequestCoroutineScope {
            return RequestCoroutineScope(applicationContext.get(), RequestScopeContext())
        }

        override fun dispose(instance: RequestCoroutineScope) {
            instance.complete()
        }

        inner class RequestScopeContext : ThreadContextElement<RequestContext?>,
            AbstractCoroutineContextElement(Key),
            CompletionHandler {

            private val requestContext = requestScope.suspendCurrent() ?: null
            private val active = AtomicBoolean(true)
            override fun restoreThreadContext(context: CoroutineContext, oldState: RequestContext?) {
                threadLocal.set(oldState)
            }

            override fun updateThreadContext(context: CoroutineContext): RequestContext? {
                val oldScope = threadLocal.get()
                threadLocal.set(requestContext)
                return oldScope
            }

            override fun invoke(cause: Throwable?) {
                if (active.compareAndSet(true, false)) {
                    requestContext?.release()
                }
            }
        }
    }

    class Binder : AbstractBinder() {
        override fun configure() {
            bindFactory(ScopeFactory::class.java, Singleton::class.java)
                .to(RequestCoroutineScope::class.java)
                .to(CoroutineScope::class.java)
                .`in`(RequestScoped::class.java)
                .named(NAME)
                .ranked(-1)
        }
    }
}
