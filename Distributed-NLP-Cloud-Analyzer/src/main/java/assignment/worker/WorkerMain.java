package assignment.worker;

import assignment.messages.AnalysisTaskMessage;
import assignment.messages.TaskDoneMessage;
import assignment.util.AwsConfig;
import assignment.util.JsonUtils;
import assignment.util.S3Utils;
import assignment.util.SqsUtils;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;


import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.TypedDependency;


public class WorkerMain {

    // Load Stanford parser once per worker JVM
    private static final LexicalizedParser PARSER =
            LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");

    private static final TreebankLanguagePack TLP = new PennTreebankLanguagePack();
    private static final GrammaticalStructureFactory GSF = TLP.grammaticalStructureFactory();


    public static void main(String[] args) {
        System.out.println("Worker started. Polling tasks...");
        WorkerMain worker = new WorkerMain();
        worker.run();
    }

    private void run() {
        while (true) {
            handleTasks();
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
        }
    }

    private void handleTasks() {
        List<Message> messages = SqsUtils.receiveMessages(
                AwsConfig.QUEUE_MANAGER_TO_WORKER,
                1,
                10
        );

        if (messages.isEmpty()) return;

        for (Message m : messages) {
            String body = m.body();

            if (!body.contains("\"type\":\"ANALYSIS_TASK\"")) {
                continue;
            }

            AnalysisTaskMessage task = JsonUtils.fromJson(body, AnalysisTaskMessage.class);
            System.out.println("Received task: job=" + task.jobId + " taskId=" + task.taskId
                    + " type=" + task.analysisType);

            TaskDoneMessage response = processTask(task);

            // Send TASK_DONE
            String json = JsonUtils.toJson(response);
            SqsUtils.sendMessage(AwsConfig.QUEUE_WORKER_TO_MANAGER, json);

            // Delete the original message
            SqsUtils.deleteMessage(AwsConfig.QUEUE_MANAGER_TO_WORKER, m);
        }
    }

    private TaskDoneMessage processTask(AnalysisTaskMessage task) {
        try {
            String tempInputPath = "/tmp/worker-" + task.jobId + "-" + task.taskId + ".txt";
            String tempOutputPath = "/tmp/output-" + task.jobId + "-" + task.taskId + ".txt";

            // 1. download source file
            downloadUrlToFile(task.sourceUrl, tempInputPath);

            // 2. run analysis
            runAnalysis(task.analysisType, tempInputPath, tempOutputPath);

            // 3. upload result to S3
            String resultKey = task.resultPrefix + task.taskId + "-" +
                    task.analysisType.toLowerCase() + ".txt";

            S3Utils.uploadFile(task.resultBucket, resultKey, tempOutputPath);

            // 4. success
            return new TaskDoneMessage(
                    task.jobId,
                    task.taskId,
                    task.analysisType,
                    task.sourceUrl,
                    true,
                    task.resultBucket,
                    resultKey,
                    null
            );
            

        } catch (Exception e) {
            System.out.println("Task failed: " + e.getMessage());
            return new TaskDoneMessage(
                    task.jobId,
                    task.taskId,
                    task.analysisType,
                    task.sourceUrl,
                    false,
                    null,
                    null,
                    e.getMessage()
            );
        }
        
    }

    private void downloadUrlToFile(String urlStr, String localPath) throws Exception {
        try (InputStream in = new URL(urlStr).openStream();
             FileOutputStream out = new FileOutputStream(localPath)) {

            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
    }

    private void runAnalysis(String analysisType, String inputPath, String outputPath) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(inputPath, StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {

            String line;
            
            while ((line = br.readLine()) != null) {


                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Treat each line as a sentence
                List<HasWord> sentence = Sentence.toWordList(line.split("\\s+"));
                Tree parse = PARSER.apply(sentence);

                if ("POS".equalsIgnoreCase(analysisType)) {
                    writePos(parse, out);
                } else if ("CONSTITUENCY".equalsIgnoreCase(analysisType)) {
                    writeConstituency(parse, out);
                } else if ("DEPENDENCY".equalsIgnoreCase(analysisType)) {
                    writeDependency(parse, out);
                } else {
                    throw new IllegalArgumentException("Unknown analysis type: " + analysisType);
                }

                out.println(); 
                out.println();

            }
        }
    }

    private void writePos(Tree parse, PrintWriter out) {
        List<TaggedWord> tagged = parse.taggedYield();
        for (TaggedWord tw : tagged) {
            out.print(tw.word());
            out.print("/");
            out.print(tw.tag());
            out.print(" ");
        }
        out.println();
    }

    private void writeConstituency(Tree parse, PrintWriter out) {
        // Penn Treebank style LISP tree
        out.println(parse.toString());
    }

    private void writeDependency(Tree parse, PrintWriter out) {
        var gs = GSF.newGrammaticalStructure(parse);
        Collection<TypedDependency> deps = gs.typedDependencies();
        for (TypedDependency dep : deps) {
            out.println(dep.toString());
        }
    }

}
