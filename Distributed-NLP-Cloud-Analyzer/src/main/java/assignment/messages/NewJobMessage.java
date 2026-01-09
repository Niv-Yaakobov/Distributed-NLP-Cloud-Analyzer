package assignment.messages;

/**
 * Sent from Local Application to Manager when a new job is submitted.
 */
public class NewJobMessage {

    public String type = "NEW_JOB";

    // Unique id for this job
    public String jobId;

    // S3 location of the input file
    public String inputBucket;
    public String inputKey;

    // Maximum number of tasks per worker (CLI argument n)
    public int nPerWorker;

    // Prefix in S3 where job outputs should be stored, e.g. "jobs/<jobId>/"
    public String outputPrefix;

    // If true, Manager will terminate itself and all the workers after all pending tasks and jobs will be completed
    public boolean terminateWhenDone;

    public NewJobMessage() {
    }

    public NewJobMessage(String jobId,
                         String inputBucket,
                         String inputKey,
                         int nPerWorker,
                         String outputPrefix,
                         boolean terminateWhenDone) {
        this.jobId = jobId;
        this.inputBucket = inputBucket;
        this.inputKey = inputKey;
        this.nPerWorker = nPerWorker;
        this.outputPrefix = outputPrefix;
        this.terminateWhenDone = terminateWhenDone;
    }
}
