# Distributed-NLP-Cloud-Analyzer
 
**Created by:** Niv Yaakobov 322578998

---

## 1. Project Overview
This project implements a distributed text-analysis system on AWS. The application processes a list of text file URLs and performs linguistic analysis using the **Stanford Parser**. 
The system supports three types of analysis:
* **POS**: Part-of-speech tagging.
* **CONSTITUENCY**: Constituency parsing.
* **DEPENDENCY**: Dependency parsing.

The workload is distributed across **EC2 Worker** instances, orchestrated by a **Manager** instance, with communication handled via **SQS** and storage via **S3**.



---

## 2. System Architecture

### Components:
* **Local Application**: 
    * Acts as the entry point. 
    * Uploads the input file to S3 and initiates the Manager.
    * Waits for the "jobDone" message and downloads the final HTML summary.
* **Manager (EC2)**:
    * Orchestrates the workflow and listens to the Local→Manager SQS queue.
    * Calculates required workers: $requiredWorkers = \lceil totalMessages / n \rceil$.
    * Ensures a safety limit of $\leq 19$ instances to avoid AWS student account blocks.
    * Aggregates worker results into a final HTML summary.
* **Worker (EC2)**:
    * Polls the Manager→Worker queue.
    * Downloads text files, performs parsing, and uploads results to S3.
    * Handles exceptions gracefully without crashing.

---

## 3. How to Run

### Prerequisites
* Temporary AWS Academy credentials placed in: `/home/ubuntu/.aws/credentials`.
* S3 bucket and SQS queue names configured in `AwsConfig`.
* AMI ID embedded in the Manager code.

### Execution
Run the application from the local machine:
```bash
java -jar manager-worker-stanford-parser.jar <inputFile> <outputFile> <n> [terminate]
