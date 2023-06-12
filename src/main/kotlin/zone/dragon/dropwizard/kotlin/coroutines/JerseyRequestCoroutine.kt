package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import jakarta.ws.rs.container.AsyncResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.slf4j.MDCContext
import org.glassfish.hk2.api.PerLookup
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.process.internal.RequestScope
import org.slf4j.MDC
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import org.glassfish.hk2.api.Factory as Hk2Factory

class JerseyRequestCoroutine private constructor(
    private val asyncResponse: AsyncResponse, context: CoroutineContext, private val job: CompletableJob
) : Continuation<Any?>, CoroutineScope by CoroutineScope(context + job + MDCContext()) {

    class Binder : AbstractBinder() {
        override fun configure() {
            bindFactory(Factory::class.java, Singleton::class.java)
                .to(JerseyRequestCoroutine::class.java)
                .`in`(PerLookup::class.java)
        }
    }

    class Factory @Inject constructor(
        private val asyncResponseProvider: Provider<AsyncResponse>,
        private val parentScopeProvider: Provider<CoroutineScope>,
        private val requestScope: RequestScope
    ) : Hk2Factory<JerseyRequestCoroutine> {

        override fun provide(): JerseyRequestCoroutine {
            return JerseyRequestCoroutine(
                asyncResponseProvider.get(), parentScopeProvider.get().coroutineContext, requestScope
            )
        }

        override fun dispose(instance: JerseyRequestCoroutine) {
            instance.cancel("Scope Destroyed")
        }
    }

    private constructor(
        asyncResponse: AsyncResponse, parentContext: CoroutineContext, requestScope: RequestScopeContext
    ) : this(
        asyncResponse, parentContext + requestScope, Job(parentContext[Job])
    ) {
        job.invokeOnCompletion(requestScope)
    }

    constructor(asyncResponse: AsyncResponse, parentContext: CoroutineContext, requestScope: RequestScope) : this(
        asyncResponse, parentContext, RequestScopeContext(requestScope)
    )

    override val context = coroutineContext

    override fun resumeWith(result: Result<Any?>) {
        Dispatchers.IO.dispatch(context) {
            // Ensure MDC is preserved as we hand off to post-processing
            MDC.setContextMap(context[MDCContext]?.contextMap)
            // Proceed with the request pipeline
            result.onFailure {
                asyncResponse.resume(it)
                job.cancel(CancellationException(it.message, it))
            }.onSuccess {
                asyncResponse.resume(it)
                job.complete()
            }
        }
    }

    fun complete() {
        job.complete()
    }
}
