package com.varabyte.kobweb.cli.run

import com.varabyte.kobweb.cli.common.Anims
import com.varabyte.kobweb.cli.common.kobwebFolder
import com.varabyte.kobweb.cli.common.newline
import com.varabyte.kobweb.server.api.*
import com.varabyte.konsole.foundation.anim.konsoleAnimOf
import com.varabyte.konsole.foundation.input.Keys
import com.varabyte.konsole.foundation.input.onKeyPressed
import com.varabyte.konsole.foundation.konsoleApp
import com.varabyte.konsole.foundation.konsoleVarOf
import com.varabyte.konsole.foundation.render.aside
import com.varabyte.konsole.foundation.runUntilSignal
import com.varabyte.konsole.foundation.text.*
import com.varabyte.konsole.foundation.timer.addTimer
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Duration

private enum class RunState {
    STARTING,
    RUNNING,
    STOPPING_GRACEFULLY,
    STOPPING_VIA_INTERRUPT,
    CHANGED_EXTERNALLY,
    STOPPED,
    CANCELLED,
    CANCELLED_VIA_INTERRUPT,
}

fun handleRun(env: ServerEnvironment) = konsoleApp {
    val kobwebFolder = kobwebFolder ?: return@konsoleApp

    newline() // Put space between user prompt and eventual first line of Gradle output

    val serverStateFile = ServerStateFile(kobwebFolder)

    val envName = when (env) {
        ServerEnvironment.DEV -> "development"
        ServerEnvironment.PROD -> "production"
    }
    lateinit var serverState: ServerState // Set if RunState ever hits RunState.RUNNING; otherwise, don't use!
    val ellipsisAnim = konsoleAnimOf(Anims.ELLIPSIS)
    var runState by konsoleVarOf(RunState.STARTING)
    var addOutputSeparator by konsoleVarOf(false) // Separate active block from Gradle output above if any
    konsole {
        if (addOutputSeparator) {
            textLine()
        }

        when (runState) {
            RunState.STARTING -> {
                textLine("Starting a Kobweb server ($envName)$ellipsisAnim")
                textLine()
                textLine("Press Q anytime to cancel.")
            }
            RunState.RUNNING -> {
                green {
                    text("Kobweb server ($envName) is running at ")
                    cyan { text("http://localhost:${serverState.port}") }
                }
                textLine(" (PID = ${serverState.pid})")
                textLine()
                textLine("Press Q anytime to stop it.")
            }
            RunState.STOPPING_GRACEFULLY -> {
                textLine("Server is stopping$ellipsisAnim")
            }
            RunState.STOPPED -> {
                textLine("Server stopped gracefully.")
            }
            RunState.CHANGED_EXTERNALLY -> {
                yellow { textLine("Exiting. It seems like the server was stopped by a separate process.") }
            }
            RunState.STOPPING_VIA_INTERRUPT -> {
                yellow { textLine("CTRL-C received. Kicked off a request to stop the server but we have to exit NOW.") }
            }
            RunState.CANCELLED -> {
                textLine("Server startup cancelled.")
            }
            RunState.CANCELLED_VIA_INTERRUPT -> {
                yellow { textLine("CTRL-C received. Server startup cancelled.") }
            }
        }
    }.runUntilSignal {
        fun consumeStream(stream: InputStream) {
            val isr = InputStreamReader(stream)
            val br = BufferedReader(isr)
            while (true) {
                val line = br. readLine() ?: break
                addOutputSeparator = true
                aside {
                    black(isBright = true) {
                        textLine(line)
                    }
                }
            }
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        val startServerProcess = Runtime.getRuntime()
            .exec(arrayOf("./gradlew", "-PkobwebEnv=$env", "kobwebStart", "-t"))

        CoroutineScope(Dispatchers.IO).launch { consumeStream(startServerProcess.inputStream) }
        CoroutineScope(Dispatchers.IO).launch { consumeStream(startServerProcess.errorStream) }

        Runtime.getRuntime().addShutdownHook(Thread {
            if (runState == RunState.RUNNING || runState == RunState.STOPPING_GRACEFULLY) {
                runState = RunState.STOPPING_VIA_INTERRUPT

                ServerRequestsFile(kobwebFolder).enqueueRequest(ServerRequest.Stop())
            }
            else {
                runState = RunState.CANCELLED_VIA_INTERRUPT
            }
            signal()
        })

        onKeyPressed {
            if (key in listOf(Keys.EOF, Keys.Q)) {
                if (runState == RunState.STARTING) {
                    runState = RunState.STOPPING_GRACEFULLY
                    CoroutineScope(Dispatchers.IO).launch {
                        startServerProcess.destroy()
                        startServerProcess.waitFor()

                        runState = RunState.CANCELLED
                        signal()
                    }
                } else if (runState == RunState.RUNNING) {
                    runState = RunState.STOPPING_GRACEFULLY
                    CoroutineScope(Dispatchers.IO).launch {
                        startServerProcess.destroy()
                        startServerProcess.waitFor()

                        val stopServerProcess = Runtime.getRuntime().exec(arrayOf("./gradlew", "kobwebStop"))
                        CoroutineScope(Dispatchers.IO).launch { consumeStream(stopServerProcess.inputStream) }
                        CoroutineScope(Dispatchers.IO).launch { consumeStream(stopServerProcess.errorStream) }
                        stopServerProcess.waitFor()

                        runState = RunState.STOPPED
                        signal()
                    }
                }
            }
        }

        coroutineScope {
            while (runState == RunState.STARTING) {
                serverStateFile.content?.takeIf { it.isRunning() }?.let {
                    serverState = it
                    runState = RunState.RUNNING
                    return@coroutineScope
                }
                delay(300)
            }
        }

        if (runState == RunState.RUNNING) {
            addTimer(Duration.ofMillis(500), repeat = true) {
                if (runState == RunState.RUNNING) {
                    if (!serverState.isRunning() || serverStateFile.content != serverState) {
                        runState = RunState.CHANGED_EXTERNALLY
                        signal()
                    }
                } else {
                    repeat = false
                }
            }
        }
    }
}