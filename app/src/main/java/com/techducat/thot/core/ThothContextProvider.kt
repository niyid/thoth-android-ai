package com.techducat.thot.core

/**
 * Contract for any component that can execute a [ThothTask] and deliver a result.
 * All implementations must be safe to call from the main thread; they are
 * responsible for dispatching to a background thread internally.
 */
interface ThothContextProvider {
    /**
     * Run [task] and deliver the AI-generated response to [callback].
     * [callback] is always invoked on the **main thread**.
     */
    fun runTask(task: ThothTask, callback: (String) -> Unit)
}
