package assignment;

import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import assignment.manager.JobState;
import assignment.manager.JobTaskResult;
import assignment.messages.AnalysisTaskMessage;
import assignment.messages.JobDoneMessage;
import assignment.messages.NewJobMessage;
import assignment.messages.TaskDoneMessage;
import assignment.util.AwsConfig;
import assignment.util.JsonUtils;
import assignment.util.SqsUtils;
import assignment.util.S3Utils;
import assignment.util.Ec2Utils;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Manager main loop:
 * - Receives NEW_JOB from app-to-manager queue
 * - Creates ANALYSIS_TASK messages for workers
 * - Collects TASK_DONE from workers
 * - Builds summary and sends JOB_DONE back to app
 */
public class ManagerMain {

    // Once a job with terminateWhenDone=true arrives, this becomes true.
    private volatile boolean shutdownRequested = false;


    // All active jobs, keyed by jobId
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    // Thread pool for parallel job handling
    private final ExecutorService jobExecutor = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        ManagerMain manager = new ManagerMain();
        manager.run();
    }

    private void run() {
        System.out.println("Manager started, polling queues...");
        
        while (true) {
            handleNewJobs();
            handleTaskDone();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Poll the app-to-manager queue for NEW_JOB messages.
     */
    private void handleNewJobs() {
        List<Message> messages = SqsUtils.receiveMessages(
                AwsConfig.QUEUE_APP_TO_MANAGER,
                5,
                5);

        if (messages.isEmpty()) {
            return;
        }

        for (Message m : messages) {
            String body = m.body();

            if (!body.contains("\"type\":\"NEW_JOB\"")) {
                continue;
            }

            NewJobMessage newJob = JsonUtils.fromJson(body, NewJobMessage.class);
            System.out.println("Received NEW_JOB: " + newJob.jobId);

            // If shutdown was already requested by a previous job, ignore any new jobs.
            if (shutdownRequested) {
                System.out.println("Shutdown already requested, ignoring NEW_JOB " + newJob.jobId);
                // Best behavior is to delete the msg so it doesn't keep reappearing
                SqsUtils.deleteMessage(AwsConfig.QUEUE_APP_TO_MANAGER, m);
                continue;
            }

            // If THIS job is the one that should trigger shutdown after everything is done:
            if (newJob.terminateWhenDone) {
                shutdownRequested = true;
                System.out.println("Shutdown requested by job " + newJob.jobId + ". No more jobs will be accepted after this one.");
            }

            JobState job = new JobState(
                    newJob.jobId,
                    newJob.inputBucket,
                    newJob.inputKey,
                    newJob.outputPrefix,
                    newJob.nPerWorker,
                    newJob.terminateWhenDone
            );
            jobs.put(newJob.jobId, job);

            // Handle job in parallel
            jobExecutor.submit(() -> {
                try {
                    createTasksForJob(job);
                } catch (Exception e) {
                    System.out.println("Failed to create tasks for job " + job.jobId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });

            SqsUtils.deleteMessage(AwsConfig.QUEUE_APP_TO_MANAGER, m);

        }
    }

    private void handleTaskDone() {
        List<Message> messages = SqsUtils.receiveMessages(
                AwsConfig.QUEUE_WORKER_TO_MANAGER,
                5,
                5
        );

        if (messages.isEmpty()) {
            return;
        }

        for (Message m : messages) {
            String body = m.body();

            if (!body.contains("\"type\":\"TASK_DONE\"")) {
                continue;
            }

            TaskDoneMessage done = JsonUtils.fromJson(body, TaskDoneMessage.class);

            JobState job = jobs.get(done.jobId);
            if (job == null) {
                System.out.println("Received TASK_DONE for unknown jobId: " + done.jobId);
                SqsUtils.deleteMessage(AwsConfig.QUEUE_WORKER_TO_MANAGER, m);
                continue;
            }

            // Update job state
            JobTaskResult result = new JobTaskResult(
                    done.taskId,
                    done.analysisType,
                    done.sourceUrl,
                    done.success,
                    done.resultBucket,
                    done.resultKey,
                    done.errorMessage
            );

            job.results.put(done.taskId, result);
            job.completedTasks++;

            System.out.println("Job " + job.jobId + " task " + done.taskId +
                    " done. success=" + done.success +
                    " (" + job.completedTasks + "/" + job.totalTasks + ")");

            SqsUtils.deleteMessage(AwsConfig.QUEUE_WORKER_TO_MANAGER, m);

            if (job.isComplete()) {
                onJobComplete(job);
            }
        }
    }


    private void createTasksForJob(JobState job) {
        System.out.println("Creating tasks for job: " + job.jobId);

        // Download the input file from S3 into a temp file
        String localTempPath = "/tmp/" + job.jobId + ".txt";
        S3Utils.downloadFile(
                job.inputBucket,
                job.inputKey,
                localTempPath);

        // Read file and parse lines
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(localTempPath))) {
            String line;
            int index = 0;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String[] parts = line.split("\\t");
                if (parts.length != 2) {
                    System.out.println("Skipping invalid line: " + line);
                    continue;
                }

                String analysisType = parts[0].trim();
                String sourceUrl = parts[1].trim();

                String taskId = String.valueOf(index);

                // Build AnalysisTaskMessage
                AnalysisTaskMessage taskMsg = new AnalysisTaskMessage(
                        job.jobId,
                        taskId,
                        analysisType,
                        sourceUrl,
                        job.inputBucket, 
                        job.outputPrefix + "tasks/" 
                );

                // Convert to JSON
                String json = assignment.util.JsonUtils.toJson(taskMsg);

                // Send to manager-to-worker
                assignment.util.SqsUtils.sendMessage(
                        assignment.util.AwsConfig.QUEUE_MANAGER_TO_WORKER,
                        json);

                index++;
            }

        job.totalTasks = index;
        System.out.println("Job " + job.jobId + " has " + index + " tasks.");

        if (job.totalTasks == 0) {
            System.out.println("No tasks created for job " + job.jobId + ", no workers needed.");
            return;
        }

        // Calculate the number of requires worker (before considering existing workers k)
        int m = (job.totalTasks + job.nPerWorker - 1) / job.nPerWorker;
        System.out.println("Job " + job.jobId + " requires m=" + m + " workers (before considering existing workers k).");

        // If there are k active workers, it will create (m - k) new ones, capped at 19.
        Ec2Utils.ensureWorkers(m);


        } catch (Exception e) {
            throw new RuntimeException("Failed to create tasks for job " + job.jobId, e);
        }
    }


    private void onJobComplete(JobState job) {
        System.out.println("Job completed (all tasks done): " + job.jobId);

        String summaryBucket = AwsConfig.S3_BUCKET;
        String summaryKey = job.outputPrefix + "summary.html";

        // Build + upload HTML summary
        buildAndUploadSummaryHtml(job, summaryBucket, summaryKey);

        boolean allOk = job.results.values().stream().allMatch(r -> r.success);
        boolean success = allOk;
        String errorMessage = allOk ? null : "Some tasks failed. See summary for details.";

        JobDoneMessage done = new JobDoneMessage(
                job.jobId,
                success,
                summaryBucket,
                summaryKey,
                errorMessage
        );

        String json = JsonUtils.toJson(done);
        System.out.println("Sending JOB_DONE for job " + job.jobId + " to queue: " +
                AwsConfig.QUEUE_MANAGER_TO_APP);
        SqsUtils.sendMessage(AwsConfig.QUEUE_MANAGER_TO_APP, json);

        jobs.remove(job.jobId);

        // If shutdown was requested (because some job had terminateWhenDone=true)
        // and there are NO more jobs left, we can safely terminate workers and exit.
        if (shutdownRequested && jobs.isEmpty()) {
            System.out.println("All jobs completed and shutdown requested. Terminating all workers and exiting Manager.");
            Ec2Utils.terminateAllWorkers();
            jobExecutor.shutdownNow();
            System.exit(0);
        }
    }



    private void buildAndUploadSummaryHtml(JobState job, String summaryBucket, String summaryKey) {
        System.out.println("Building summary HTML for job " + job.jobId);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html><head><meta charset=\"UTF-8\"><title>Job Summary ")
        .append(job.jobId)
        .append("</title></head><body>\n");

        sb.append("<h1>Job Summary: ").append(job.jobId).append("</h1>\n");
        sb.append("<p>Total tasks: ").append(job.totalTasks).append("</p>\n");
        sb.append("<p>Completed tasks: ").append(job.completedTasks).append("</p>\n");

        sb.append("<table border=\"1\" cellpadding=\"4\" cellspacing=\"0\">\n");
        sb.append("<tr>")
        .append("<th>Task ID</th>")
        .append("<th>Analysis Type</th>")
        .append("<th>Source URL</th>")
        .append("<th>Success</th>")
        .append("<th>Result</th>")
        .append("<th>Error</th>")
        .append("</tr>\n");

        java.util.List<JobTaskResult> rows = new java.util.ArrayList<>(job.results.values());
        java.util.Collections.sort(rows, (a, b) -> {
            try {
                int ta = Integer.parseInt(a.taskId);
                int tb = Integer.parseInt(b.taskId);
                return Integer.compare(ta, tb);
            } catch (NumberFormatException e) {
                return a.taskId.compareTo(b.taskId);
            }
        });

        String regionId = AwsConfig.REGION.id();

        for (JobTaskResult r : rows) {
            sb.append("<tr>");

            sb.append("<td>").append(r.taskId).append("</td>");
            sb.append("<td>").append(r.analysisType).append("</td>");
            sb.append("<td>").append(escapeHtml(r.sourceUrl)).append("</td>");

            sb.append("<td>").append(r.success ? "YES" : "NO").append("</td>");

            if (r.success && r.resultBucket != null && r.resultKey != null) {
                String url = "https://" + r.resultBucket + ".s3." + regionId + ".amazonaws.com/" + r.resultKey;
                sb.append("<td><a href=\"").append(url).append("\">View result</a></td>");
            } else {
                sb.append("<td>-</td>");
            }

            sb.append("<td>").append(r.errorMessage == null ? "" : escapeHtml(r.errorMessage)).append("</td>");

            sb.append("</tr>\n");
        }

        sb.append("</table>\n");
        sb.append("</body></html>\n");

        String tmpPath = "/tmp/summary-" + job.jobId + ".html";
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmpPath)) {
            out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write local summary HTML for job " + job.jobId, e);
        }

        S3Utils.uploadFile(summaryBucket, summaryKey, tmpPath);
        System.out.println("Summary HTML uploaded to s3://" + summaryBucket + "/" + summaryKey);
    }

    private String escapeHtml(String s) {
    if (s == null) return "";
    return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
}

}
