package assignment.messages;

/**
 * Sent from Manager to Worker for each analysis task (one line in the input file).
 */
public class AnalysisTaskMessage {

    public String type = "ANALYSIS_TASK";

    public String jobId;

    // Task identifier within the job (e.g. line index as string)
    public String taskId;

    // Analysis type: "POS", "CONSTITUENCY", or "DEPENDENCY"
    public String analysisType;

    // URL of the source text file
    public String sourceUrl;

    // Where Worker should upload the result (S3)
    public String resultBucket;
    public String resultPrefix; // Result would be inside "jobs/<jobId>/tasks/"

    public AnalysisTaskMessage() {
    }

    public AnalysisTaskMessage(String jobId,
                               String taskId,
                               String analysisType,
                               String sourceUrl,
                               String resultBucket,
                               String resultPrefix) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.analysisType = analysisType;
        this.sourceUrl = sourceUrl;
        this.resultBucket = resultBucket;
        this.resultPrefix = resultPrefix;
    }
}
