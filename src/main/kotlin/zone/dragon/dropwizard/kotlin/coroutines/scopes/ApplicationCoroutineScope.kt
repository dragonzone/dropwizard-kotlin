package zone.dragon.dropwizard.kotlin.coroutines.scopes

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.glassfish.hk2.api.Factory
import org.glassfish.hk2.api.PerLookup
import org.glassfish.hk2.utilities.binding.AbstractBinder
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * [CoroutineScope] for the Jersey container that supervises all request scopes and ensures they are cleaned up when
 * the Jersey container shuts down or reloads.
 *
 * @author Bryan Harclerode
 */
class ApplicationCoroutineScope private constructor(context: CoroutineContext, private val job: CompletableJob) :
    CoroutineScope by CoroutineScope(context + job) {

    companion object {
        const val NAME = "zone.dragon.dropwizard.kotlin.APPLICATION_SCOPE"
    }

    private class ScopeFactory(private val context: CoroutineContext) : Factory<ApplicationCoroutineScope> {

        @Inject
        constructor() : this(EmptyCoroutineContext)

        companion object : KLogging()

        override fun provide(): ApplicationCoroutineScope {
            return ApplicationCoroutineScope(context, SupervisorJob())
        }

        override fun dispose(instance: ApplicationCoroutineScope) {
            instance.job.complete()
            if (!instance.job.isCompleted) {
                logger.info { "Waiting for coroutines to complete..." }
                // Wait for the job to actually complete
                runBlocking { instance.job.join() }
                logger.info { "All coroutines completed." }
            }
        }
    }

    /**
     * Factory that exposes the
     */
    private class ContextFactory @Inject constructor(
        @Named(NAME) private val scopeProvider: Provider<CoroutineScope>
    ) : Factory<CoroutineContext> {
        override fun provide(): CoroutineContext = scopeProvider.get().coroutineContext

        override fun dispose(instance: CoroutineContext?) {
            // nothing to do
        }
    }

    /**
     * Binder for [ApplicationCoroutineScope] as the default [CoroutineScope] used as a parent across all the requests
     */
    class Binder : AbstractBinder() {
        override fun configure() {
            // Using Rank -2 as a default for application scope so that the request scope can still use -1 as a default
            bindFactory(ScopeFactory::class.java, Singleton::class.java)
                .to(CoroutineScope::class.java)
                .`in`(Singleton::class.java)
                .named(NAME)
                .ranked(-2)
            bindFactory(ContextFactory::class.java, Singleton::class.java)
                .to(CoroutineContext::class.java)
                .`in`(PerLookup::class.java)
                .named(NAME)
                .ranked(-2)
            bind(Dispatchers.Default).to(CoroutineDispatcher::class.java).ranked(-2)
        }
    }
}
