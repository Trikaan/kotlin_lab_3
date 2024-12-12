package org.jub.kotlin.hometask4

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
fun CoroutineScope.hasActiveJobs(): Boolean = this.coroutineContext.job.children.any { it.isActive }
class ApplicationImpl(
    private val resultsFile: String,
    private val tasks: List<Callable<out Any>>
) : Application {
    private var running = true
    private var isForce = false
    private val threadPool = Executors.newFixedThreadPool(6)
    private val coroutines = CoroutineScope(threadPool.asCoroutineDispatcher())
    override fun waitToFinish() {
        while (coroutines.hasActiveJobs() && !isForce) Thread.sleep(100)
    }
    override fun run() {
        File(resultsFile).writeText("")
        while (running) {
            val command = readlnOrNull() ?: ""
            handleCommand(command)
        }
    }
    private fun handleCommand(command: String) {
        when {
            command.startsWith("task") -> handleTask(command)
            command == "get" -> get()
            command == "finish grace" -> finishGrace()
            command == "finish force" -> finishForce()
            command == "clean" -> clean()
            command == "help" -> help()
            command == "" -> running = false
            else -> println("Unknown command: $command")
        }
    }
    private fun handleTask(command: String) {
        if (running) runBlocking {
            coroutines.launch {
                val parts = command.split(" ")
                if (parts.size == 3) {
                    val name = parts[1]
                    val taskIndex = parts[2].toIntOrNull()
                    if (taskIndex != null && taskIndex in tasks.indices) {
                        val task = tasks[taskIndex]
                        try {
                            val result = task.call()
                            var resultString = "$name: $result"
                            File(resultsFile).appendText("$resultString\n")
                        } catch (e: Exception) {
                            System.err.println(e)
                        }
                    }
                }
            }
        }
    }
    private fun get() {
        runBlocking {
            coroutines.launch {
                val results = File(resultsFile).readLines()
                if (results.isNotEmpty()) {
                    val lastResult = results.last()
                    val name = lastResult.substringBefore(":")
                    val result = lastResult.substringAfter(":").trim()
                    println("$result [$name]")
                }
                else println("No results available.")
            }
        }
    }
    private fun finishGrace() {
        println("Shutting down gracefully...")
        running = false
        while (coroutines.hasActiveJobs()) Thread.sleep(100)
    }
    private fun finishForce() {
        running = false
        runBlocking {
            coroutines.coroutineContext.job.cancel()
        }
    }
    private fun clean() {
        File(resultsFile).writeText("") // Clear the file
        println("Results file cleaned.")
    }
    private fun help() {
        println("Available commands:")
        println("task <name> <index> - Run a task.")
        println("get - Get the latest result.")
        println("finish grace - Stop gracefully.")
        println("finish force - Stop immediately.")
        println("clean - Clear results.")
    }
}


interface Application : Runnable {
    fun waitToFinish() {}

    companion object {

        fun create(resultsFile: String, tasks: List<Callable<out Any>>?): Application =
            ApplicationImpl(resultsFile, tasks ?: emptyList())
    }
}