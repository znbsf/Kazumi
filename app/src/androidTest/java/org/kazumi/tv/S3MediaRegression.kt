package org.kazumi.tv

import android.app.Instrumentation
import android.content.Intent
import android.media.AudioManager
import androidx.media3.session.MediaController
import org.kazumi.tv.playback.NativePlayer
import org.kazumi.tv.playback.PlaybackRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Silent local sample; never reads or mutates the user's library or credentials. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object S3MediaRegression {
    @Suppress("DEPRECATION")
    fun run(test: Instrumentation, policy: org.kazumi.tv.playback.ResourcePolicy? = null) {
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val sample = java.io.File(test.targetContext.cacheDir, "s3-silent-fixture.wav")
        val pcmSize = 8000 * 2 * 30
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(pcmSize + 36).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcmSize).array()
        sample.outputStream().use { it.write(header); it.write(ByteArray(pcmSize)) }
        lateinit var engine: NativePlayer
        var engineCreated = false
        var controller: MediaController? = null
        val audio = activity.getSystemService(AudioManager::class.java)
        val focusListener = AudioManager.OnAudioFocusChangeListener { }
        fun awaitState(label: String, predicate: () -> Boolean) {
            repeat(80) {
                var ready = false
                test.runOnMainSync { ready = predicate() }
                if (ready) return
                Thread.sleep(100)
            }
            error("Timed out: $label")
        }
        try {
            test.runOnMainSync {
                engine = NativePlayer(activity, sample.toURI().toString(), resourcePolicy=policy ?: org.kazumi.tv.playback.DeviceResourcePolicy.read(activity, sample.toURI().toString()))
                engineCreated = true
                // Overlapping engines must never collide on the session ID.
                NativePlayer(activity).also { it.release(); it.release() }
                engine.player.volume = 0f
                engine.open(PlaybackRequest(sample.toURI().toString(), emptyMap(), "S3 silent fixture"))
            }
            awaitState("local audio playing") { engine.player.isPlaying }
            lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
            test.runOnMainSync { future = MediaController.Builder(activity, engine.sessionToken!!).buildAsync() }
            controller = future.get(10, TimeUnit.SECONDS)
            awaitState("controller title") { controller.mediaMetadata.title?.toString() == "S3 silent fixture" }
            test.runOnMainSync { controller.pause() }
            awaitState("remote pause") { !engine.player.playWhenReady }
            test.runOnMainSync { controller.seekTo(5000); controller.play() }
            awaitState("remote seek/play") { engine.player.isPlaying && engine.player.currentPosition >= 5000 }
            test.runOnMainSync {
                check(audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
            awaitState("competing audio focus pauses playback") { !engine.player.playWhenReady }
            test.runOnMainSync { audio.abandonAudioFocus(focusListener); controller.play() }
            awaitState("explicit resume") { engine.player.isPlaying }
            test.runOnMainSync { engine.setForegroundActive(false) }
            awaitState("background session disconnected") { !controller.isConnected && !engine.player.playWhenReady && engine.sessionToken == null }
            test.runOnMainSync { engine.setForegroundActive(true) }
            awaitState("foreground returns paused") { engine.sessionToken != null && !engine.player.playWhenReady }
        } finally {
            test.runOnMainSync {
                audio.abandonAudioFocus(focusListener)
                controller?.release()
                if (engineCreated) { engine.release(); engine.release() }
                activity.finish()
            }
            sample.delete()
        }
    }
}
