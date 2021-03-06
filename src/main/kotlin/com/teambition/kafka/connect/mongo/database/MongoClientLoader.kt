package com.teambition.kafka.connect.mongo.database

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
import java.util.concurrent.ConcurrentHashMap

/**
 * @author Xu Jingxin
 */
object MongoClientLoader {
    private val defaultOptions
        get() = MongoClientOptions.builder()
            .connectTimeout(1_000_000)
    private val clients = ConcurrentHashMap<String, MongoClient>()

    fun getClient(uri: String, options: MongoClientOptions.Builder = defaultOptions, reconnect: Boolean = false) =
        synchronized(clients) {
            if ("ssl=true" !in uri) {
                options.sslEnabled(false)
            }
            if (reconnect) {
                clients[uri] = MongoClient(MongoClientURI(uri, options))
                clients[uri]
            } else {
                clients.getOrPut(uri) {
                    MongoClient(MongoClientURI(uri, options))
                }
            }
        }!!
}
