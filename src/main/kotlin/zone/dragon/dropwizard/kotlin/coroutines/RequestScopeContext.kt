package zone.dragon.dropwizard.kotlin.coroutines

import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import org.glassfish.jersey.process.internal.RequestContext
import org.glassfish.jersey.process.internal.RequestScope
import org.glassfish.jersey.process.internal.RequestScoped
import java.lang.RuntimeException
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Context Element that captures the current jersey [RequestScope] and all [RequestScoped] objects and makes them
 * available to a coroutine as it dispatches onto other threads. This will add a reference count until the element is
 * [invoke]d, which should be done automatically by attaching it to a job with [Job.invokeOnCompletion]
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class RequestScopeContext private constructor(
    private val activeInstance: RequestContext,
    private val threadLocal: ThreadLocal<RequestContext?>
) : CopyableThreadContextElement<RequestContext?>,
    AbstractCoroutineContextElement(Key) {

    constructor(requestScope: RequestScope) : this(
        requestScope.suspendCurrent(),
        currentRequestContextField.get(requestScope) as ThreadLocal<RequestContext?>
    )

    /**
     * Key of [RequestScopeContext] in [CoroutineContext].
     */
    companion object Key : CoroutineContext.Key<RequestScopeContext> {
        private val currentRequestContextField: Field

        init {
            try {
                currentRequestContextField = RequestScope::class.java.getDeclaredField("currentRequestContext")
                currentRequestContextField.isAccessible = true
            } catch (e: NoSuchFieldException) {
                throw RuntimeException("This version of dropwizard-kotlin is not compatible with the version of jersey in use", e)
            }
        }
    }

    private val disposeHandle = AtomicReference<DisposableHandle?>(null)

    override fun restoreThreadContext(context: CoroutineContext, oldState: RequestContext?) {
        threadLocal.set(oldState)
    }

    override fun copyForChild(): CopyableThreadContextElement<RequestContext?> {
        return RequestScopeContext(activeInstance.reference, threadLocal)
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        return overwritingElement
    }

    override fun updateThreadContext(context: CoroutineContext): RequestContext? {
        if (disposeHandle.get() == null) {
            val handle = context[Job]?.invokeOnCompletion { activeInstance.release() }
            if (handle != null && !disposeHandle.compareAndSet(null, handle)) {
                handle.dispose()
            }
        }
        val oldScope = threadLocal.get()
        threadLocal.set(activeInstance)
        return oldScope
    }


}
