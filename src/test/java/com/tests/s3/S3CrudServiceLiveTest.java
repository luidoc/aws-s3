package com.tests.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.http.SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES;

import java.net.URI;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.utils.AttributeMap;

// This live test needs a running Docker instance so that a S3Mock Container can be started

@Testcontainers
public class S3CrudServiceLiveTest {

  private static final String TEST_BUCKET_NAME = "test-bucket";

  @Container
  private final S3MockContainer s3Mock = new S3MockContainer("latest");
  private S3Client s3Client;

  @BeforeEach
  void setUp() {

    AwsCredentials creds = AwsBasicCredentials.create("lo_que_sea", "9-DrEldxh0q2y-wUjnTC46zVdESs");
    AwsCredentialsProvider provider = StaticCredentialsProvider.create(creds);
    var endpoint = s3Mock.getHttpsEndpoint();
    var serviceConfig = S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .build();
    var httpClient = UrlConnectionHttpClient.builder()
        .buildWithDefaults(AttributeMap.builder()
            .put(TRUST_ALL_CERTIFICATES, Boolean.TRUE)
            .build());
    s3Client = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of("auto"))
        .serviceConfiguration(serviceConfig)
        .credentialsProvider(provider)
        .httpClient(httpClient)
        .build();
  }

  @Test
  void whenVerifyingCreationOfS3Bucket_thenCorrect() {
    var s3CrudService = new S3CrudService(s3Client);
    s3CrudService.createBucket(TEST_BUCKET_NAME);

    var createdBucketName = s3Client.listBuckets()
        .buckets()
        .get(0)
        .name();
    assertThat(TEST_BUCKET_NAME).isEqualTo(createdBucketName);
  }

  @Test
  void whenCreatingAnObjectOnS3Bucket_thenSameObjectIsRetrived() {
    var s3CrudService = new S3CrudService(s3Client);
    s3CrudService.createBucket(TEST_BUCKET_NAME);

    var fileToSave = FileGenerator.generateFiles(1, 100)
        .get(0);
    s3CrudService.createObject(TEST_BUCKET_NAME, fileToSave);

    var savedFileContent = s3CrudService.getObject(TEST_BUCKET_NAME, fileToSave.getName());

    assertThat(Arrays.equals(fileToSave.getContent()
        .array(), savedFileContent.orElse(new byte[] {}))).isTrue();

    s3CrudService.deleteObject(TEST_BUCKET_NAME, fileToSave.getName());

    var deletedFileContent = s3CrudService.getObject(TEST_BUCKET_NAME, fileToSave.getName());
    assertThat(deletedFileContent).isEmpty();
  }
}
