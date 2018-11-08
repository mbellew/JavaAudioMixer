package audiosound

import java.lang.IllegalArgumentException
import java.util.function.Supplier


var zeros = FloatArray(512)


class Mux : Source
{
    private var sources = ArrayList<SourceController>(8)
    private var channelSlots : Array<ChannelMap?> = arrayOfNulls(8)
    private var sinks = ArrayList<Sink>()
    private var output = Array(16) {FloatArray(512)}
    private var closed = false

    override fun getChannelCount(): Int
    {
        return 16
    }

    override fun getChannel(i: Int): Supplier<FloatArray>
    {
        return Supplier {output[i]}
    }

    override fun close()
    {
        sources.forEach {it.source.close()}
        closed = true
    }

    override fun hasNext(): Boolean
    {
        if (closed)
            return false;
        _step()
        return true
    }

    override fun next(): Boolean
    {
        return !closed
    }

    // simple pipe of source slots to output lines (mostly for testing)
    fun addSource(source :Source, bind :IntArray, outputWeights :Array<FloatArray>?) : SourceController
    {
        if (source.getChannelCount() != bind.size)
            throw IllegalArgumentException()
        val sourceMap = SourceController(source, bind)
        sources.add(sourceMap)
        for (sourceChannel in bind.indices)
        {
            val mixerChannel = bind[sourceChannel]
            val weights = FloatArray(16)
            if (null == outputWeights)
                weights[sourceChannel] = 1.0f
            else
            {
                val w = outputWeights[sourceChannel]
                for (i in 0 until Math.min(w.size,16))
                    weights[i] = w[i]
            }
            channelSlots[mixerChannel] = ChannelMap(sourceMap, weights, source.getChannel(sourceChannel))
        }
        return sourceMap
    }


    fun addSource(source :Source, outputWeights :Array<FloatArray>?) : SourceController?
    {
        val channels = source.getChannelCount()
        val bind = IntArray(channels)
        var ch=0
        for (slot in channelSlots.indices)
        {
            if (null == channelSlots[slot])
                bind[ch++] = slot
            if (ch == channels)
                break
        }
        if (ch != channels)
            return null     // NO ROOM!
        return addSource(source, bind, outputWeights)
    }


    fun getSourceCount() :Int
    {
        return sources.size
    }

    private fun cleanupSources()
    {
        sources.forEach {it.inUse = false}
        channelSlots.forEach { if (null != it) it.sourceMap.inUse = true }
        val it = sources.iterator()
        while (it.hasNext())
            if (!it.next().inUse)
                it.remove()
    }

    fun addSink(sink :Sink)
    {
        sinks.add(sink)
        // TODO outputChannels
    }

    private fun _removeSource(c : SourceController)
    {
        val index = sources.indexOf(c);
        if (index == -1)
            return
        c.slots.forEach { channelSlots[it] = null; }
        sources.removeAt(index)
        c.source.close();
    }

    private fun _step()
    {
        // note we modify sources in this loop, so clone it first to keep it less confusing
        val copy = sources.toTypedArray()
        copy.forEach { sourceMap ->
            sourceMap.updateEffect()
            if (!sourceMap.source.hasNext())
                _removeSource(sourceMap)
            else
                sourceMap.source.next()
        }

        // pull input PCM data
        val data = Array(8) {zeros}
        for (i in channelSlots.indices)
        {
            val pcm = channelSlots[i]?.channelNum?.get()
            if (null != pcm)
                data[i] = pcm
        }

        // transform from input->output
        // TODO generate matrix
        val muxmatrix = Array(8) {
            if (channelSlots[it] != null) channelSlots[it]!!.weights else zeros
        }
        val mux = MixerMatrix(muxmatrix)
        mux.mix(data, output)

        // push output PCM data
        // TODO support more than one output javax.sound.mixer
        if (!sinks.isEmpty())
        {
            val sink = sinks[0]
            for (i in 0 until sink.getChannelCount())
                sink.getChannel(i).accept(output[i])
            sink.push()
        }
    }


    enum class Effect
    {
        FADE
        {
            override fun update(mux :Mux, source :SourceController){
                source.slots.forEach() {
                    if (it >= 0 && null != mux.channelSlots[it])
                    {
                        val weights = mux.channelSlots[it]!!.weights
                        var sum = 0.0f;
                        for (i in weights.indices)
                        {
                            weights[i] *= 0.98f
                            sum += weights[i]
                        }
                        if (sum < 0.01f)
                            source.stop()
                    }
                }
            }
        };
        abstract fun update(mux :Mux, source :SourceController)
    }


    inner class SourceController(val source :Source, val slots :IntArray)
    {
        var inUse = false
        var effect : Effect? = null

        public fun stop()
        {
            _removeSource(this)
        }

        public fun updateMix(channel :Int, newWeights :FloatArray)
        {
            val weights = channelSlots[slots[channel]]!!.weights
            for (i in 0 until weights.size)
                weights[i] = if (i<newWeights.size) newWeights[i] else 0.0f
        }

        public fun fade()
        {
            effect = Effect.FADE
        }

        fun updateEffect()
        {
            effect?.update(this@Mux, this);
        }
    }

    inner class ChannelMap(val sourceMap :SourceController, val weights :FloatArray, val channelNum : Supplier<FloatArray>)
}


