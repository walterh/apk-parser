package net.dongliu.apk.parser.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.dongliu.apk.parser.utils.BlockMemoryStream.SeekOrigin;

public class StreamUtils {

    public static void writeStreamToFile(BlockMemoryStream bms, File f) throws Exception {
        FileOutputStream fop = new FileOutputStream(f, true);
        StreamUtils.writeStreamToStream(new InputBlockMemoryStream(bms), fop);
    }

    public static BlockMemoryStream readFileToStream(File f) throws Exception {
        BlockMemoryStream bms = new BlockMemoryStream();
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(f);
            writeStreamToStream(fis, bms);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }

        return bms;
    }

    public static void readFileToStream(File f, BlockMemoryStream bms) throws Exception {
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(f);
            writeStreamToStream(fis, bms);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    public static Long writeStreamToStream(InputStream srcStream, OutputStream destStream) throws IOException {
        return writeStreamToStream(srcStream, destStream, BlockMemoryStream.DefaultBlockAllocationSize);
    }

    public static Long writeStreamToStream(InputStream srcStream, OutputStream destStream, int blockSize) throws IOException {
        byte[] byteBuff = new byte[blockSize];
        int count = 0;
        Long totalBytesWritten = 0L;

        while ((count = srcStream.read(byteBuff, 0, byteBuff.length)) > 0) {
            destStream.write(byteBuff, 0, count);
            totalBytesWritten += count;
        }

        destStream.flush();

        return totalBytesWritten;
    }

    public static void writeStreamToStream(InputStream srcStream, BlockMemoryStream destStream) throws Exception {
        writeStreamToStream(srcStream, destStream, BlockMemoryStream.DefaultBlockAllocationSize);
    }

    public static void writeStreamToStream(InputStream srcStream, BlockMemoryStream destStream, int blockSize) throws Exception {
        byte[] byteBuff = new byte[blockSize];
        int count = 0;

        while ((count = srcStream.read(byteBuff, 0, byteBuff.length)) > 0) {
            destStream.write(byteBuff, 0, count);
        }

        destStream.flush();
    }

    public static byte[] getBytes(InputStream is) throws Exception {
        BlockMemoryStream bms = null;
        boolean dispose = false;

        if (is instanceof InputBlockMemoryStream) {
            bms = ((InputBlockMemoryStream) is).getChunkedMemoryStream();
        } else {
            bms = new BlockMemoryStream();
            writeStreamToStream(is, bms);
        }

        bms.seek(0, SeekOrigin.Begin);
        byte[] bytes = bms.toArray();

        if (dispose) {
            bms.close();
        }

        return bytes;
    }

    public static void safeClose(InputStream in) {
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void safeClose(OutputStream out) {
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}