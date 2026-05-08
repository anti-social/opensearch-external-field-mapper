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

package company.evo.opensearch.indices

import org.apache.logging.log4j.LogManager
import org.opensearch.common.lifecycle.AbstractLifecycleComponent
import org.opensearch.common.unit.TimeValue
import org.opensearch.env.Environment
import org.opensearch.env.NodeEnvironment
import org.opensearch.threadpool.Scheduler
import org.opensearch.threadpool.ThreadPool
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ExternalFileService internal constructor(
    nodeEnv: NodeEnvironment,
    private val threadPool: ThreadPool,
) : AbstractLifecycleComponent() {

    private val logger = LogManager.getLogger(this::javaClass)
    val externalDataDir = nodeEnv.sharedDataPath().resolve(EXTERNAL_DIR_NAME)
        ?: throw IllegalStateException(
            "${Environment.PATH_SHARED_DATA_SETTING} setting must be specified"
        )
    private val mapFileProviders = ConcurrentHashMap<String, ExternalFileValues.Provider>()

    @Volatile
    private var refreshTask: Scheduler.Cancellable? = null

    companion object {
        const val EXTERNAL_DIR_NAME = "external_files"
        const val REFRESH_INTERVAL_SECONDS = 30L

        private var lateInstance = AtomicReference<ExternalFileService>()
        val instance: ExternalFileService
            get() {
                return lateInstance.get()
                    ?: throw IllegalStateException("ExternalFileService is not initialized")
            }
    }

    init {
        if (!lateInstance.compareAndSet(null, this)) {
            throw IllegalStateException("ExternalFileService must exist in a single copy")
        }
    }

    override fun doStart() {
        refreshTask = threadPool.scheduleWithFixedDelay(
            { refreshCurrentVersions() },
            TimeValue.timeValueSeconds(REFRESH_INTERVAL_SECONDS),
            ThreadPool.Names.GENERIC,
        )
    }

    override fun doStop() {
        refreshTask?.cancel()
        refreshTask = null
    }

    override fun doClose() {
        refreshTask?.cancel()
        refreshTask = null
        reset()
    }

    fun reset() {
        mapFileProviders.values.forEach { provider ->
            try {
                provider.close()
            } catch (e: Throwable) {
                logger.warn("Failed to close external file provider", e)
            }
        }
        mapFileProviders.clear()
    }

    fun addFile(
        indexName: String,
        fieldName: String,
        mapName: String,
        sharding: Boolean,
        numShards: Int,
        useMemorySegments: Boolean,
    ) {
        val extDir = getExternalFileDir(mapName)

        // compute() executes atomically per key
        mapFileProviders.compute(mapName) { _, existing ->
            if (existing == null) {
                logger.info("Adding external file field: {index=$indexName, field=$fieldName, path=$extDir, sharding=$sharding, numShards=$numShards}")
                ExternalFileValues.Provider(extDir, sharding, numShards, useMemorySegments)
            } else if (
                existing.dir != extDir ||
                existing.sharding != sharding ||
                existing.numShards != numShards ||
                existing.useMemorySegments != useMemorySegments
            ) {
                logger.warn(
                    "Conflicting external_file parameters for map=$mapName " +
                        "(index=$indexName, field=$fieldName): " +
                        "existing dir=${existing.dir} sharding=${existing.sharding} " +
                        "numShards=${existing.numShards} useMemorySegments=${existing.useMemorySegments}, " +
                        "incoming dir=$extDir sharding=$sharding " +
                        "numShards=$numShards useMemorySegments=$useMemorySegments. " +
                        "Keeping the existing provider."
                )
                existing
            } else {
                existing
            }
        }
    }

    fun refreshCurrentVersions() {
        mapFileProviders.forEach { (mapName, provider) ->
            try {
                provider.refreshCurrentVersions()
            } catch (e: Throwable) {
                logger.warn("Failed to refresh current versions for map=$mapName", e)
            }
        }
    }

    fun getValues(mapName: String, keyType: ExternalFieldKeyType, shardId: Int?): ExternalFileValues {
        val provider = mapFileProviders[mapName] ?: return EmptyFileValues
        return provider.getValues(keyType, shardId)
    }

    fun getExternalFileDir(name: String): Path {
        return externalDataDir.resolve(name)
    }
}
