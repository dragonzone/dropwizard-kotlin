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

package zone.dragon.dropwizard.kotlin.coroutines.scopes

import java.util.Deque
import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.CoroutineMDCAdapter
import org.slf4j.MDC
import org.slf4j.spi.MDCAdapter

/**
 * Coroutine Thread Context for propagating the SLF4J MDC across a request
 *
 * This requires intercepting the MDC adapter with [CoroutineMDCAdapter] to deal with the concurrency aspects of coroutines.
 *
 * @author Bryan Harclerode
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class CoroutineMdcThreadContext(
    context: Map<String, String>?, deques: Map<String, Deque<String>>?
) : AbstractCoroutineContextElement(Key), CopyableThreadContextElement<AutoCloseable>, MDCAdapter {

    private val context = context?.let(::HashMap) ?: mutableMapOf()
    private val deques = deques?.mapValues { LinkedList(it.value) }?.toMutableMap() ?: mutableMapOf()

    constructor() : this(MDC.getCopyOfContextMap(), emptyMap())

    companion object Key : CoroutineContext.Key<CoroutineMdcThreadContext>

    @ExperimentalCoroutinesApi
    override fun copyForChild(): CoroutineMdcThreadContext {
        return CoroutineMdcThreadContext(context, deques)
    }

    @ExperimentalCoroutinesApi
    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineMdcThreadContext {
        return overwritingElement as CoroutineMdcThreadContext
    }

    @ExperimentalCoroutinesApi
    override fun restoreThreadContext(context: CoroutineContext, oldState: AutoCloseable) {
        oldState.close()
    }

    @ExperimentalCoroutinesApi
    override fun updateThreadContext(context: CoroutineContext): AutoCloseable {
        return CoroutineMDCAdapter.activateCoroutineContext(this)
    }

    override fun get(key: String): String? = context[key]

    override fun put(key: String, `val`: String?) {
        if (`val` == null) {
            context.remove(key)
        } else {
            context[key] = `val`
        }
    }

    override fun remove(key: String) {
        context.remove(key)
    }

    override fun clear() {
        context.clear()
    }

    override fun getCopyOfContextMap(): MutableMap<String, String> {
        return HashMap(context)
    }

    override fun setContextMap(contextMap: MutableMap<String, String>?) {
        if (contextMap != null) {
            context.clear()
            context.putAll(contextMap)
        }
    }

    override fun pushByKey(key: String, value: String?) {
        deques.computeIfAbsent(key) { LinkedList() }.push(value)
    }

    override fun popByKey(key: String): String? {
        return deques[key]?.pop()
    }

    override fun getCopyOfDequeByKey(key: String): Deque<String>? {
        return deques[key]?.let { ConcurrentLinkedDeque(it) }
    }

    override fun clearDequeByKey(key: String) {
        deques.keys.remove(key)
    }
}
