package assignment.manager;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory state for a single job that the Manager is handling.
 */
public class JobState {

    public final String jobId;

    public final String inputBucket;
    public final String inputKey;

    public final String outputPrefix;
    public final int nPerWorker;
    public final boolean terminateWhenDone;

    // Total number of tasks created for this job
    public volatile int totalTasks;

    // Number of TASK_DONE messages processed for this job
    public int completedTasks;

    // taskId -> result
    public Map<String, JobTaskResult> results = new HashMap<>();

    public JobState(String jobId,
                    String inputBucket,
                    String inputKey,
                    String outputPrefix,
                    int nPerWorker,
                    boolean terminateWhenDone) {
        this.jobId = jobId;
        this.inputBucket = inputBucket;
        this.inputKey = inputKey;
        this.outputPrefix = outputPrefix;
        this.nPerWorker = nPerWorker;
        this.terminateWhenDone = terminateWhenDone;
    }

    public boolean isComplete() {
        return completedTasks == totalTasks && totalTasks > 0;
    }
}
