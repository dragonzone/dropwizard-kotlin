package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.ws.rs.container.AsyncResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.glassfish.hk2.api.PerLookup
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.process.internal.RequestScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

class JerseyRequestCoroutineScope private constructor(
    private val asyncResponse: AsyncResponse, context: CoroutineContext, private val job: CompletableJob
) : Continuation<Any?>, CoroutineScope by CoroutineScope(context + job) {

    class Binder : AbstractBinder() {
        override fun configure() {
            bindAsContract(JerseyRequestCoroutineScope::class.java).`in`(PerLookup::class.java)
        }
    }

    private constructor(
        asyncResponse: AsyncResponse, context: CoroutineContext, requestScope: RequestScopeContext
    ) : this(
        asyncResponse, context + requestScope, Job(context[Job])
    ) {
        job.invokeOnCompletion(requestScope)
    }

    @Inject
    constructor(asyncResponse: AsyncResponse, parentScope: CoroutineScope, requestScope: RequestScope) : this(
        asyncResponse, parentScope.coroutineContext, RequestScopeContext(requestScope)
    )

    override val context = coroutineContext

    override fun resumeWith(result: Result<Any?>) {
        result.onFailure {
            asyncResponse.resume(it)
            job.cancel(CancellationException(it.message, it))
        }
        result.onSuccess {
            asyncResponse.resume(it)
            job.complete()
        }
    }

    fun dispose() {
        job.complete()
    }
}
