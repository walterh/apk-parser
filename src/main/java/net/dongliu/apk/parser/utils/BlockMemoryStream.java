
package net.dongliu.apk.parser.utils;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.dongliu.apk.parser.exception.ArgumentException;
import net.dongliu.apk.parser.exception.ArgumentOutOfRangeException;
import net.dongliu.apk.parser.exception.InvalidOperationException;

public class BlockMemoryStream implements Closeable {

    public enum SeekOrigin {
        Begin, Current, End
    }

    /*
     * Internal List of byte[] buffers
     */
    private List<byte[]> internalBufferList;

    /*
     * current position
     */
    private int currentPosition;

    /*
     * The buffer in mBuffList which is currently being pointed to (mPosn)
     */
    private int bufferIndex = 0;

    /*
     * byte index within the current byte[] buffer
     */
    private int byteIdx = 0;

    /*
     * total number of bytes in the memory stream
     */
    private int internalLength;

    /*
     * total capacity of the memory stream
     */
    private int internalCapacity;

    /*
     * indicates if the stream is currently writeable (only false after it is disposed)
     */
    private boolean isWritable;

    /*
     * is the stream readable (only false after it is disposed)
     */
    private boolean isOpen;

    /*
     * Size of the allocation block.
     */
    private int maximumBlockSize;

    /*
     * Suppress disposal.
     */
    private boolean disableDispose;

    public final static int DefaultBlockAllocationSize = 81920;

    private final static int MaxStreamLength = Integer.MAX_VALUE;

    public BlockMemoryStream() throws Exception {
        this(0);
    }

    public BlockMemoryStream(int capacity) throws Exception {
        this(capacity, DefaultBlockAllocationSize);
    }

    public BlockMemoryStream(int capacity, int maxBlockSize) throws Exception {
        if (capacity < 0) {
            throw new ArgumentOutOfRangeException("capacity");
        } else if (maxBlockSize <= 0) {
            throw new ArgumentOutOfRangeException("maxBlockSize");
        }

        maximumBlockSize = maxBlockSize;

        internalCapacity = capacity;
        allocateBlockList(capacity);

        isWritable = true;
        isOpen = true;
    }

    @SuppressWarnings("unused")
    private void getBuffer(int buffIndex, int totalSent, Ref<byte[]> byteBuff, Ref<Integer> length) {

        byteBuff.set(internalBufferList.get(buffIndex));

        int bufferCount = (internalBufferList != null) ? internalBufferList.size() : 0;

        if (buffIndex == bufferCount - 1) {
            // The last byteBuff may contain actual data less than its capacity.
            length.set(internalLength - totalSent);
        } else {
            length.set(byteBuff.get().length);
        }
    }

    private void allocateBlockList(int capacity) {
        //Debug.Assert(capacity >= 0);
        addBlockListCapacity(capacity, true);
    }

    /*
     * If capacity is less than mMaxBlockSize, only 1 byteBuff is created
     * and its size is equal to capacity.  Otherwise, size is mMaxBlockSize
     * 
     * @param capacity = the additional capacity needed
     * @param initial = initial
     */
    private void addBlockListCapacity(int capacity, boolean initial) {
        int count = capacity / maximumBlockSize; // integer division
        // TODO
        int rem = capacity % maximumBlockSize; // calculate the remainder
        int countTotal = count;

        if (rem != 0) {
            // add additional for remainder
            countTotal++;
        }

        if (initial) {
            // make size equal to capacity
            internalBufferList = new ArrayList<byte[]>(countTotal);
        }

        // initialize the new capacity
        for (int i = 0; i < count; ++i) {
            internalBufferList.add(new byte[maximumBlockSize]);
        }

        if (rem > 0) {
            // add the remainder as a separate buff
            internalBufferList.add(new byte[rem]);
        }
    }

    private void resetBlockList() {
        if (internalBufferList != null) {
            internalBufferList.clear();
            internalBufferList = null;
        }
    }

    private void resizeBlockList(int capacity) {
        int moreCapacity = capacity - internalCapacity;

        if (moreCapacity > 0) {
            addBlockListCapacity(moreCapacity, false);
        }
    }

    /*
     * gets the number of bytes allocated for this stream.
     */
    @SuppressWarnings("unused")
    private int getCapacity() throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        }
        return internalCapacity;
    }

    private void setCapacity(int capacity) throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        } else if (capacity < internalLength) {
            throw new ArgumentOutOfRangeException("value");
        }

        if (capacity != internalCapacity) {
            if (capacity > 0) {
                resizeBlockList(capacity);
            } else {
                // <= 0 means reset
                resetBlockList();
            }

            internalCapacity = capacity;
        }
    }

    /*
     * Resizes the internal blocks so that "capacity" can be written without
     * another resize
     * 
     * @param capacity = desired capacity
     * @returns true if new blocks were allocated
     */
    private boolean ensureCapacity(int capacity) throws Exception {
        if (capacity < 0) {
            throw new ArgumentOutOfRangeException("capacity");
        }

        if (capacity > internalCapacity) {
            int newCapacity = capacity;
            // minimum larger size
            if (newCapacity < 1024) {
                newCapacity = 1024;
            }

            if (newCapacity < maximumBlockSize) {
                // [walterh] bugbug
                // if the new capacity is below the mMaxBlockSize, use MemoryStream logic to increase the size
                if (newCapacity < internalCapacity << 1) { // Multiplicative factor of 2
                    newCapacity = internalCapacity << 1; // Multiplicative factor of 2
                }
            } else {
                // [walterh] bugbug
                // calculate the number of buffers that we'll need
                int buffCount = newCapacity / maximumBlockSize; // integer div
                int remainder = newCapacity % maximumBlockSize; // mod calculates remainder
                if (remainder > 0) {
                    buffCount++; // account for the remainder
                }

                newCapacity = buffCount * maximumBlockSize;
            }

            setCapacity(newCapacity);

            return true;
        }
        return false;
    }

    private void clearBlockList(int buffOffset, int count) {
        int buffIndex = bufferIndex;
        int byteIndex = byteIdx;

        while (count > 0) {
            int buffByteCount = internalBufferList.get(buffIndex).length - byteIndex;
            if (count < buffByteCount) {
                buffByteCount = count;
            }

            Arrays.fill(internalBufferList.get(buffIndex), byteIndex, byteIndex + buffByteCount, (byte) 0);
            //Array.Clear(mBuffList[buffIndex], byteIndex, buffByteCount);

            count -= buffByteCount;
            assert (count >= 0);
            ++buffIndex;
            byteIndex = 0;
        }
    }

    /*
     * Move the current position specified by offset.
     * 
     * @param offset = amount to offset
     */
    private void moveBlockListIndices(int offset) {
        if (currentPosition == 0) {
            byteIdx = 0;
            bufferIndex = 0;
        } else {
            int byteIndex = byteIdx;
            int buffIndex = bufferIndex;

            if (offset > 0) {
                // Move forward.
                for (buffIndex = bufferIndex; buffIndex < internalBufferList.size(); ++buffIndex) {
                    if ((byteIndex + offset) < internalBufferList.get(buffIndex).length) {
                        byteIndex += offset;
                        break;
                    } else {
                        offset -= (internalBufferList.get(buffIndex).length - byteIndex);
                        byteIndex = 0;
                    }
                }
            } else if (offset < 0) {
                // Move backward.
                offset = -1 * offset;
                for (buffIndex = bufferIndex; buffIndex >= 0; --buffIndex) {
                    if ((byteIndex - offset) >= 0) {
                        byteIndex -= offset;
                        break;
                    } else {
                        offset -= (byteIndex + 1);
                        byteIndex = internalBufferList.get(buffIndex - 1).length - 1;
                    }
                }
            }

            if (bufferIndex != buffIndex) {
                bufferIndex = buffIndex;
            }
            if (byteIdx != byteIndex) {
                byteIdx = byteIndex;
            }
        }
    }

    private void getBlockListIndices(int virtualIndex, Ref<Integer> buffIndex, Ref<Integer> byteIndex) {
        int index = 0;
        buffIndex.set(-1);
        byteIndex.set(-1);

        for (int i = 0; i < internalBufferList.size(); ++i) {
            if ((index <= virtualIndex) && (virtualIndex <= (index + internalBufferList.get(i).length - 1))) {
                buffIndex.set(i);
                byteIndex.set(virtualIndex - index);
                break;
            }

            index += internalBufferList.get(i).length;
        }

        assert (buffIndex.get() != -1 && byteIndex.get() != -1);
    }

    private void copyFromBlockList(int buffListOffset, byte[] byteBuff, int bufferOffset, int count) {
        copyBlockList(true, buffListOffset, byteBuff, bufferOffset, count);
    }

    private void copyToBlockList(int buffListOffset, byte[] byteBuff, int bufferOffset, int count) {
        copyBlockList(false, buffListOffset, byteBuff, bufferOffset, count);
    }

    private void copyBlockList(boolean fromBlockList, int buffOffset, byte[] dst, int dstOffset, int count) {
        int buffIndex = 0;
        int byteIndex = 0;

        Ref<Integer> buffIndexRef = new Ref<Integer>(0);
        Ref<Integer> byteIndexRef = new Ref<Integer>(0);

        if (buffOffset != currentPosition) {
            getBlockListIndices(buffOffset, buffIndexRef, byteIndexRef);
            buffIndex = buffIndexRef.get();
            byteIndex = byteIndexRef.get();
        } else {
            buffIndex = bufferIndex;
            byteIndex = byteIdx;
        }

        while (count > 0) {
            int buffByteCount = internalBufferList.get(buffIndex).length - byteIndex;
            if (count < buffByteCount) {
                buffByteCount = count;
            }

            if (fromBlockList) {
                System.arraycopy(internalBufferList.get(buffIndex), byteIndex, dst, dstOffset, buffByteCount);
            } else {
                System.arraycopy(dst, dstOffset, internalBufferList.get(buffIndex), byteIndex, buffByteCount);
            }

            dstOffset += buffByteCount;
            count -= buffByteCount;
            assert (count >= 0);
            ++buffIndex;
            byteIndex = 0;
        }
    }

    public boolean canRead() {
        return isOpen;
    }

    public boolean canSeek() {
        return isOpen;
    }

    public boolean canWrite() {
        return isWritable;
    }

    public boolean getDisableDispose() {
        return disableDispose;
    }

    public void setDisableDispose(boolean val) {
        disableDispose = val;
    }

    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public void close() {
        if (disableDispose == false) {
            try {
                isOpen = false;
                isWritable = false;
            } finally {
                resetBlockList();
            }
        }
    }

    public void flush() {
        // do nothing since all the memory is committed.
    }

    public long getLength() throws InvalidOperationException {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        }
        return internalLength;
    }

    public long getPosition() throws InvalidOperationException {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        }
        return currentPosition;
    }

    public void setPosition(long posn) throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        } else if (posn < 0) {
            throw new ArgumentOutOfRangeException("value");
        } else if (posn > MaxStreamLength) {
            throw new ArgumentOutOfRangeException("value");
        }

        // Update the current position.
        int offset = (int) posn - currentPosition;
        currentPosition += offset;
        moveBlockListIndices(offset);
    }

    public int read(byte[] buffer, int offset, int count) throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        } else if (buffer == null) {
            throw new IllegalArgumentException("buffer");
        } else if (offset < 0) {
            throw new ArgumentOutOfRangeException("offset");
        } else if (count < 0) {
            throw new ArgumentOutOfRangeException("count");
        } else if (buffer.length - offset < count) {
            throw new ArgumentException("invalid offset");
        }

        if (count == 0) {
            return 0;
        }

        int n = internalLength - currentPosition;
        if (n > count) {
            n = count;
        }
        if (n <= 0) {
            return 0;
        }

        assert (currentPosition + n >= 0); // len is less than 2^31 -1.

        copyFromBlockList(currentPosition, buffer, offset, n);

        // Update current position.
        currentPosition += n;
        moveBlockListIndices(n);

        return n;
    }

    public byte readByte() throws InvalidOperationException {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        }

        if (currentPosition >= internalLength) {
            return -1;
        }

        byte b = internalBufferList.get(bufferIndex)[byteIdx];

        // Update current position.
        currentPosition++;
        moveBlockListIndices(1);

        return b;
    }

    public long seek(long offset, SeekOrigin loc) throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        } else if (offset > MaxStreamLength) {
            throw new ArgumentOutOfRangeException("offset");
        }

        switch (loc) {
        case Begin:
            if (offset < 0) {
                throw new ArgumentOutOfRangeException("offset");
            }

            // Update current position.
            int diff = (int) offset - currentPosition;
            currentPosition += diff;
            moveBlockListIndices(diff);
            break;

        case Current:
            if (offset + currentPosition < 0) {
                throw new ArgumentOutOfRangeException("offset");
            }
            // Update current position.
            currentPosition += (int) offset;
            moveBlockListIndices((int) offset);
            break;

        case End:
            if (internalLength + offset < 0) {
                throw new ArgumentOutOfRangeException("offset");
            }
            // Update current position.
            diff = internalLength + (int) offset - currentPosition;
            currentPosition += diff;
            moveBlockListIndices(diff);
            break;

        default:
            throw new ArgumentException("NoSuchOrigin");
        }

        return currentPosition;
    }

    /*
     * Sets the length of the stream to a given value.  The new value
     * must be less than the space remaining in the array
     */
    public void setLength(long len) throws Exception {
        if (!isWritable) {
            throw new InvalidOperationException("StreamNotWritableAfterDispose");
        } else if (len > MaxStreamLength) {
            throw new ArgumentOutOfRangeException("len");
        } else if (len < 0 || len > (Integer.MAX_VALUE)) {
            throw new ArgumentOutOfRangeException("len");
        }

        int newLength = (int) len;
        boolean allocatedNewArray = ensureCapacity(newLength);

        if (!allocatedNewArray && newLength > internalLength) {
            clearBlockList(internalLength, newLength - internalLength);
        }

        internalLength = newLength;
        if (currentPosition > newLength) {
            // Update current position.
            int offset = newLength - currentPosition;
            currentPosition += offset;
            moveBlockListIndices(offset);
        }
    }

    public void write(byte[] buffer, int offset, int count) throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        } else if (!isWritable) {
            throw new InvalidOperationException("StreamNotWritableAfterDispose");
        } else if (buffer == null) {
            throw new IllegalArgumentException("buffer");
        } else if (offset < 0) {
            throw new ArgumentOutOfRangeException("offset");
        } else if (count < 0) {
            throw new ArgumentOutOfRangeException("count");
        } else if (buffer.length - offset < count) {
            throw new ArgumentException(String.format("Argument_InvalidOffLen: buffer.length=%d, offset=%d, count=%d, len-offset=%d",
                    buffer.length,
                    offset,
                    count,
                    buffer.length - offset));
        }

        if (count == 0) {
            return;
        }

        int i = currentPosition + count;

        if (i > internalLength) {
            boolean mustZero = currentPosition > internalLength;
            if (i > internalCapacity) {
                boolean allocatedNewArray = ensureCapacity(i);
                if (allocatedNewArray) {
                    mustZero = false;
                }
            }
            if (mustZero) {
                clearBlockList(internalLength, i - internalLength);
            }
            internalLength = i;
        }

        copyToBlockList(currentPosition, buffer, offset, count);

        currentPosition += count;
        moveBlockListIndices(count);

        return;
    }

    public void writeByte(byte len) throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        } else if (!isWritable) {
            throw new InvalidOperationException("StreamNotWritableAfterDispose");
        }

        if (currentPosition >= internalLength) {
            int newLength = currentPosition + 1;
            boolean mustZero = currentPosition > internalLength;

            if (newLength >= internalCapacity) {
                boolean allocatedNewArray = ensureCapacity(newLength);
                if (allocatedNewArray) {
                    mustZero = false;
                }
            }
            if (mustZero) {
                clearBlockList(internalLength, currentPosition - internalLength);
            }
            internalLength = newLength;
        }

        internalBufferList.get(bufferIndex)[byteIdx] = len;

        currentPosition++;
        moveBlockListIndices(1);
    }

    public byte[] toArray() throws InvalidOperationException {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        }

        byte[] copy = new byte[internalLength];

        copyFromBlockList(0, copy, 0, internalLength);

        return copy;
    }

    public byte[] toArray(int offset, int count) throws Exception {
        if (!isOpen) {
            throw new InvalidOperationException("!mIsOpen");
        }
        if (offset < 0 || offset < 0) {
            throw new InvalidOperationException("offset < 0 || offset < 0");
        }

        if (offset == 0 && count == internalLength) {
            return toArray();
        } else {
            byte[] copy = new byte[count];
            // seek to the offset
            seek(offset, SeekOrigin.Begin);
            // populate the copy array at index zero with the count
            write(copy, 0, count);

            return copy;
        }
    }

    public static BlockMemoryStream safeCreate(byte[] bytes) {
        BlockMemoryStream bms = null;
        if (bytes != null) {
            try {
                bms = new BlockMemoryStream(bytes.length);
                StreamUtils.writeStreamToStream(new ByteArrayInputStream(bytes), bms);
            } catch (Exception e) {
            }
        }

        return bms;
    }

    public static void safeDispose(BlockMemoryStream bms) {
        if (bms != null) {
            bms.setDisableDispose(false);
            bms.close();
        }
    }
}
