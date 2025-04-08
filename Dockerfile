FROM public.ecr.aws/lambda/java:17

# Copy function code JAR to the Lambda task root
COPY target/function.jar ${LAMBDA_TASK_ROOT}/lib/

# Install unzip utility
RUN yum install -y unzip

# Extract and include Lambda layer contents
COPY layer.zip /tmp/
RUN mkdir -p /opt && \
    unzip /tmp/layer.zip -d /opt/ && \
    chmod -R 755 /opt/ && \
    rm /tmp/layer.zip

# Set the handler as the CMD
CMD [ "com.example.App::handleRequest" ]
