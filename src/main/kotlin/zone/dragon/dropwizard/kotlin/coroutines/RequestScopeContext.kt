package zone.dragon.dropwizard.kotlin.coroutines

import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.ThreadContextElement
import org.glassfish.jersey.process.internal.RequestContext
import org.glassfish.jersey.process.internal.RequestScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class RequestScopeContext(private val requestScope: RequestScope) : ThreadContextElement<RequestContext?>,
    AbstractCoroutineContextElement(Key),
    CompletionHandler {


    /**
     * Key of [RequestScopeContext] in [CoroutineContext].
     */
    companion object Key : CoroutineContext.Key<RequestScopeContext> {
        private val currentRequestContextField = RequestScope::class.java.getDeclaredField("currentRequestContext")
        init {
            currentRequestContextField.isAccessible = true
        }
    }

    private val activeInstance = requestScope.suspendCurrent()
    private val requestScopeThreadLocal = currentRequestContextField.get(requestScope) as ThreadLocal<RequestContext?>
    private val active = AtomicBoolean(true)


    override fun restoreThreadContext(context: CoroutineContext, oldState: RequestContext?) {
        requestScopeThreadLocal.set(oldState)
    }

    override fun updateThreadContext(context: CoroutineContext): RequestContext? {
        val oldScope = requestScopeThreadLocal.get()
        requestScopeThreadLocal.set(activeInstance)
        return oldScope
    }

    override fun invoke(cause: Throwable?) {
        if (active.compareAndSet(true, false)) {
            activeInstance.release()
        }
    }

}
