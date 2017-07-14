package org.apache.kafka.connect.mongo

import org.bson.Document
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * @author Xu Jingxin
 */
data class CronJobDataMap(
    val uri: String,
    val databases: List<String>,
    val messages: ConcurrentLinkedQueue<Document>
)
