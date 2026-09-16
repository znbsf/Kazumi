package org.kazumi.tv.download

import org.json.JSONObject

data class ReplacementMedia(val id:String,val url:String,val mime:String?,val metadata:String)
enum class ReplacementStage { REMOVE, ADD }
data class DownloadReplacement(val media:ReplacementMedia,val stage:ReplacementStage=ReplacementStage.REMOVE) {
    fun json():String=JSONObject().put("version",1).put("id",media.id).put("url",media.url).put("mime",media.mime ?: JSONObject.NULL)
        .put("metadata",media.metadata).put("stage",stage.name).toString()
    companion object {
        fun read(raw:String):DownloadReplacement {
            require(raw.length<=256*1024)
            val value=JSONObject(raw); require(value.getInt("version")==1)
            val id=value.getString("id"); require(id.matches(Regex("[0-9a-f]{64}")))
            val url=value.getString("url"); val uri=java.net.URI(url)
            require(uri.scheme in listOf("http","https") && !uri.host.isNullOrBlank() && uri.userInfo==null)
            val metadata=value.getString("metadata"); val decoded=DownloadMetadata.read(metadata.toByteArray(Charsets.UTF_8))
            require(id==DownloadMetadata.id(decoded.subject.id,decoded.resumeKey))
            return DownloadReplacement(ReplacementMedia(id,url,if(value.isNull("mime"))null else value.getString("mime"),metadata),ReplacementStage.valueOf(value.getString("stage")))
        }
    }
}
enum class ReplacementAction { REMOVE, STAGE_ADD, ADD, FINISH, WAIT, CONFLICT }
object ReplacementRecovery {
    fun next(pending:DownloadReplacement,current:ReplacementMedia?,removing:Boolean):ReplacementAction = when(pending.stage) {
        ReplacementStage.REMOVE -> if(current==null)ReplacementAction.STAGE_ADD else if(removing)ReplacementAction.WAIT else ReplacementAction.REMOVE
        ReplacementStage.ADD -> when { current==null -> ReplacementAction.ADD; removing -> ReplacementAction.WAIT; current==pending.media -> ReplacementAction.FINISH; else -> ReplacementAction.CONFLICT }
    }
}
