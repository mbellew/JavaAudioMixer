package audiosound

class MixerMatrix
{
    val INPUT_CHANNELS = 8;
    val OUTPUT_LINES = 16;
    val SAMPLE_SIZE = 512;

    var matrix: FloatArray = FloatArray(INPUT_CHANNELS*OUTPUT_LINES);

    constructor()
    {
        for (i in 0..7)
            matrix.set(i*INPUT_CHANNELS+i, 1.0f);
    }

    constructor( src : Array<FloatArray> )
    {
        update(src);
    }

    fun update(src : Array<FloatArray>)
    {
        for (i in 0..INPUT_CHANNELS-1)
            for (j in 0..OUTPUT_LINES-1)
                matrix.set(i*INPUT_CHANNELS+j, src.get(i).get(j));
    }


    fun mix(samples :Array<FloatArray> , output: Array<FloatArray>)
    {
        val ch0 = samples.get(0);
        val ch1 = samples.get(1);
        val ch2 = samples.get(2);
        val ch3 = samples.get(3);
        val ch4 = samples.get(4);
        val ch5 = samples.get(5);
        val ch6 = samples.get(6);
        val ch7 = samples.get(7);

        for (sample in 0..SAMPLE_SIZE-1)
        {
            for (line in 0..OUTPUT_LINES-1)
            {
                output.get(line).set(sample,
                        ch0.get(sample) * matrix.get(0 * INPUT_CHANNELS + line) +
                        ch1.get(sample) * matrix.get(1 * INPUT_CHANNELS + line) +
                        ch2.get(sample) * matrix.get(2 * INPUT_CHANNELS + line) +
                        ch3.get(sample) * matrix.get(3 * INPUT_CHANNELS + line) +
                        ch4.get(sample) * matrix.get(4 * INPUT_CHANNELS + line) +
                        ch5.get(sample) * matrix.get(5 * INPUT_CHANNELS + line) +
                        ch6.get(sample) * matrix.get(6 * INPUT_CHANNELS + line) +
                        ch7.get(sample) * matrix.get(7 * INPUT_CHANNELS + line)
                )
            }
        }
    }


    fun mixi(samples :Array<FloatArray> , lines:Int, output: FloatArray)
    {
        val ch0 = samples.get(0);
        val ch1 = samples.get(1);
        val ch2 = samples.get(2);
        val ch3 = samples.get(3);
        val ch4 = samples.get(4);
        val ch5 = samples.get(5);
        val ch6 = samples.get(6);
        val ch7 = samples.get(7);

        for (sample in 0..SAMPLE_SIZE-1)
        {
            for (line in 0..lines-1)
            {
                output.set( sample*lines + line,
                        ch0.get(sample) * matrix.get(0 * INPUT_CHANNELS + line) +
                        ch1.get(sample) * matrix.get(1 * INPUT_CHANNELS + line) +
                        ch2.get(sample) * matrix.get(2 * INPUT_CHANNELS + line) +
                        ch3.get(sample) * matrix.get(3 * INPUT_CHANNELS + line) +
                        ch4.get(sample) * matrix.get(4 * INPUT_CHANNELS + line) +
                        ch5.get(sample) * matrix.get(5 * INPUT_CHANNELS + line) +
                        ch6.get(sample) * matrix.get(6 * INPUT_CHANNELS + line) +
                        ch7.get(sample) * matrix.get(7 * INPUT_CHANNELS + line)
                )
            }
        }
    }
}


fun main(args : Array<String>)
{
    val map = Array(8) {i -> FloatArray(16)}
    for (i in 0..7)
    {
        map.get(i).set(i,1.0f);
        map.get(i).set(i+8,1.0f);
    }
    val mixer = MixerMatrix(map);


    val samples = Array(16)  {i -> FloatArray(512) {j -> Math.random().toFloat()} }
    val output = Array(16)  {i -> FloatArray(512)}

    val start = System.currentTimeMillis();
    for (i in 0..16_000)
        mixer.mix(samples,output);
    System.out.printf("%f\n", (System.currentTimeMillis()-start)/1000.0);

    val outputi = FloatArray(16*512)
    val starti = System.currentTimeMillis();
    for (i in 0..16_000)
        mixer.mixi(samples,16, outputi);
    System.out.printf("%f\n", (System.currentTimeMillis()-starti)/1000.0);
}
