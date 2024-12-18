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

package org.slf4j

import org.slf4j.spi.MDCAdapter
import zone.dragon.dropwizard.kotlin.coroutines.scopes.CoroutineMdcThreadContext
import java.util.Deque
import java.util.LinkedList

/**
 * Intercepts the SLF4J MDC to enable context switching for coroutines
 *
 *
 *
 * @author Bryan Harclerode
 */
class CoroutineMDCAdapter(private val originalProvider: MDCAdapter) : MDCAdapter {

    private class Frame(val context: CoroutineMdcThreadContext, val stack: LinkedList<Frame>) : AutoCloseable {

        init {
            stack.push(this)
        }
        override fun close() {
            stack.remove(this)
        }
    }

    companion object {

        private val activeCoroutineMdcContext = ThreadLocal.withInitial { LinkedList<Frame>() }

        init {
            // Hook the MDC Adapter to intercept all calls
            MDC.mdcAdapter = CoroutineMDCAdapter(MDC.getMDCAdapter())
        }

        /**
         * Activates a new coroutine context on the current thread for the MDC
         *
         * This immediately activates the given MDC context for the current thread until the returned handle is closed.
         */
        fun activateCoroutineContext(context: CoroutineMdcThreadContext): AutoCloseable {
            return Frame(context, activeCoroutineMdcContext.get())
        }
    }

    /**
     * The "Active" MDC Adapter for the current thread
     *
     * This defaults to the original provider MDC adapter, but prefers the most recently [activated][activateCoroutineContext] context that
     * is still active for the current thread.
     */
    private val activeAdapter: MDCAdapter
        get() = activeCoroutineMdcContext.get().peekFirst()?.context ?: originalProvider

    override fun put(key: String?, `val`: String?) {
        activeAdapter.put(key, `val`)
    }

    override fun get(key: String?): String? {
        return activeAdapter.get(key)
    }

    override fun remove(key: String?) {
        activeAdapter.remove(key)
    }

    override fun clear() {
        activeAdapter.clear()
    }

    override fun getCopyOfContextMap(): MutableMap<String, String>? {
        return activeAdapter.copyOfContextMap
    }

    override fun setContextMap(contextMap: MutableMap<String, String>?) {
        activeAdapter.setContextMap(contextMap)
    }

    override fun pushByKey(key: String?, value: String?) {
        activeAdapter.pushByKey(key, value)
    }

    override fun popByKey(key: String?): String? {
        return activeAdapter.popByKey(key)
    }

    override fun getCopyOfDequeByKey(key: String?): Deque<String>? {
        return activeAdapter.getCopyOfDequeByKey(key)
    }

    override fun clearDequeByKey(key: String?) {
        activeAdapter.clearDequeByKey(key)
    }

}
