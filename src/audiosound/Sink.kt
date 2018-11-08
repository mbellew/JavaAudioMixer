package audiosound

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Consumer
import javax.sound.sampled.*
import javax.sound.sampled.AudioFormat.Encoding.PCM_FLOAT


interface Sink
{
    fun getChannelCount() :Int
    fun getChannel(i :Int) : Consumer<FloatArray>
    fun push()
    fun drain()
}


/** Wraps a javax.sound.Mixer and manages opening/writing to its output SourceDataLine */

class MultiChannelSinkAdapter : Sink
{
    private val mixer :Mixer
    private val line :SourceDataLine
    private val channels :Int

    private val buffer : ByteBuffer
    private val data :Array<FloatArray>

    constructor(mixer :Mixer, line :SourceDataLine, channels :Int)
    {
        this.mixer = mixer
        this.channels = channels
        this.data = Array(channels) {FloatArray(512)}
        this.buffer = ByteBuffer.allocate(4 * channels * 512)
        val format = AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100.0f, 32, channels, channels*4, 44100.0f, false)
        this.line = line
        line.open(format, 3 * format.frameSize * 512)
    }

    constructor(mixer :Mixer, channels :Int)
    {
        this.mixer = mixer
        this.channels = channels
        this.data = Array(channels) {FloatArray(512)}
        this.buffer = ByteBuffer.allocate(4 * channels * 512)
        //val format = AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100.0f, 32, slots, slots*4, 44100.0f, false)
        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100.0f, 16, channels, channels*2, 44100.0f, false)
        this.line = mixer.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
        line.open(format)
    }

    override fun push()
    {
        buffer.clear()

        if (line.format.encoding == PCM_FLOAT)
        {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val fb = buffer.asFloatBuffer()
            for (i in 0..511)
                for (j in 0 until channels)
                    fb.put(data[j][i])
            buffer.position(fb.position()*4)
        }
        else
        {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val sb = buffer.asShortBuffer()
            for (i in 0..511)
                for (j in 0 until channels)
                {
                    val f =  Math.min(1.0f, Math.max(-1.0f, data[j][i]))
                    sb.put((f * 16384).toShort())
                }
            buffer.position(sb.position()*2)
        }

        line.write(buffer.array(), 0, buffer.position())

        // if line is not already running start it when buffer is more than half full
        if (!line.isRunning) // && line.available() < line.bufferSize/2)
            line.start()
    }

    override fun getChannelCount(): Int
    {
        return channels
    }

    override fun getChannel(i: Int): Consumer<FloatArray>
    {
        return Consumer { pcm -> data[i] = pcm }
    }

    override fun drain()
    {
        if (line.isActive)
            line.drain()
    }
}


fun main(args :Array<String>)
{
    val mixers = AudioSystem.getMixerInfo()

    var outputMixerInfo :Mixer.Info? = null
    var outputLineInfo :Line.Info? = null

    mixers.forEach { mixerInfo ->
        val m = AudioSystem.getMixer(mixerInfo)
        System.out.println()
        System.out.println("$mixerInfo")
        System.out.println("name: ${mixerInfo.name}\ndescription: ${mixerInfo.description}\nvendor:${mixerInfo.vendor}\nversion: ${mixerInfo.version}")
        m.sourceLineInfo.forEach {
            System.out.println("\t${it.lineClass} $it")
            if (mixerInfo.name.startsWith("Port Device") && mixerInfo.description.contains("USB") &&
                    it.toString().contains("Speaker"))
            {
                outputMixerInfo = mixerInfo
                outputLineInfo = it
            }
        }
        m.targetLineInfo.forEach {
            System.out.println("\t${it.lineClass} $it")
        }
    }

    if (null == outputLineInfo || null == outputMixerInfo)
        return
}