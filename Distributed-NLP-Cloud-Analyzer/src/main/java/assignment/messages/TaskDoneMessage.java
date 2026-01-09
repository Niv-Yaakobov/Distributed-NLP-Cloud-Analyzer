package assignment.messages;

/**
 * Sent from Worker to Manager when a single analysis task is completed.
 */
public class TaskDoneMessage {

    public String type = "TASK_DONE";

    // Job and task identifiers
    public String jobId;
    public String taskId;

    public String analysisType;
    public String sourceUrl;

    // true if analysis succeeded and result is in S3
    public boolean success;

    // S3 location of the result, if success == true
    public String resultBucket;
    public String resultKey;

    // Error description if success == false
    public String errorMessage;

    public TaskDoneMessage() {
    }

    public TaskDoneMessage(String jobId,
                           String taskId,
                           String analysisType,
                           String sourceUrl,
                           boolean success,
                           String resultBucket,
                           String resultKey,
                           String errorMessage) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.analysisType = analysisType;
        this.sourceUrl = sourceUrl;
        this.success = success;
        this.resultBucket = resultBucket;
        this.resultKey = resultKey;
        this.errorMessage = errorMessage;
    }
}
