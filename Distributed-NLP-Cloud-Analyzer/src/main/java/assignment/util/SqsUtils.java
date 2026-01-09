package assignment.util;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;


public class SqsUtils {

    private static final SqsClient sqs = SqsClient.builder()
            .region(AwsConfig.REGION)
            .build();


    public static void sendMessage(String queueUrl, String body) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build();

        sqs.sendMessage(request);
    }

    public static List<Message> receiveMessages(String queueUrl, int maxMessages, int waitSeconds) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(waitSeconds)
                .visibilityTimeout(300)
                .build();

        ReceiveMessageResponse response = sqs.receiveMessage(request);
        return response.messages();
    }

 
    public static void deleteMessage(String queueUrl, Message message) {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();

        sqs.deleteMessage(request);
    }
}
