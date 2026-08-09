package com.knowflow.storage;

import com.knowflow.config.AppProperties;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioStorageService {
    private final MinioClient client;
    private final AppProperties.Minio properties;

    public MinioStorageService(AppProperties.Minio properties) {
        this.properties = properties;
        this.client = MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).build();
    }

    @PostConstruct
    void ensureBucket() throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
                if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                return;
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(1000L);
            }
        }
        throw new IllegalStateException("MinIO unavailable after startup retries", last);
    }

    public void put(String objectKey, InputStream stream, long size, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
                .stream(stream, size, -1).contentType(contentType).build());
    }

    public InputStream get(String objectKey) throws Exception {
        return client.getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    }

    public void delete(String objectKey) throws Exception {
        client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    }


}
