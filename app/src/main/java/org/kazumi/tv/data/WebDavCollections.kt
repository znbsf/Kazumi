package org.kazumi.tv.data

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class WebDavFailure(message:String,cause:Throwable?=null):Exception(message,cause)
class WebDavAccount(val directory:String,val username:String,val password:String) {
    override fun toString()="WebDavAccount(redacted)"
}
data class CollectionSnapshot(val collections:List<CollectionEntry>,val changes:List<CollectionChange>)
object CollectionSnapshotCodec {
    const val MAX_CHARS=10_000_000
    fun write(snapshot:CollectionSnapshot):String {
        require(snapshot.collections.size<=200 && snapshot.collections.distinctBy { it.subject.id }.size==snapshot.collections.size)
        val raw=JSONObject().put("format","KazumiTV-collections").put("version",1)
            .put("collections",JSONArray(snapshot.collections.map(CollectionCodec::write)))
            .put("journal",JSONObject(CollectionChanges.write(snapshot.changes))).toString()
        require(raw.length<=MAX_CHARS); return raw
    }
    fun read(raw:String):CollectionSnapshot {
        require(raw.length<=MAX_CHARS)
        val root=JSONObject(raw)
        require(root.getString("format")=="KazumiTV-collections" && root.getInt("version")==1)
        val rows=root.getJSONArray("collections"); require(rows.length()<=200)
        val entries=List(rows.length()) { CollectionCodec.read(rows.getJSONObject(it)) }
        require(entries.distinctBy { it.subject.id }.size==entries.size)
        return CollectionSnapshot(entries,CollectionChanges.read(root.getJSONObject("journal").toString()))
    }
    fun fingerprint(snapshot:CollectionSnapshot)=LibraryArchiveCodec.fingerprint(write(snapshot),"")
}
data class WebDavRemote(val snapshot:CollectionSnapshot,val etag:String?,val missing:Boolean)
data class CollectionSyncPreview(val local:CollectionSnapshot,val remote:WebDavRemote,val merged:CollectionSnapshot) {
    val added get()=merged.collections.count { next -> local.collections.none { it.subject.id==next.subject.id } }
    val removed get()=local.collections.count { old -> merged.collections.none { it.subject.id==old.subject.id } }
    val updated get()=merged.collections.count { next -> local.collections.any { it.subject.id==next.subject.id && it!=next } }
    val uploads get()=local.changes.count { c -> remote.snapshot.changes.none { it.id==c.id } }
}

/** Only a dedicated native JSON resource is touched; no Flutter Hive files or remote DELETE/MOVE. */
class WebDavCollections(private val account:WebDavAccount, client:OkHttpClient=defaultClient,allowLoopbackForTest:Boolean=false) {
    private val client=client.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).retryOnConnectionFailure(false).build()
    private val endpoint=endpoint(account.directory,allowLoopbackForTest)
    init { require(account.username.isNotBlank() && ':' !in account.username && account.username.length<=512 && account.password.isNotEmpty() && account.password.length<=2048) { "请检查WebDAV账号和密码" } }
    companion object {
        private val defaultClient=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build()
        fun endpoint(directory:String,allowLoopbackForTest:Boolean=false):HttpUrl {
            val base=directory.trim().toHttpUrl()
            require(base.isHttps || (allowLoopbackForTest && base.host in setOf("127.0.0.1","localhost","::1"))) { "WebDAV请使用HTTPS目录地址" }
            require(base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null) { "地址不能包含账号、查询参数或片段" }
            return base.newBuilder().apply { if(!base.encodedPath.endsWith('/'))addPathSegment("") }.addPathSegment("kazumitv-collections-v1.json").build()
        }
        fun strongTag(value:String?):Boolean = value!=null && value.length in 2..512 && value.first()=='"' && value.last()=='"' && value.substring(1,value.length-1).all { it.code==0x21 || it.code in 0x23..0x7e || it.code in 0x80..0xff }
    }
    private fun request()=Request.Builder().url(endpoint).header("Authorization",Credentials.basic(account.username,account.password,Charsets.UTF_8))
        .header("User-Agent","KazumiTV/0.2").header("Cache-Control","no-cache")
    private suspend fun <T> exchange(request:Request,consume:(Response)->T):T = try {
        HttpText.exchange(client,request,consume)
    } catch(e:CancellationException) { throw e } catch(e:WebDavFailure) { throw e } catch(e:Exception) { throw WebDavFailure("WebDAV连接或数据校验失败，请检查网络、目录和文件格式",e) }
    private fun checkStatus(code:Int) {
        if(code in 300..399)throw WebDavFailure("WebDAV地址发生跳转，请填写最终HTTPS目录地址")
        if(code==401 || code==403)throw WebDavFailure("WebDAV认证失败或目录无权限")
        if(code==409)throw WebDavFailure("WebDAV目录不存在，请先在服务器创建目录")
        if(code==412)throw WebDavFailure("远端收藏已变化，请重新预览后再同步")
        if(code !in 200..299)throw WebDavFailure("WebDAV服务返回HTTP $code")
    }
    suspend fun read():WebDavRemote=exchange(request().get().build()) { response ->
        if(response.code==404)WebDavRemote(CollectionSnapshot(emptyList(),emptyList()),null,true)
        else {
            checkStatus(response.code)
            if(response.code!=200)throw WebDavFailure("服务器未返回完整收藏文件，已停止同步")
            val etag=response.header("ETag")
            if(!strongTag(etag))throw WebDavFailure("服务器未提供强ETag，无法安全同步；未写入远端")
            val snapshot=CollectionSnapshotCodec.read(BoundedText.read(requireNotNull(response.body).charStream(),CollectionSnapshotCodec.MAX_CHARS))
            WebDavRemote(snapshot,etag,false)
        }
    }
    suspend fun put(expected:WebDavRemote,snapshot:CollectionSnapshot) {
        val body=CollectionSnapshotCodec.write(snapshot).toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder=request().put(body)
        if(expected.missing)builder.header("If-None-Match","*") else {
            require(strongTag(expected.etag)); builder.header("If-Match",requireNotNull(expected.etag))
        }
        exchange(builder.build()) {
            checkStatus(it.code)
            if(it.code !in setOf(200,201,204))throw WebDavFailure("服务器未确认提交完成，请重新预览确认")
        }
    }
    suspend fun preview(store:LibraryStore):CollectionSyncPreview {
        val local=store.collectionSyncSnapshot()
        val remote=read()
        val result=CollectionChanges.merge(remote.snapshot.collections,remote.snapshot.changes,local.changes)
        return CollectionSyncPreview(local,remote,CollectionSnapshot(result.collections,result.changes))
    }
    suspend fun commit(store:LibraryStore,preview:CollectionSyncPreview) {
        if(store.collectionSyncSnapshot()!=preview.local)throw WebDavFailure("预览后本地收藏已变化，请重新预览")
        put(preview.remote,preview.merged)
        val applied=try { store.applyCollectionSync(preview.local,preview.merged) } catch(_:Exception) { false }
        if(!applied)throw WebDavFailure("远端已提交，本地期间有变化或保存失败，请重新预览确认并重试")
    }
}
