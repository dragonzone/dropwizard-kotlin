/*
 * MIT License
 *
 * Copyright (c) 2023 Bryan Harclerode
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

package zone.dragon.dropwizard.kotlin.coroutines

import java.util.concurrent.Executor
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.eclipse.jetty.server.Server
import org.glassfish.hk2.api.Factory
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.server.spi.Container
import org.glassfish.jersey.server.spi.ContainerLifecycleListener
import org.glassfish.jersey.servlet.ServletContainer

/**
 * A [CoroutineDispatcher] that dispatches into the Jetty [Server.getThreadPool]
 *
 * The Jetty thread pool behaves very similarly to [Dispatchers.IO], and simplifies application management so that developers only have to
 * tune a single thread pool for requests.
 *
 * @author Bryan Harclerode
 */
class JettyCoroutineDispatcherFactory : Factory<CoroutineDispatcher>, ContainerLifecycleListener {

    companion object {
        /**
         * Name of the HK2 Binding
         *
         * This can be used to specifically inject the Jetty thread pool dispatcher when HK2 has multiple [CoroutineDispatcher]s registered.
         */
        const val NAME = "zone.dragon.dropwizard.kotlin.JETTY_DISPATCHER"

        /**
         * Name of the servlet attribute under which Jetty publishes its thread pool
         */
        private const val JETTY_THREADPOOL_ATTRIBUTE_NAME = "org.eclipse.jetty.server.Executor"
    }

    private lateinit var executor: Executor

    override fun provide(): CoroutineDispatcher = executor.asCoroutineDispatcher()

    override fun dispose(instance: CoroutineDispatcher?) = Unit

    override fun onStartup(container: Container) {
        executor = (container as ServletContainer).servletContext.getAttribute(JETTY_THREADPOOL_ATTRIBUTE_NAME) as Executor
    }

    override fun onReload(container: Container?) = Unit

    override fun onShutdown(container: Container?) = Unit

    /**
     * HK2 Binder that configures [JettyCoroutineDispatcherFactory]
     */
    class Binder : AbstractBinder() {
        override fun configure() {
            val factory = JettyCoroutineDispatcherFactory()
            bind(factory).to(ContainerLifecycleListener::class.java)
            bindFactory(factory).to(CoroutineDispatcher::class.java).named(NAME).`in`(Singleton::class.java).ranked(-2)
        }
    }
}
