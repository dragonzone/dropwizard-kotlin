/*
 * Copyright 2019 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package zone.dragon.dropwizard.kotlin.coroutines

import kotlinx.coroutines.CoroutineScope
import org.glassfish.hk2.api.ServiceLocator
import org.glassfish.jersey.process.Inflector
import org.glassfish.jersey.server.model.Invocable
import org.glassfish.jersey.server.model.Parameter
import org.glassfish.jersey.server.model.Parameterized
import org.glassfish.jersey.server.spi.internal.ParameterValueHelper
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.ContainerRequestContext

/**
 * @author Darth Android
 * @date 5/19/2019
 */
class CoroutineDispatcher(
    private val invocable: Invocable,
    private val locator: ServiceLocator
) : Inflector<ContainerRequestContext, Any?> {

    private val parameters = object : Parameterized {
        override fun requiresEntity(): Boolean = parameters.any { it.source == Parameter.Source.ENTITY }

        override fun getParameters(): MutableList<Parameter> = invocable.parameters.subList(0, invocable.parameters.size - 1)
    }

    private val parameterValueProviders = ParameterValueHelper.createValueProviders(locator, parameters)

    override fun apply(data: ContainerRequestContext): Any? {
        val handler = invocable.handler.getInstance(locator)
        val method = invocable.definitionMethod
        val scope = locator.getService(CoroutineScope::class.java)
        val continuation = AsyncResponseContinuation(locator.getService(AsyncResponse::class.java), scope.coroutineContext)
        var args = ParameterValueHelper.getParameterValues(parameterValueProviders)
        args = Array<Any?>(args.size+1) {
            if (it < args.size) {
                args[it]
            } else {
                continuation
            }
        }
        return method(handler, *args)
    }
}