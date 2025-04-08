package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class App implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  @Override
  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    // Log the incoming event
    context.getLogger().log("Received event: " + event);

    Map<String, Object> response = new HashMap<>();
    Map<String, Object> body = new HashMap<>();

    try {
      // Create S3 client
      AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

      // List buckets
      List<Bucket> buckets = s3Client.listBuckets();

      // Extract bucket names
      List<String> bucketNames = buckets.stream()
        .map(Bucket::getName)
        .collect(Collectors.toList());

      body.put("message", "Successfully retrieved buckets");
      body.put("buckets", bucketNames);
      response.put("statusCode", 200);
      response.put("body", body);
    } catch (Exception e) {
      context.getLogger().log("Error listing buckets: " + e.getMessage());
      body.put("message", "Error listing buckets: " + e.getMessage());
      response.put("statusCode", 500);
      response.put("body", body);
    }

    return response;
  }
}
