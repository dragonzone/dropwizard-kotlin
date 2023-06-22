package zone.dragon.dropwizard.kotlin.coroutines.scopes

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Provider
import kotlinx.coroutines.CoroutineScope
import mu.KLogging
import org.glassfish.hk2.api.IterableProvider
import org.glassfish.hk2.api.ServiceHandle
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import java.util.concurrent.atomic.AtomicReference

/**
 * Application listener that injects and disposes the [ApplicationCoroutineScope] for the Jersey container and
 * [RequestCoroutineScope] for each request
 */
class CoroutineScopeManager @Inject constructor(
    @param:Named(ApplicationCoroutineScope.NAME) private val applicationScopeProvider: IterableProvider<CoroutineScope>,
    @param:Named(RequestCoroutineScope.NAME) private val requestScopeProvider: IterableProvider<RequestCoroutineScope>
) : ApplicationEventListener {

    companion object : KLogging()

    private val activeApplicationScope = AtomicReference<ServiceHandle<CoroutineScope>?>(null)

    override fun onEvent(event: ApplicationEvent) {
        when (event.type) {
            // Obtain and save a handle if a scope isn't already active
            ApplicationEvent.Type.INITIALIZATION_START -> {
                val handle = applicationScopeProvider.handle
                if (activeApplicationScope.compareAndSet(null, handle)) {
                    handle.service
                }
            }
            // Destroy the handle when the application closes if a scope is active
            ApplicationEvent.Type.DESTROY_FINISHED -> {
                activeApplicationScope.getAndSet(null)?.close()
            }
            // Ignore other events
            else -> {}
        }
    }

    override fun onRequest(requestEvent: RequestEvent): RequestEventListener? {
        if (requestEvent.type == RequestEvent.Type.START) {
            // RequestScope is not yet available, so we have to pass the provider and load it only on the appropriate
            // event
            return RequestScopeListener(requestScopeProvider)
        }
        return null
    }

    private class RequestScopeListener(
        private val requestScopeProvider: Provider<RequestCoroutineScope>
    ) : RequestEventListener {

        private lateinit var requestScope: RequestCoroutineScope

        override fun onEvent(event: RequestEvent) {
            when (event.type) {
                // Since we can't capture the RequestScope in the START event of the ApplicationEventListener,
                // we instead have to wait until the MATCHING_START event
                RequestEvent.Type.MATCHING_START -> {
                    requestScope = requestScopeProvider.get()
                }

                RequestEvent.Type.FINISHED -> {
                    requestScope.complete()
                }
                // Ignore other events
                else -> {}
            }
        }
    }
}
