package com.teambition.kafka.connect.mongo.source

import com.mongodb.BasicDBObject
import com.mongodb.util.JSON
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.ADDITIONAL_FILTER
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.ANALYZE_SCHEMA_CONFIG
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.BATCH_SIZE_CONFIG
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.DATABASES_CONFIG
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.INITIAL_IMPORT_CONFIG
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.MONGO_URI_CONFIG
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.SCHEMA_NAME_CONFIG
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.SCHEMA_REGISTRY_URL_CONFIG
import com.teambition.kafka.connect.mongo.source.MongoSourceConfig.Companion.TOPIC_PREFIX_CONFIG
import org.apache.commons.lang.StringUtils
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.utils.AppInfoParser
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.source.SourceConnector
import org.apache.kafka.connect.util.ConnectorUtils
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Connect mongodb with configs
 */
open class MongoSourceConnector : SourceConnector() {
    protected open val log = LoggerFactory.getLogger(MongoSourceConnector::class.java)!!
    private var databases: String = ""
    private var uri: String = ""
    private var batchSize: String = ""
    private var initialImport: String = ""
    private var topicPrefix: String = ""
    private var schemaName: String = ""
    private var analyzeSchema: String = ""
    private var schemaRegistryUrl: String = ""
    private var additionalFilter: String = ""

    override fun version(): String = AppInfoParser.getVersion()

    override fun taskClass(): Class<out Task> = MongoSourceTask::class.java

    override fun start(props: Map<String, String>) {
        log.trace("Parsing configuration: {}", props)
        databases = getRequiredProp(props, DATABASES_CONFIG)
        batchSize = getRequiredProp(props, BATCH_SIZE_CONFIG)
        initialImport = getRequiredProp(props, INITIAL_IMPORT_CONFIG)
        uri = getRequiredProp(props, MONGO_URI_CONFIG)
        topicPrefix = getRequiredProp(props, TOPIC_PREFIX_CONFIG)
        schemaName = getRequiredProp(props, SCHEMA_NAME_CONFIG)
        analyzeSchema = props[ANALYZE_SCHEMA_CONFIG] ?: "false"
        additionalFilter = props[ADDITIONAL_FILTER]
            ?.takeIf { it.isNotEmpty() }
            ?.also { JSON.parse(it) as BasicDBObject }
            ?: ""

        if (analyzeSchema == "true") {
            schemaRegistryUrl = getRequiredProp(props, SCHEMA_REGISTRY_URL_CONFIG)
        }
    }

    /**
     * Group tasks by number of dbs
     */
    override fun taskConfigs(maxTasks: Int): MutableList<MutableMap<String, String>> {
        val configs = mutableListOf<MutableMap<String, String>>()
        val dbs = databases.split(",").dropLastWhile(String::isEmpty)
        val numGroups = Math.min(dbs.size, maxTasks)
        val dbsGrouped = ConnectorUtils.groupPartitions(dbs, numGroups)

        for (i in 0 until numGroups) {
            val config = HashMap<String, String>()
            config[MONGO_URI_CONFIG] = uri
            config[DATABASES_CONFIG] = StringUtils.join(dbsGrouped[i], ",")
            config[INITIAL_IMPORT_CONFIG] = initialImport
            config[BATCH_SIZE_CONFIG] = batchSize
            config[TOPIC_PREFIX_CONFIG] = topicPrefix
            config[SCHEMA_NAME_CONFIG] = schemaName
            config[ANALYZE_SCHEMA_CONFIG] = analyzeSchema
            config[SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl
            config[ADDITIONAL_FILTER] = additionalFilter
            configs.add(config)
        }
        return configs
    }

    override fun stop() {}

    override fun config(): ConfigDef = MongoSourceConfig.config

    private fun getRequiredProp(props: Map<String, String>, key: String): String {
        val value = props[key]
        if (value == null || value.isEmpty()) {
            throw ConnectException("Missing $key config")
        }
        return value
    }
}
