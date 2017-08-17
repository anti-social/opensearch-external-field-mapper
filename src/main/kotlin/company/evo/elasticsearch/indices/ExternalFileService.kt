/*
* Copyright 2017 Alexander Koval
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package company.evo.elasticsearch.indices

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import org.elasticsearch.common.component.AbstractLifecycleComponent
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.env.NodeEnvironment
import org.elasticsearch.index.Index
import org.elasticsearch.threadpool.ThreadPool


class ExternalFileService : AbstractLifecycleComponent {

    private val nodeDir: Path
    private val threadPool: ThreadPool
    private val values: MutableMap<FileKey, FileValue?> = ConcurrentHashMap()
    private val tasks: MutableMap<FileKey, UpdateTask> = HashMap()

    companion object {
        lateinit var instance: ExternalFileService
        var started: Boolean = false
    }

    private data class UpdateTask(
            val future: ThreadPool.Cancellable,
            val settings: FileSettings
    )

    @Inject
    constructor(
            settings: Settings,
            threadPool: ThreadPool,
            nodeEnv: NodeEnvironment) : super(settings) {
        this.nodeDir = nodeEnv.nodeDataPaths()[0]
        this.threadPool = threadPool
    }

    override public fun doStart() {
        if (started) {
            throw IllegalStateException("Already started")
        }
        instance = this
    }

    override public fun doStop() {
        started = false
    }

    override fun doClose() {}

    @Synchronized
    fun addField(index: Index, fieldName: String, updateInterval: Long, url: String?) {
        val fileSettings = FileSettings(updateInterval, url)
        val fileUpdater = ExternalFileUpdater(this.nodeDir, index, fieldName, fileSettings)
        val key = FileKey(index.name, fieldName)
        this.values.computeIfAbsent(key) {
            fileUpdater.loadValues(null)
        }
        val existingTask = this.tasks[key]
        if (existingTask != null && existingTask.settings != fileSettings) {
            existingTask.future.cancel()
            this.tasks.remove(key)
        }
        val task = this.tasks.getOrPut(key) {
            val future = threadPool.scheduleWithFixedDelay(
                    {
                        if (url != null) {
                            fileUpdater.download()
                        }
                        this.values.compute(key) { _, v ->
                            fileUpdater.loadValues(v?.lastModified)
                        }
                    },
                    TimeValue.timeValueSeconds(updateInterval),
                    ThreadPool.Names.SAME)
            UpdateTask(future, fileSettings)
        }
        this.tasks.put(key, task)
    }

    @Synchronized
    internal fun getUpdateInterval(index: Index, fieldName: String): Long? {
        val key = FileKey(index.name, fieldName)
        return this.tasks[key]?.settings?.updateInterval
    }

    fun getValues(index: Index, fieldName: String): Map<String, Double> {
        return getValues(index.name, fieldName)
    }

    fun getValues(indexName: String, fieldName: String): Map<String, Double> {
        val key = FileKey(indexName, fieldName)
        return this.values[key]?.values.orEmpty()
    }
}
