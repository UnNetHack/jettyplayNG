package jettyplay;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * An implementation of List, designed so that appending an array to the list
 * is a fast operation. It also records the times at which array chunks are
 * appended, for use with live data.
 * @author ais523
 */
public class ByteChunkList extends AbstractList<Byte> {
    ArrayList<Object> backingList; // holds chunks of byte[], Byte[], ArrayList<Byte>
    ArrayList<Integer> cumulativeSizeList;
    ArrayList<Date> chunkTimeList;
    
    ByteChunkList() {
        backingList = new ArrayList<>();
        cumulativeSizeList = new ArrayList<>();
        chunkTimeList = new ArrayList<>();
    }

    private int findChunk(int index) {
        // We find the chunk that the index index is in via binary search.
        int searchDistance = cumulativeSizeList.size();
        if (searchDistance == 0) throw new IndexOutOfBoundsException();
        int currentIndex = 0;
        while (searchDistance > 1) {
            int t = cumulativeSizeList.get(currentIndex);
            if (t == index) return currentIndex + 1;
            searchDistance = (searchDistance + 1) / 2;
            if (t < index) currentIndex += searchDistance;
            else currentIndex -= searchDistance;
            if (currentIndex < 0) currentIndex = 0;
            if (currentIndex >= cumulativeSizeList.size())
                currentIndex = cumulativeSizeList.size() - 1;
        }
        if (cumulativeSizeList.get(currentIndex) <= index)
            currentIndex++;
        return currentIndex;
    }

    /**
     * Stores a conveniently-sized amount of data into storeIn, starting
     * at index, and not exceeding the storage space provided. There is
     * no guarantee that any more than one element of storeIn is filled.
     * @param index The index of this list to store into storeIn[off].
     * @param storeIn The array to store the data in.
     * @param off The index of the first element to store in.
     * @param len The maximum number of elements to store.
     * @return The number of elements stored into storeIn.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public synchronized int getRestOfChunk(
            int index, byte[] storeIn, int off, int len) {
        int chunk = findChunk(index);
        int indexInChunk = index;
        if (chunk > 0) indexInChunk -= cumulativeSizeList.get(chunk-1);
        Object array = backingList.get(chunk);
        int l = len;
        if (array instanceof byte[]) {
            if (l + indexInChunk > ((byte[])array).length)
                l = ((byte[])array).length - indexInChunk;
            System.arraycopy((byte[])array, indexInChunk, storeIn, off, l);
            return l;
        }
        if (array instanceof Byte[]) {
            if (l + indexInChunk > ((Byte[])array).length)
                l = ((Byte[])array).length - indexInChunk;
            for (int i = 0; i < l; i++)
                storeIn[i+off] = ((Byte[])array)[i+indexInChunk];
            return l;
        }
        if (array instanceof ArrayList) {
            ArrayList arrayList = (ArrayList) array;
            if (l + indexInChunk > arrayList.size())
                l = arrayList.size() - indexInChunk;
            for (int i = 0; i < l; i++)
                storeIn[i+off] = (Byte)arrayList.get(i+indexInChunk);
            return l;
        }
        throw new IndexOutOfBoundsException("Could not find which part of the list to index");
    }

    /**
     * Returns the byte from this list at the given index.
     * @param index The index to return the byte from.
     * @return The byte at that index.
     * @throws IndexOutOfBoundsException if the list is not long enough to
     * contain the given index, or the given index is negative. In some cases,
     * there may be a spurious wrong answer, rather than an exception.
     */
    @Override
    @SuppressWarnings({"unchecked","rawtypes"})
    public synchronized Byte get(int index) {
        int chunk = findChunk(index);
        int indexInChunk = index;
        if (chunk > 0) indexInChunk -= cumulativeSizeList.get(chunk-1);
        Object array = backingList.get(chunk);
        if (array instanceof byte[]) return ((byte[])array)[indexInChunk];
        if (array instanceof Byte[]) return ((Byte[])array)[indexInChunk];
        if (array instanceof ArrayList) {
            ArrayList arrayList = (ArrayList) array;
            return (Byte)arrayList.get(indexInChunk);
        }
        throw new IndexOutOfBoundsException("Could not find which part of the list to index");
    }
    /**
     * Returns the date of bytes in this list at the given index.
     * @param index The index to return the date from.
     * @return The date at that index.
     * @throws IndexOutOfBoundsException if the list is not long enough to
     * contain the given index, or the given index is negative. In some cases,
     * there may be a spurious wrong answer, rather than an exception.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public synchronized Date getDate(int index) {
        int chunk = findChunk(index);
        return chunkTimeList.get(chunk);
    }
    /**
     * Returns the number of bytes in the list.
     * @return The number of bytes in the list.
     */
    @Override
    public synchronized int size() {
        if (cumulativeSizeList.isEmpty()) return 0;
        return cumulativeSizeList.get(cumulativeSizeList.size()-1);
    }
    /**
     * Inserts a new byte into the array at the given location, moving all the
     * other bytes forwards to fit. The timestamp of the new byte might be set
     * to the timestamp of surrounding bytes (because a ByteChunkList should
     * always have weakly increasing timestamps, unless the system clock jumps
     * backwards) rather than to the current time.
     * @param index The index to insert the byte at.
     * @param element The byte to insert;
     */
    @Override
    @SuppressWarnings({"unchecked","rawtypes"})
    public synchronized void add(int index, Byte element) {
        if (index == size()) {appendArray(new Byte[]{element}); return;}
        int chunk = findChunk(index);
        int indexInChunk = index;
        if (chunk > 0) indexInChunk -= cumulativeSizeList.get(chunk);
        Object array = backingList.get(chunk);
        if (array instanceof byte[]) {
            ArrayList<Byte> al = new ArrayList<>();
            for (byte b: (byte[])array) {
                al.add(b);
            }
            backingList.set(chunk, al);
            array = al;
        }
        if (array instanceof Byte[]) {
            ArrayList<Byte> al = new ArrayList<>();
            al.addAll(Arrays.asList((Byte[])array));
            backingList.set(chunk, al);
            array = al;
        }
        if (array instanceof ArrayList) {
            ArrayList arrayList = (ArrayList) array;
            arrayList.add(indexInChunk, element);
            for (int i = chunk; i < cumulativeSizeList.size(); i++)
                cumulativeSizeList.set(i, cumulativeSizeList.get(i)+1);
            return;
        }
        throw new IndexOutOfBoundsException("Could not find which part of the list to index");
    }

    /**
     * Deletes a byte at the given index, moving all the other bytes backwards
     * to fit.
     * @param index The index to delete the byte from.
     * @return The byte deleted.
     */
    @Override
    @SuppressWarnings({"unchecked","rawtypes"})
    public synchronized Byte remove(int index) {
        int chunk = findChunk(index);
        int indexInChunk = index;
        if (chunk > 0) indexInChunk -= cumulativeSizeList.get(chunk-1);
        Object array = backingList.get(chunk);
        if (array instanceof byte[]) {
            ArrayList<Byte> al = new ArrayList<>();
            for (byte b: (byte[])array) {
                al.add(b);
            }
            backingList.set(chunk, al);
            array = al;
        }
        if (array instanceof Byte[]) {
            ArrayList<Byte> al = new ArrayList<>();
            al.addAll(Arrays.asList((Byte[])array));
            backingList.set(chunk, al);
            array = al;
        }
        if (array instanceof ArrayList) {
            ArrayList arrayList = (ArrayList) array;
            Byte b = (Byte) arrayList.remove(indexInChunk);
            for (int i = chunk; i < cumulativeSizeList.size(); i++)
                cumulativeSizeList.set(i, cumulativeSizeList.get(i)-1);
            return b;
        }
        throw new IndexOutOfBoundsException("Could not find which part of the list to index");
    }
    /**
     * Changes the value of the element at a given location. Currently
     * unimplemented.
     * @param index Unused.
     * @param element Unused.
     * @return Never returns.
     * @throws UnsupportedOperationException always.
     */
    @Override
    public synchronized Byte set(int index, Byte element) {
        return super.set(index, element);
    }

    /**
     * Appends an array of bytes to the end of this list. The array is
     * wrapped by this list, rather than copied, and so should not be
     * changed after being added to the list.
     * @param array Either a byte[] or a Byte[] to append.
     */
    public synchronized void appendArray(Object array) {
        int s = -1;
        if (array instanceof byte[]) s = ((byte[])array).length;
        if (array instanceof Byte[]) s = ((Byte[])array).length;
        if (s == 0) return;
        if (s > -1) {
            cumulativeSizeList.add(size()+s);
            backingList.add(array);
            chunkTimeList.add(new Date());
        } else
            throw new ClassCastException("Argument is not a byte array");
    }
    /**
     * Appends the first count bytes of an array of bytes to the end of
     * this list. The array is wrapped by this list, rather than copied,
     * and so should not be changed after being added to the list.
     * @param array Either a byte[] or a Byte[] to append.
     * @param count The number of bytes to add.
     */
    public synchronized void appendArray(Object array, int count) {
        int s = -1;
        if (array instanceof byte[]) s = ((byte[])array).length;
        if (array instanceof Byte[]) s = ((Byte[])array).length;
        if (s == 0) return;
        if (s > -1) {
            cumulativeSizeList.add(size() + count);
            backingList.add(array);
            chunkTimeList.add(new Date());
        } else
            throw new ClassCastException("Argument is not a byte array");
    }
}
