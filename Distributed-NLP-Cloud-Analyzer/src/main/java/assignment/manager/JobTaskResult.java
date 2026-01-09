package assignment.manager;

/**
 * In-memory representation of a single task result inside a job,
 * based on TaskDoneMessage data.
 */
public class JobTaskResult {

    public String taskId;
    public String analysisType;
    public String sourceUrl;

    public boolean success;
    public String resultBucket;
    public String resultKey;
    public String errorMessage;

    public JobTaskResult() {
    }

    public JobTaskResult(String taskId,
                         String analysisType,
                         String sourceUrl,
                         boolean success,
                         String resultBucket,
                         String resultKey,
                         String errorMessage) {
        this.taskId = taskId;
        this.analysisType = analysisType;
        this.sourceUrl = sourceUrl;
        this.success = success;
        this.resultBucket = resultBucket;
        this.resultKey = resultKey;
        this.errorMessage = errorMessage;
    }
}

