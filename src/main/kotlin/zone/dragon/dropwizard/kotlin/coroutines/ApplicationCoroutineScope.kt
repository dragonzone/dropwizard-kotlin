package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import mu.KLogging
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.server.BackgroundScheduler
import org.glassfish.jersey.server.ManagedAsyncExecutor
import org.glassfish.jersey.spi.ExecutorServiceProvider
import org.glassfish.jersey.spi.ScheduledExecutorServiceProvider
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import org.glassfish.hk2.api.Factory as Hk2Factory

/**
 * [CoroutineScope] for the Jersey container that dispatches to the container thread pools
 *
 * @author Bryan Harclerode
 */
class ApplicationCoroutineScope private constructor(private val executor: BackgroundAsyncExecutor) : CoroutineScope {

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext = executor.asCoroutineDispatcher() + job

    class Factory @Inject constructor(
        @ManagedAsyncExecutor private val executorProvider: ExecutorServiceProvider,
        @BackgroundScheduler private val schedulerProvider: ScheduledExecutorServiceProvider
    ) : Hk2Factory<ApplicationCoroutineScope> {

        companion object : KLogging()

        override fun provide(): ApplicationCoroutineScope {
            logger.debug { "Starting application coroutine scope" }
            val executor = BackgroundAsyncExecutor(executorProvider.executorService, schedulerProvider.executorService)
            return ApplicationCoroutineScope(executor)
        }

        override fun dispose(instance: ApplicationCoroutineScope) {
            logger.debug { "Closing application coroutine scope" }
            instance.job.complete()
            executorProvider.dispose(instance.executor.executor)
            schedulerProvider.dispose(instance.executor.scheduler)
        }
    }

    class Binder : AbstractBinder() {
        override fun configure() {
            bindFactory(Factory::class.java, Singleton::class.java)
                .to(CoroutineScope::class.java)
                .`in`(Singleton::class.java)
        }
    }

    private class BackgroundAsyncExecutor(
        val executor: ExecutorService, val scheduler: ScheduledExecutorService
    ) : ExecutorService by executor, ScheduledExecutorService {
        override fun schedule(
            command: Runnable, delay: Long, unit: TimeUnit
        ): ScheduledFuture<*> = scheduler.schedule(command, delay, unit)

        override fun <V : Any?> schedule(
            callable: Callable<V>, delay: Long, unit: TimeUnit
        ): ScheduledFuture<V> = scheduler.schedule(callable, delay, unit)

        override fun scheduleAtFixedRate(
            command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit
        ): ScheduledFuture<*> = scheduler.scheduleAtFixedRate(command, initialDelay, period, unit)

        override fun scheduleWithFixedDelay(
            command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit
        ): ScheduledFuture<*> = scheduler.scheduleWithFixedDelay(command, initialDelay, delay, unit)

        override fun shutdown() {
            // Underlying executors are managed by Jersey, so ignore attempts to close this dispatcher
        }
    }

}
