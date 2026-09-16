package org.kazumi.tv
import android.app.Instrumentation
import android.content.ContextWrapper
import org.kazumi.tv.data.*

object CollectionJournalRegression {
    fun run(test:Instrumentation) {
        val actual=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("journal_test_$name",mode)
        }
        val prefs=context.getSharedPreferences("tv_library",0); prefs.edit().clear().commit()
        try {
            var store=LibraryStore(context)
            val a=Subject(901,"同步测试A","",""); val b=Subject(902,"同步测试B","","")
            check(store.setCollection(a,CollectionType.PLANNED)); check(store.setCollection(b,CollectionType.WATCHING))
            val backup=store.exportBackup(); val before=store.collections(); val base=store.collectionChanges()
            check(store.changeCollections(setOf(901,902),CollectionType.WATCHED)==2)
            check(store.changeCollections(setOf(901,902),null)==2)
            store=LibraryStore(context)
            check(store.collections().isEmpty()); check(store.collectionChanges().takeLast(2).all { it.action==CollectionAction.DELETE })
            val merged=CollectionChanges.merge(before,base,store.collectionChanges())
            check(merged.collections.isEmpty()); check(CollectionChanges.merge(merged.collections,merged.changes,store.collectionChanges())==merged)
            store.restoreBackup(backup,store.backupFingerprint())
            check(store.collectionChanges().takeLast(2).all { it.action==CollectionAction.ADD })
            store.undoRestore(); check(store.collections().isEmpty())
            check(store.collectionChanges().takeLast(2).all { it.action==CollectionAction.DELETE })
            val changes=store.collectionChanges(); check(changes.map { it.id }.distinct().size==changes.size)
            prefs.edit().putString("collection_changes","{\"version\":99,\"changes\":[]}").commit()
            check(!store.setCollection(a,CollectionType.PLANNED)); check(store.collections().isEmpty())
            check(prefs.getString("collection_changes","")!!.contains("99"))
            prefs.edit().clear().putString("favorites",LibraryCodec.write(before.map(CollectionCodec::write))).commit()
            val seeds=store.collectionChanges(); check(seeds==LibraryStore(context).collectionChanges())
            check(store.setCollection(a,null)); check(store.collectionChanges().size==3)
            check(store.collectionChanges().take(2)==seeds)
            check(actual==test.targetContext.getSharedPreferences("tv_library",0).all)
        } finally { prefs.edit().clear().commit() }
    }
}
