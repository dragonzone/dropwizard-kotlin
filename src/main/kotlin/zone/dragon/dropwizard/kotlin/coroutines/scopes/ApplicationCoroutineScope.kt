/*
 * MIT License
 *
 * Copyright (c) 2019-2023 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package zone.dragon.dropwizard.kotlin.coroutines.scopes

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.glassfish.hk2.api.Factory
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
            instance.job.cancel("Application is shutting down")
            if (!instance.job.isCompleted) {
                logger.info { "Waiting for coroutines to complete..." }
                // Wait for the job to actually complete
                runBlocking { instance.job.join() }
                logger.info { "All coroutines completed." }
            }
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
            bind(Dispatchers.Default).to(CoroutineDispatcher::class.java).ranked(-2)
        }
    }
}


