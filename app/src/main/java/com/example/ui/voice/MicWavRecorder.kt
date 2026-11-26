package com.example.ui.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class VoiceRecorderMeter(
  val isRecording: Boolean = false,
  val normalizedLevel: Float = 0f,
  val elapsedSeconds: Int = 0,
)

/**
 * Lightweight 16kHz mono PCM -> RIFF WAV recorder for short household voice entries.
 * Always verifies runtime [Manifest.permission.RECORD_AUDIO] before initializing [AudioRecord].
 */
class MicWavRecorder(private val context: Context) {

  private val _meter = MutableStateFlow(VoiceRecorderMeter())
  val meter: StateFlow<VoiceRecorderMeter> = _meter.asStateFlow()

  @Volatile private var stopRequested = false
  @Volatile private var cancelRequested = false

  fun hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  fun requestStop() {
    stopRequested = true
  }

  fun cancel() {
    cancelRequested = true
    stopRequested = true
  }

  @SuppressLint("MissingPermission")
  suspend fun recordUntilStopped(
    maxDurationMs: Long = MAX_DURATION_MS,
  ): Result<ByteArray> =
    withContext(Dispatchers.IO) {
      if (!hasAudioPermission()) {
        return@withContext Result.failure(SecurityException("Microphone permission not granted."))
      }

      stopRequested = false
      cancelRequested = false

      val minBufferBytes =
        AudioRecord.getMinBufferSize(
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (minBufferBytes <= 0) {
        return@withContext Result.failure(IllegalStateException("Audio input unavailable on this device."))
      }

      val bufferSizeBytes = maxOf(minBufferBytes, SAMPLE_RATE_HZ / 2)
      val recorder =
        try {
          AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes,
          )
        } catch (e: Exception) {
          return@withContext Result.failure(e)
        }

      if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        try {
          recorder.release()
        } catch (_: Exception) {
        }
        return@withContext Result.failure(IllegalStateException("Microphone failed to initialize."))
      }

      val pcmOut = ByteArrayOutputStream(SAMPLE_RATE_HZ * 2 * 6)
      val readBuffer = ShortArray(bufferSizeBytes / 2)
      val startMs = System.currentTimeMillis()

      try {
        recorder.startRecording()
        _meter.value = VoiceRecorderMeter(isRecording = true, normalizedLevel = 0.08f, elapsedSeconds = 0)

        while (currentCoroutineContext().isActive && !stopRequested) {
          val elapsedMs = System.currentTimeMillis() - startMs
          if (elapsedMs >= maxDurationMs) {
            break
          }

          val readShorts = recorder.read(readBuffer, 0, readBuffer.size)
          if (readShorts > 0) {
            var sumSq = 0.0
            val byteBuf = ByteBuffer.allocate(readShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until readShorts) {
              val s = readBuffer[i]
              byteBuf.putShort(s)
              val norm = s / 32768.0
              sumSq += norm * norm
            }
            pcmOut.write(byteBuf.array())

            val rms = sqrt(sumSq / readShorts).toFloat()
            val level = (rms * 4.5f).coerceIn(0.06f, 1f)
            _meter.value =
              VoiceRecorderMeter(
                isRecording = true,
                normalizedLevel = level,
                elapsedSeconds = (elapsedMs / 1000L).toInt(),
              )
          }
        }

        if (cancelRequested) {
          return@withContext Result.failure(CancellationException("Recording cancelled"))
        }

        val pcmBytes = pcmOut.toByteArray()
        if (pcmBytes.size < MIN_VALID_PCM_BYTES) {
          return@withContext Result.failure(
            IllegalStateException("Recording was too short. Hold and speak your entry clearly.")
          )
        }

        Result.success(wrapPcmAsWav(pcmBytes, SAMPLE_RATE_HZ, CHANNELS, BITS_PER_SAMPLE))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      } finally {
        _meter.value = VoiceRecorderMeter()
        try {
          if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            recorder.stop()
          }
        } catch (_: Exception) {
        }
        try {
          recorder.release()
        } catch (_: Exception) {
        }
      }
    }

  companion object {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val MAX_DURATION_MS = 20_000L
    private const val MIN_VALID_PCM_BYTES = 3_200 // ~0.1s minimum speech buffer

    fun wrapPcmAsWav(
      pcmData: ByteArray,
      sampleRate: Int = SAMPLE_RATE_HZ,
      channels: Int = CHANNELS,
      bitsPerSample: Int = BITS_PER_SAMPLE,
    ): ByteArray {
      val totalAudioLen = pcmData.size
      val totalDataLen = totalAudioLen + 36
      val byteRate = sampleRate * channels * (bitsPerSample / 8)
      val blockAlign = (channels * bitsPerSample / 8).toShort()

      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      header.put("RIFF".toByteArray(Charsets.US_ASCII))
      header.putInt(totalDataLen)
      header.put("WAVE".toByteArray(Charsets.US_ASCII))
      header.put("fmt ".toByteArray(Charsets.US_ASCII))
      header.putInt(16) // PCM subchunk size
      header.putShort(1) // PCM audio format = 1
      header.putShort(channels.toShort())
      header.putInt(sampleRate)
      header.putInt(byteRate)
      header.putShort(blockAlign)
      header.putShort(bitsPerSample.toShort())
      header.put("data".toByteArray(Charsets.US_ASCII))
      header.putInt(totalAudioLen)

      return header.array() + pcmData
    }
  }
}
