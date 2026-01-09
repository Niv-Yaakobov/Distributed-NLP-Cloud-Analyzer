package assignment.messages;

/**
 * Sent from Manager to Local Application when a job is completed.
 */
public class JobDoneMessage {

    public String type = "JOB_DONE";

    public String jobId;

    // Whether summary was successfully created and uploaded
    public boolean success;

    // S3 location of the summary HTML (if success == true)
    public String summaryBucket;
    public String summaryKey;

    // Error description if success == false (otherwise null)
    public String errorMessage;

    public JobDoneMessage() {
    }

    public JobDoneMessage(String jobId,
                          boolean success,
                          String summaryBucket,
                          String summaryKey,
                          String errorMessage) {
        this.jobId = jobId;
        this.success = success;
        this.summaryBucket = summaryBucket;
        this.summaryKey = summaryKey;
        this.errorMessage = errorMessage;
    }
}
