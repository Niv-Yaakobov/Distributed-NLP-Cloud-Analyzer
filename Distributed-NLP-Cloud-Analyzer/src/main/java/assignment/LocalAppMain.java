package assignment;

import java.util.List;

import assignment.messages.JobDoneMessage;
import assignment.messages.NewJobMessage;
import assignment.util.AwsConfig;
import assignment.util.JsonUtils;
import assignment.util.S3Utils;
import assignment.util.SqsUtils;
import software.amazon.awssdk.services.sqs.model.Message;
import assignment.util.Ec2Utils;


public class LocalAppMain {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar app.jar <inputFile> <outputFile> <n> [terminate]");
            return;
        }

        String inputFile = args[0];
        String outputFile = args[1];
        int nPerWorker = Integer.parseInt(args[2]);
        boolean terminateWhenDone = args.length > 3;

        // Generate jobId
        String jobId = "job-" + System.currentTimeMillis();

        System.out.println("Starting job: " + jobId);
        System.out.println("Input file: " + inputFile);
        System.out.println("Output file: " + outputFile);
        System.out.println("nPerWorker: " + nPerWorker);
        System.out.println("Terminate when done: " + terminateWhenDone);

        // Decide S3 locations
        String inputKey = "inputs/" + jobId + ".txt";
        String outputPrefix = "jobs/" + jobId + "/";

        // Upload input file to S3
        System.out.println("Uploading input file to S3: s3://" + AwsConfig.S3_BUCKET + "/" + inputKey);
        S3Utils.uploadFile(AwsConfig.S3_BUCKET, inputKey, inputFile);
        System.out.println("Upload completed.");

        // Ensure Manager instance is running
        System.out.println("Ensuring Manager instance is running...");
        Ec2Utils.ensureManagerRunning();


        // Build the NewJobMessage
        NewJobMessage msg = new NewJobMessage(
                jobId,
                AwsConfig.S3_BUCKET,
                inputKey,
                nPerWorker,
                outputPrefix,
                terminateWhenDone
        );
        
       
        // Serialize message to JSON
        String json = JsonUtils.toJson(msg);
        System.out.println("NewJobMessage JSON:");
        System.out.println(json);

        // Send the message to the app-to-manager SQS queue
        System.out.println("Sending NewJobMessage to SQS queue: " + AwsConfig.QUEUE_APP_TO_MANAGER);
        SqsUtils.sendMessage(AwsConfig.QUEUE_APP_TO_MANAGER, json);
        System.out.println("Message sent.");

        // Wait for JOB_DONE for this jobId
        System.out.println("Waiting for JOB_DONE message for job " + jobId + "...");
        JobDoneMessage done = waitForJobDone(jobId);
        System.out.println("Received JOB_DONE: success=" + done.success);

        if (!done.success) {
            System.out.println("Job failed: " + done.errorMessage);
            return;
        }

        // Download the summary HTML from S3 and save as outputFile
        System.out.println("Downloading summary HTML from s3://" + done.summaryBucket + "/" + done.summaryKey);
        S3Utils.downloadFile(done.summaryBucket, done.summaryKey, outputFile);
        System.out.println("Summary saved to local file: " + outputFile);
    }

    /**
     * Poll the manager-to-app queue until we get a JOB_DONE message for the given jobId. 
     */
    private static JobDoneMessage waitForJobDone(String jobId) {
        while (true) {
            List<Message> messages = SqsUtils.receiveMessages(
                    AwsConfig.QUEUE_MANAGER_TO_APP,
                    1,
                    10 
            );

            if (messages.isEmpty()) {
                continue;
            }

            for (Message m : messages) {
                String body = m.body();

                if (!body.contains("\"type\":\"JOB_DONE\"")) {
                    continue;
                }

                JobDoneMessage jobDone = JsonUtils.fromJson(body, JobDoneMessage.class);

                if (!jobId.equals(jobDone.jobId)) {
                    continue;
                }

                SqsUtils.deleteMessage(AwsConfig.QUEUE_MANAGER_TO_APP, m);
                return jobDone;
            }
        }
    }
}
