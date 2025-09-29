package com.whisper.architecture.logger

import java.util.concurrent.CopyOnWriteArrayList

object Logger {

    private val FOREST: MutableList<Tree> = CopyOnWriteArrayList()

    fun plant(tree: Tree) = FOREST.add(tree)

    fun uproot(tree: Tree) = FOREST.remove(tree)

    fun uprootAll() = FOREST.clear()

    private fun log(level: LogLevel, tag: String, msg: String, throwable: Throwable? = null) {
        if (FOREST.isEmpty()) {
            return
        }
        FOREST.forEach { it.log(level, tag, msg, throwable) }
    }

    fun i(tag: String, throwable: Throwable? = null, supplier: () -> String) =
        log(LogLevel.INFO, tag, supplier(), throwable)

    fun d(tag: String, throwable: Throwable? = null, supplier: () -> String) =
        log(LogLevel.DEBUG, tag, supplier(), throwable)

    fun v(tag: String, throwable: Throwable? = null, supplier: () -> String) =
        log(LogLevel.VERBOSE, tag, supplier(), throwable)

    fun w(tag: String, throwable: Throwable? = null, supplier: () -> String) =
        log(LogLevel.WARN, tag, supplier(), throwable)

    fun e(tag: String, throwable: Throwable? = null, supplier: () -> String) =
        log(LogLevel.ERROR, tag, supplier(), throwable)

}