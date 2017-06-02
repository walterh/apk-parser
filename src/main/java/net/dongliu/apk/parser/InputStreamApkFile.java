package net.dongliu.apk.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.dongliu.apk.parser.bean.ApkSignStatus;
import net.dongliu.apk.parser.utils.BlockMemoryStream;
import net.dongliu.apk.parser.utils.InputBlockMemoryStream;
import net.dongliu.apk.parser.utils.StreamUtils;
import net.dongliu.apk.parser.utils.Utils;

public class InputStreamApkFile extends AbstractApkFile implements Closeable {
    final BlockMemoryStream cms;

    public InputStreamApkFile(InputStream is) throws Exception {
        cms = new BlockMemoryStream();
        cms.setDisableDispose(true);

        StreamUtils.writeStreamToStream(is, cms);
    }

    @Override
    protected byte[] getCertificateData() {
        try (final ZipInputStream zis = new ZipInputStream(new InputBlockMemoryStream(cms, true))) {

            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toUpperCase().endsWith(".RSA") || entry.getName().toUpperCase().endsWith(".DSA")) {
                    return Utils.toByteArray(zis);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public byte[] getFileData(String path) throws IOException {
        try (final ZipInputStream zis = new ZipInputStream(new InputBlockMemoryStream(cms, true))) {

            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                if (path.equals(entry.getName())) {
                    return Utils.toByteArray(zis);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.cms.close();
    }

    @Override
    public ApkSignStatus verifyApk() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}
