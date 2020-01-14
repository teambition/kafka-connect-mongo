package com.teambition.kafka.connect.mongo.source

import org.bson.BsonTimestamp
import org.bson.Document
import org.bson.types.ObjectId
import java.lang.Integer.parseInt
import java.util.*

/**
 * Created by jentle on 9/27/17.
 * If start in the old format 'latest_timestamp,inc', use oplog tailing by default
 * If start in the new format 'latest_timestamp,inc,object_id,finished_import':
 *    if finished_import is true, use oplog tailing and update latest_timestamp
 *    else start mongo collection import from the object_id first then tailing
 */
class MongoSourceOffset() {
    companion object {
        enum class Pattern {
            OPLOG, CHANGESTREAMS
        }
    }

    /**
     * Offset Pattern
     * 1578990230,1 (Oldest offset only contains timestamp and inc)
     * 1578990230,1,5e1d7a96e619dad727219d36,0 (New offset contains timestamp,inc,offsetid,flag of finish initial import)
     * cs:{"_data":{...}} (New offset read from change streams, start with cs, contains resume id of change streams)
     */
    constructor(offsetString: String) : this() {
        val pieces = offsetString.trim().split(",".toRegex())

        val timestamp: Int = parseInt(pieces[0])
        val inc: Int = pieces.let { if (it.size > 1) parseInt(it[1]) else 0 }
        ts = BsonTimestamp(timestamp, inc)

        objectId = if (pieces.size > 2) ObjectId(pieces[2]) else objectId
        finishedImport = if (pieces.size > 3) parseInt(pieces[3]) > 0 else finishedImport
    }

    /**
     * Must wait for finished initial import then read from oplog
     */
    constructor(doc: Document, pattern: Pattern = Pattern.OPLOG) : this() {
        this.pattern = pattern
        when (pattern) {
            Pattern.OPLOG -> {
                ts = doc["ts"] as BsonTimestamp
                finishedImport = true
            }
            Pattern.CHANGESTREAMS -> {
                csOffset = doc["_id"] as String
                finishedImport = true
            }
        }
    }

    var dbColl = ""
    var pattern: Pattern = Pattern.OPLOG
    var ts = BsonTimestamp(0, 0)
    var objectId = ObjectId("000000000000000000000000")
    var csOffset: String? = null
    var finishedImport = false

    /**
     * Start from current time will skip a lot of redundant scan on oplog
     * Format: LATEST_TIMESTAMP,INC,OBJECT_ID,FINISH_IMPORT
     */
    override fun toString(): String {
        val finishedFlag = if (finishedImport) 1 else 0
        return "${ts.time},${ts.inc},$objectId,$finishedFlag"
    }

    // To real offset used in kafka
    fun toOffset(): Map<String, String> {
        return Collections.singletonMap(dbColl, this.toString())
    }
}
