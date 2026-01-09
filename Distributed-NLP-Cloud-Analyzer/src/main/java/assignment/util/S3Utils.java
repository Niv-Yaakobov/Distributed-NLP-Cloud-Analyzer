package assignment.util;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;


public class S3Utils {

    private static final S3Client s3 = S3Client.builder()
            .region(AwsConfig.REGION)
            .build();


    public static void uploadFile(String bucket, String key, String filePath) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3.putObject(req, RequestBody.fromFile(Path.of(filePath)));
    }

    public static void downloadFile(String bucket, String key, String localPath) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (ResponseInputStream<?> s3Object = s3.getObject(req);
             FileOutputStream out = new FileOutputStream(localPath)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = s3Object.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download s3://" + bucket + "/" + key + " to " + localPath, e);
        }
    }
}
