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

package zone.dragon.dropwizard.kotlin.coroutines

import jakarta.inject.Inject
import jakarta.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.glassfish.jersey.model.Parameter.Source
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.ContainerRequest
import org.glassfish.jersey.server.model.Parameter
import org.glassfish.jersey.server.spi.internal.ValueParamProvider
import org.glassfish.jersey.server.spi.internal.ValueParamProvider.Priority
import java.util.function.Function
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * [ValueParamProvider] that resolves a [JerseyRequestCoroutine] for injecting into suspending kotlin functions
 *
 * @author Bryan Harclerode
 */
@jakarta.ws.rs.ext.Provider
class ContinuationValueParamProvider : ValueParamProvider {

    companion object {
        private val TERMINAL_CONTINUATION = object : Continuation<Unit> {

            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                // NOOP
            }
        }
        private val valueProvider: Function<ContainerRequest, *> = Function { TERMINAL_CONTINUATION }
    }


    override fun getValueProvider(parameter: Parameter): Function<ContainerRequest, *>? {
        if (parameter.source != Source.CONTEXT) {
            return null
        }
        if (parameter.rawType != Continuation::class.java) {
            return null
        }
        return valueProvider
    }

    override fun getPriority(): ValueParamProvider.PriorityType {
        return Priority.NORMAL
    }
}
