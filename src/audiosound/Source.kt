package audiosound

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Consumer
import java.util.function.Supplier
import javax.sound.sampled.AudioFormat.Encoding.PCM_FLOAT
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem


interface Source : Iterator<Boolean>
{
    fun getChannelCount() :Int
    fun getChannel(ch :Int) :Supplier<FloatArray>
    fun close()
}


//fun sleep(ms :Int)
//{
//    try
//    {
//        Thread.sleep(ms)
//    }
//    catch (x : InterruptedException)
//    {
//    }
//}


class SinkAdapter : Source
{
    private val source: AudioInputStream
    private var closed = false

    private val buffer : ByteBuffer
    private val channel0 = FloatArray(512)
    private val channel1 = FloatArray(512)

    constructor(stream: AudioInputStream)
    {
        //source = AudioSystem.getAudioInputStream(AUDIO_FORMAT_STEREO_FLOAT, stream)
        source = stream
        buffer = ByteBuffer.allocate(source.format.frameSize * 512)
    }

    constructor(file : File) : this(AudioSystem.getAudioInputStream(BufferedInputStream(FileInputStream(file))))
    {
    }

    // means not closed
    override fun hasNext(): Boolean
    {
        if (closed)
            return false

        var bytesRead=0
        while (buffer.position() < buffer.capacity())
        {
            val read = source.read(buffer.array(), buffer.position(), buffer.capacity()-buffer.position())
            if (read == -1)
            {
                source.close()
                while (buffer.position() < buffer.capacity())
                    buffer.put(0)
                return bytesRead > 0
            }
            buffer.position(buffer.position()+read)
            bytesRead += read
        }
        return bytesRead > 0
    }

    override fun next() :Boolean
    {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.flip()
        if (source.format.encoding == PCM_FLOAT)
        {
            val floats = buffer.asFloatBuffer()
            for (i in 0..511)
            {
                channel0[i] = floats.get(i * 2 + 0)
                channel1[i] = floats.get(i * 2 + 1)
            }
        }
        else if (source.format.channels == 1)
        {
            val shorts = buffer.asShortBuffer()
            for (i in 0..511)
            {
                channel0[i] = shorts.get(i).toFloat() / 16384
            }
        }
        else
        {
            val shorts = buffer.asShortBuffer()
            for (i in 0..511)
            {
                channel0[i] = shorts.get(i * 2 + 0).toFloat() / 16384
                channel1[i] = shorts.get(i * 2 + 1).toFloat() / 16384
            }
        }
        buffer.clear()
        return true
    }

    override fun close()
    {
        if (!closed)
        {
            source.close()
            closed = true
        }
    }

    override fun getChannelCount(): Int
    {
        return source.format.channels
    }

    override fun getChannel(i: Int): Supplier<FloatArray>
    {
        return when (i)
        {
            0 -> Supplier { channel0; }
            1 -> Supplier { channel1; }
            else -> throw IllegalArgumentException()
        }
    }
}


class Q<T>
{
    val qlock = Object()
    val list = LinkedList<T>()
    fun put(el :T)
    {
        synchronized(qlock)
        {
            list.addLast(el)
            //val size = list.size
            //if (size == 1)
                qlock.notify()
            //if (size > 100)
            //    qlock.wait()
        }
    }
    fun take() : T
    {
        synchronized(qlock)
        {
            while (true)
            {
                if (!list.isEmpty())
                {
                    val el = list.removeFirst()
                    //val size = list.size
                    //if (size < 50)
                    //    qlock.notify()
                    return el
                }
                qlock.wait();
            }
        }
        throw IllegalStateException()
    }
}


class AsyncSource(private val source :Source) : Thread("async"), Source
{
    private val sourceChannels = Array(source.getChannelCount()) {source.getChannel(it)}
    private val queue = LinkedBlockingQueue<List<FloatArray>>(100);
    private val emptyArray = FloatArray(0)
    private val EOF = List(0) {emptyArray}
    private var nextOutput :List<FloatArray> = EOF
    private var closed = false

    init {
        setDaemon(true)
        start()
    }

    override fun run()
    {
        try
        {
            source.forEach(){
                queue.put( sourceChannels.map(){it.get().clone()} )
            }
            queue.put(EOF)
        }
        catch (x :InterruptedException)
        {
            ;
        }
        finally
        {
            source.close()
        }
    }

    override fun getChannelCount(): Int
    {
        return sourceChannels.size
    }

    override fun hasNext(): Boolean
    {
        if (closed)
            return false
        nextOutput = queue.take()
        return !nextOutput.isEmpty()
    }

    override fun next(): Boolean
    {
        return !nextOutput.isEmpty()
    }

    override fun getChannel(i: Int): Supplier<FloatArray>
    {
        return Supplier { nextOutput[i] }
    }

    override fun close()
    {
        closed = true
        source.close()
        this.interrupt();
    }
}

// channelMap[index] is source channel, index is sink channel
class SimpleSinkWrapper(val source :Source, val sink :Sink, val channelMap :IntArray) :Source by source
{
    val inputChannels = Array<Supplier<FloatArray>>(channelMap.size)  {source.getChannel(channelMap[it])}
    val outputChannels = Array<Consumer<FloatArray>>(channelMap.size) {sink.getChannel(it)}

    override fun next(): Boolean
    {
        source.next()
        for (i in 0 until channelMap.size)
            outputChannels[i].accept(inputChannels[i].get())
        sink.push()
        return true;
    }
}


class RemapSource(val source :Source, map_ :IntArray) : Source by source
{
    val map :IntArray = map_.clone()

    override fun getChannel(ch: Int): Supplier<FloatArray>
    {
        return source.getChannel(map[ch])
    }
}



class LowChannel(val source :Source) : Source by source
{
    override fun getChannelCount(): Int
    {
        return source.getChannelCount() + 1
    }

    override fun getChannel(ch: Int): Supplier<FloatArray>
    {
        if (ch < source.getChannelCount())
            return source.getChannel(ch)
        val channels = Array(source.getChannelCount()) {source.getChannel(it)}
        return object : Supplier<FloatArray> {
            override fun get(): FloatArray
            {
                val ret = FloatArray(512)
                channels.forEach {
                    val data = it.get()
                    for (i in 0..511)
                        ret[i] += data[i]
                }
                // CONSIDER low-pass filter?
                return ret
            }
        }
    }
}


// TODO support multiple ganged sinks


fun wrapSink(source :Source, sink :Sink) : Source
{
    val map = IntArray(sink.getChannelCount()) {it}
    return SimpleSinkWrapper(source, sink, map)
}


fun wrapAsync(source :Source) :Source
{
    if (source is AsyncSource)
        return source
    return AsyncSource(source)
}
