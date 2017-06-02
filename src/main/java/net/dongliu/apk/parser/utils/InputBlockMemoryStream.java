package net.dongliu.apk.parser.utils;

import java.io.IOException;
import java.io.InputStream;

import net.dongliu.apk.parser.exception.InvalidOperationException;
import net.dongliu.apk.parser.utils.BlockMemoryStream.SeekOrigin;

public class InputBlockMemoryStream extends InputStream {
    private BlockMemoryStream bms;

    public InputBlockMemoryStream(BlockMemoryStream bms) throws Exception {
        this(bms, false);
    }

    public InputBlockMemoryStream(BlockMemoryStream cms, boolean disableDispose) throws Exception {
        bms = cms;
        bms.setDisableDispose(disableDispose);
        bms.seek(0, SeekOrigin.Begin);
    }

    public BlockMemoryStream getChunkedMemoryStream() {
        return bms;
    }

    @Override
    public int available() {
        int bytesAvail = 0;

        try {
            bytesAvail = (int) bms.getLength() - (int) bms.getPosition();
        } catch (InvalidOperationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return bytesAvail;
    }

    @Override
    public void close() {
        bms.close();
    }

    @Override
    public void mark(int readlimit) {
        System.console().writer().write("in mark");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        try {
            return bms.readByte();
        } catch (InvalidOperationException e) {
            // TODO Auto-generated catch block
            throw new IOException();
        }
    }

    @Override
    public int read(byte[] b) {
        int bytesRead = 0;
        try {
            bytesRead = bms.read(b, 0, b.length);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return bytesRead;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        int bytesRead = 0;
        try {
            bytesRead = bms.read(b, off, len);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // if we specify non-zero amount to read, and the amount we read is zero,
        // check if we are at EOF.  According to docs, we need to return -1
        try {
            if (len != 0 && bytesRead == 0 && bms.getPosition() == bms.getLength()) {
                bytesRead = -1;
            }
        } catch (InvalidOperationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return bytesRead;
    }

    @Override
    public void reset() {
        System.console().writer().write("in reset");
    }

    @Override
    public long skip(long n) {
        long bytesSkipped = 0;
        try {
            bytesSkipped = bms.seek(n, SeekOrigin.Current);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return bytesSkipped;
    }
}