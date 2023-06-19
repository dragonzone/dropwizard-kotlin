package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.hk2.api.Factory as Hk2Factory

/**
 * [CoroutineScope] for the Jersey container that dispatches to the container thread pools
 *
 * @author Bryan Harclerode
 */
class ApplicationCoroutineScope private constructor(dispatcher: CoroutineDispatcher, private val job: CompletableJob) :
    CoroutineScope by CoroutineScope(dispatcher + job) {

    companion object {
        const val NAME = "zone.dragon.dropwizard.kotlin.APPLICATION_SCOPE"
    }

    private class Factory @Inject constructor(private val dispatcher: CoroutineDispatcher) : Hk2Factory<ApplicationCoroutineScope> {

        companion object : KLogging()

        override fun provide(): ApplicationCoroutineScope {
            logger.debug { "Starting application coroutine scope" }
            return ApplicationCoroutineScope(dispatcher, SupervisorJob())
        }

        override fun dispose(instance: ApplicationCoroutineScope) {
            logger.debug { "Closing application coroutine scope" }
            instance.job.complete()
            // Wait for the job to actually complete
            runBlocking { instance.job.join() }
        }
    }

    /**
     * Binder for [ApplicationCoroutineScope] as the default [CoroutineScope] used as a parent across all the requests
     */
    class Binder : AbstractBinder() {
        override fun configure() {
            bindFactory(Factory::class.java, Singleton::class.java)
                .to(CoroutineScope::class.java)
                .`in`(Singleton::class.java)
                .named(NAME)
                .ranked(-1)
            bind(Dispatchers.Default).to(CoroutineDispatcher::class.java).ranked(-1)
        }
    }
}
