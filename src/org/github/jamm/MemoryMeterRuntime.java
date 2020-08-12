package org.github.jamm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.function.ToLongFunction;

final class MemoryMeterRuntime extends MemoryMeter
{
    private static final long byteBufferShallowSize;

    static
    {
        long bbShallowSize = 0;
        try
        {
            MethodHandle mhSizeOf = MethodHandles.lookup().findStatic(Runtime.class, "sizeOf", MethodType.methodType(long.class, Object.class));
            bbShallowSize = (long) mhSizeOf.invoke(ByteBuffer.allocate(0));
        }
        catch (Throwable ignore)
        {
        }
        byteBufferShallowSize = bbShallowSize;
    }

    static boolean hasRuntimeSizeOf()
    {
        return byteBufferShallowSize > 0 && !Boolean.getBoolean("jamm.no-runtime-sizeof");
    }

    private final ClassValue<Long> classIncludeChecks = new ClassValue<Long>()
    {
        @Override
        protected Long computeValue(Class<?> type)
        {
            return classIncludeCheck(type);
        }
    };

    private final ToLongFunction<ByteBuffer> bbTester;

    MemoryMeterRuntime(Builder builder)
    {
        super(builder);
        if (ignoreOuterClassReference)
            throw new IllegalStateException("JEP/JDK-8249196-Runtime-sizeOf does not support ignoreOuterClassReference");

        this.bbTester = createByteBufferTester();
    }

    private ToLongFunction<ByteBuffer> createByteBufferTester()
    {
        ToLongFunction<ByteBuffer> bbTester;
        switch (byteBufferMode)
        {
            case BB_MODE_NORMAL:
                bbTester = MemoryMeterRuntime::byteBufferNorm;
                break;
            case BB_MODE_OMIT_SHARED:
                bbTester = MemoryMeterRuntime::byteBufferOmitShared;
                break;
            case BB_MODE_SHALLOW:
                bbTester = MemoryMeterRuntime::byteBufferShallow;
                break;
            case BB_MODE_HEAP_ONLY_NO_SLICE:
                bbTester = MemoryMeterRuntime::byteBufferHeapOnlyNoSlice;
                break;
            default:
                throw new IllegalArgumentException("Invalid byteBufferMode = " + byteBufferMode);
        }
        return bbTester;
    }

    private static long byteBufferNorm(ByteBuffer bb)
    {
        return Runtime.DEEP_SIZE_OF_SHALLOW | Runtime.DEEP_SIZE_OF_TRAVERSE;
    }

    private static long byteBufferOmitShared(ByteBuffer bb)
    {
        return -bb.remaining() - byteBufferShallowSize;
    }

    private static long byteBufferShallow(ByteBuffer bb)
    {
        return -byteBufferShallowSize;
    }

    private static long byteBufferHeapOnlyNoSlice(ByteBuffer bb)
    {
        if (bb.isDirect())
            return Runtime.DEEP_SIZE_OF_SHALLOW;
        // if we're only referencing a sub-portion of the ByteBuffer, don't count the array overhead (assume it's slab
        // allocated, so amortized over all the allocations the overhead is negligible and better to undercount than over)
        if (bb.capacity() > bb.remaining())
            return -bb.remaining();
        return Runtime.DEEP_SIZE_OF_SHALLOW | Runtime.DEEP_SIZE_OF_TRAVERSE;
    }

    @Override
    public long measure(Object object)
    {
        // Runtime.sizeOf() can return negative values.
        long size = Runtime.sizeOf(object);
        if (size < 0L)
            throw new IllegalArgumentException("Imprecise result for object graph of type " + object.getClass());
        return size;
    }

    @Override
    public long measureDeep(Object object)
    {
        // Runtime.deepSizeOf() can return negative values.
        long size = Runtime.deepSizeOf(object, this::testObject);
        if (size < 0L)
            throw new IllegalArgumentException("Imprecise result for object graph of type " + object.getClass());
        return size;
    }

    private long testObject(Object obj)
    {
        Class<?> cls = obj.getClass();

        if (byteBufferMode != 0 && ByteBuffer.class.isAssignableFrom(cls))
            return bbTester.applyAsLong((ByteBuffer) obj);

        return classIncludeChecks.get(cls);
    }

    private long classIncludeCheck(Class<?> cls)
    {
        if (ignoreClassPredicate.test(cls))
            // don't count this object
            return 0;

        if (skipClass(cls) ||
            // TODO non-strong reference checks should really be implemented by JEP/JDK-8249196
            (ignoreNonStrongReferences && Reference.class.isAssignableFrom(cls)))
            // just this object's shallow size, don't consider non-strong object reference
            return Runtime.DEEP_SIZE_OF_SHALLOW;

        // normal path - this object's shallow size, consider object's references
        return Runtime.DEEP_SIZE_OF_SHALLOW | Runtime.DEEP_SIZE_OF_TRAVERSE;
    }

    @Override
    public long sizeOfArray(byte[] bytes)
    {
        return measure(bytes);
    }

    @Override
    public long sizeOfArray(short[] shorts)
    {
        return measure(shorts);
    }

    @Override
    public long sizeOfArray(char[] chars)
    {
        return measure(chars);
    }

    @Override
    public long sizeOfArray(int[] ints)
    {
        return measure(ints);
    }

    @Override
    public long sizeOfArray(long[] longs)
    {
        return measure(longs);
    }

    @Override
    public long sizeOfArray(float[] floats)
    {
        return measure(floats);
    }

    @Override
    public long sizeOfArray(double[] doubles)
    {
        return measure(doubles);
    }

    @Override
    public long sizeOfArray(Object[] objects)
    {
        return measure(objects);
    }
}
