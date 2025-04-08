# [Public Documentation] Application Signals Set Up for Lambda with ECR Container Image (Java)

This guide focuses on how to properly integrate the OpenTelemetry Layer with AppSignals support into your containerized Java Lambda function.

## Why This Approach is Necessary

Lambda functions deployed as container images do not support Lambda Layers in the traditional way. When using container images, you cannot simply attach the layer as you would with other Lambda deployment methods. Instead, you must manually incorporate the layer's contents into your container image during the build process.

This document outlines the necessary steps to download the `layer.zip` artifact and properly integrate it into your containerized Java Lambda function to enable AppSignals monitoring.

## Prerequisites
- AWS CLI configured with your credentials
- Docker installed
- These instructions assumed you are on `x86_64` platform.

## 1. Set Up Project Structure
Create a directory for your Lambda function:

```console
mkdir java-lambda-function && \
cd java-lambda-function
```

Create a Maven project structure:

```console
mkdir -p src/main/java/com/example/java/lambda
mkdir -p src/main/resources
```

## 2. Obtaining and Using the OpenTelemetry Layer with AppSignals Support

### Downloading and Integrating the Layer in Dockerfile

The most crucial step is downloading and integrating the OpenTelemetry Layer with AppSignals support directly in your Dockerfile:

```Dockerfile
# Dockerfile

FROM public.ecr.aws/lambda/java:21

# Install utilities
RUN dnf install -y unzip wget maven

# Download the OpenTelemetry Layer with AppSignals Support
RUN wget https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/layer.zip -O /tmp/layer.zip

# Extract and include Lambda layer contents
RUN mkdir -p /opt && \
    unzip /tmp/layer.zip -d /opt/ && \
    chmod -R 755 /opt/ && \
    rm /tmp/layer.zip

# Copy and build function code
COPY pom.xml ${LAMBDA_TASK_ROOT}
COPY src ${LAMBDA_TASK_ROOT}/src
RUN mvn clean package -DskipTests

# Copy the JAR file to the Lambda runtime directory (from inside the container)
RUN mkdir -p ${LAMBDA_TASK_ROOT}/lib/
RUN cp ${LAMBDA_TASK_ROOT}/target/function.jar ${LAMBDA_TASK_ROOT}/lib/

# Set the handler
CMD ["com.example.java.lambda.App::handleRequest"]
```

> Note: The layer.zip file contains the OpenTelemetry instrumentation necessary for AWS AppSignals to monitor your Lambda function.

> Important: The layer extraction steps ensure that:
> 1. The layer.zip contents are properly extracted to the /opt/ directory
> 2. The otel-instrument script receives proper execution permissions
> 3. The temporary layer.zip file is removed to keep the image size smaller

## 3. Lambda Function Code
Create a Java file for your Lambda handler at `src/main/java/com/example/lambda/App.java`:

```java
package com.example.java.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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
    LambdaLogger logger = context.getLogger();
    // Log the incoming event
    logger.log("Received event: " + event);
    logger.log("Handler initializing: " + this.getClass().getName());

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
      logger.log("Error listing buckets: " + e.getMessage());
      e.printStackTrace();
      body.put("message", "Error listing buckets: " + e.getMessage());
      response.put("statusCode", 500);
      response.put("body", body);
    }

    return response;
  }
}
```

Create a `pom.xml` file in the root directory:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" 
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
<modelVersion>4.0.0</modelVersion>

<groupId>com.example.java.lambda</groupId>
<artifactId>lambda-java-function</artifactId>
<version>1.0-SNAPSHOT</version>
<packaging>jar</packaging>

<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <aws.java.sdk.version>1.12.529</aws.java.sdk.version>
    <aws.lambda.java.version>1.2.3</aws.lambda.java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
    <!-- AWS Lambda Core -->
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-core</artifactId>
        <version>${aws.lambda.java.version}</version>
    </dependency>

    <!-- AWS Lambda Events -->
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-events</artifactId>
        <version>3.11.3</version>
    </dependency>

    <!-- AWS SDK for S3 -->
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-java-sdk-s3</artifactId>
        <version>${aws.java.sdk.version}</version>
    </dependency>

    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.2</version>
    </dependency>
</dependencies>

<build>
    <finalName>function</finalName>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.4.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <createDependencyReducedPom>false</createDependencyReducedPom>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.example.java.lambda.App</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
</project>
```

## 4. Build and Deploy the Container Image

### Set up environment variables

```console
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text) 
AWS_REGION=$(aws configure get region)

# For fish shell users:
# set AWS_ACCOUNT_ID (aws sts get-caller-identity --query Account --output text) 
# set AWS_REGION (aws configure get region)
```

### Authenticate with ECR

First with public ECR (for base image):

```console
aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws
```

Then with your private ECR:

```console
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
```

### Create ECR repository (if needed)

```console
aws ecr create-repository \
    --repository-name lambda-appsignals-demo \
    --region $AWS_REGION
```

### Build, tag and push your image

```console
# Build the Docker image
docker build -t lambda-appsignals-demo .

# Tag the image
docker tag lambda-appsignals-demo:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/lambda-appsignals-demo:latest

# Push the image
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/lambda-appsignals-demo:latest
```

## 5. Create and Configure the Lambda Function

1. Go to the AWS Lambda console and create a new function
2. Select **Container image** as the deployment option
3. Select your ECR image

### Critical AppSignals Configuration

The following steps are essential for the `layer.zip` integration to work:

- Add the environment variable:
  - Key: `AWS_LAMBDA_EXEC_WRAPPER`
  - Value: `/opt/otel-instrument`
  - This environment variable tells Lambda to use the `otel-instrument` wrapper script that was extracted from the `layer.zip` file to your container's `/opt` directory.
- Attach required IAM policies:
  - **CloudWatchLambdaApplicationSignalsExecutionRolePolicy** - required for AppSignals
  - Additional policies for your function's operations (e.g., S3 access policy for our example)
- You may also need to configure your Lambda's Timeout and Memory settings under **General configuration**. In this example we use the following settings:
  - Timeout: 0 min 15 sec
  - Memory: 512 MB

## 6. Testing and Verification
1. Test your Lambda function with a simple event
2. If the layer integration is successful, your Lambda will appear in the AppSignals service map
3. You should see traces and metrics for your Lambda function in the CloudWatch console.
