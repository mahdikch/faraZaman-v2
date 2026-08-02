package Ir.co.tfs.farazaman.util;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GpxZipper{

    public static File zipGpxFile(File gpxFile) throws Exception {
        File zipFile = new File(gpxFile.getParent(), gpxFile.getName().replace(".gpx", ".zip"));
        try (
                FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos);
                FileInputStream fis = new FileInputStream(gpxFile);
                BufferedInputStream bis = new BufferedInputStream(fis)
        ) {
            ZipEntry zipEntry = new ZipEntry(gpxFile.getName());
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int count;
            while ((count = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, count);
            }
            zos.closeEntry();
        } catch (Exception e) {
            Log.e("GpxZipper", "Error zipping file: " + e.getMessage());
            throw e;
        }
        return zipFile;
    }
}
