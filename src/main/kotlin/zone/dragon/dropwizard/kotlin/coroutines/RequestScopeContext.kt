package zone.dragon.dropwizard.kotlin.coroutines

import kotlinx.coroutines.ThreadContextElement
import org.glassfish.jersey.process.internal.RequestScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class RequestScopeContext(private val requestScope: RequestScope) : ThreadContextElement<RequestScope.Instance>, AbstractCoroutineContextElement(Key) {

    private val activeInstance = requestScope.suspendCurrent()

    override fun restoreThreadContext(context: CoroutineContext, oldState: RequestScope.Instance) {
        activeInstance.
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun updateThreadContext(context: CoroutineContext): RequestScope.Instance {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Key of [RequestScopeContext] in [CoroutineContext].
     */
    companion object Key : CoroutineContext.Key<RequestScopeContext>

}