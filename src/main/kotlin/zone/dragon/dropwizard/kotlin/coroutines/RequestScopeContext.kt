package zone.dragon.dropwizard.kotlin.coroutines

import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.ThreadContextElement
import org.glassfish.jersey.process.internal.RequestScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class RequestScopeContext(private val requestScope: RequestScope) : ThreadContextElement<RequestScope.Instance>,
                                                                    AbstractCoroutineContextElement(Key),
                                                                    CompletionHandler {


    /**
     * Key of [RequestScopeContext] in [CoroutineContext].
     */
    companion object Key : CoroutineContext.Key<RequestScopeContext>

    private val activeInstance = requestScope.suspendCurrent()
    private val requestScopeThreadLocal: ThreadLocal<RequestScope.Instance>
    private val active = AtomicBoolean(true)

    init {
        val field = requestScope::class.java.getDeclaredField("currentScopeInstance")
        field.isAccessible = true
        requestScopeThreadLocal = field.get(requestScope) as ThreadLocal<RequestScope.Instance>
    }


    override fun restoreThreadContext(context: CoroutineContext, oldScope: RequestScope.Instance) {
        requestScopeThreadLocal.set(oldScope)
    }

    override fun updateThreadContext(context: CoroutineContext): RequestScope.Instance {
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